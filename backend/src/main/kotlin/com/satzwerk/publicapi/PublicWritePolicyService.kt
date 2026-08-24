package com.satzwerk.publicapi

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.DecimalNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.satzwerk.auth.InsufficientScopeException
import com.satzwerk.common.BadRequestException
import com.satzwerk.common.ConflictException
import com.satzwerk.common.ForbiddenException
import com.satzwerk.common.NotFoundException
import com.satzwerk.common.UnauthorizedException
import io.r2dbc.spi.Row
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.annotation.Id
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.http.HttpStatus
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import java.time.Instant
import java.util.UUID

private const val IDEMPOTENCY_HEADER = "Idempotency-Key"
private const val PENDING_RESPONSE_STATUS = -1
private const val MAX_PENDING_RECORD_POLLS = 40
private const val PENDING_RECORD_POLL_DELAY_MILLIS = 25L
private const val LEGACY_NO_FINGERPRINT_SENTINEL = "__legacy_no_fingerprint__"
private const val IDEMPOTENCY_PAYLOAD_MISMATCH_MESSAGE =
    "Idempotency-Key already used with a different payload"
private const val CLAIM_PUBLIC_WRITE_IDEMPOTENCY_RECORD_SQL =
    """
    INSERT INTO public_write_idempotency_records (
        principal_type,
        credential_id,
        app_id,
        grant_id,
        user_id,
        request_method,
        request_path,
        idempotency_key,
        request_fingerprint,
        response_status,
        response_body
    )
    VALUES (
        :principalType,
        :credentialId,
        :appId,
        :grantId,
        :userId,
        :requestMethod,
        :requestPath,
        :idempotencyKey,
        :requestFingerprint,
        -1,
        '__pending__'
    )
    ON CONFLICT (
        principal_type,
        credential_id,
        request_method,
        request_path,
        idempotency_key
    ) DO NOTHING
    RETURNING id, principal_type, credential_id, app_id, grant_id, user_id, request_method,
        request_path, idempotency_key, request_fingerprint, response_status, response_body, created_at
    """

private fun DatabaseClient.GenericExecuteSpec.bindNullableUuid(
    name: String,
    value: UUID?,
): DatabaseClient.GenericExecuteSpec = value?.let { bind(name, it) } ?: bindNull(name, UUID::class.java)

sealed interface PublicWriteRequestFingerprintCodec {
    fun encode(objectMapper: ObjectMapper): String

    companion object {
        fun body(requestBody: Any): PublicWriteRequestFingerprintCodec = Body(requestBody)

        fun stateless(command: String): PublicWriteRequestFingerprintCodec = Stateless(command)
    }
}

private data class Body(
    private val requestBody: Any,
) : PublicWriteRequestFingerprintCodec {
    override fun encode(objectMapper: ObjectMapper): String =
        objectMapper.valueToTree<JsonNode>(requestBody)
            .let(::canonicalizeJson)
            .let(objectMapper::writeValueAsString)
}

private data class Stateless(
    private val command: String,
) : PublicWriteRequestFingerprintCodec {
    override fun encode(objectMapper: ObjectMapper): String =
        canonicalizeJson(
            JsonNodeFactory.instance.objectNode().put("command", command.trim()),
        ).let(objectMapper::writeValueAsString)
}

private data class PublicWriteRequestMetadata(
    val principalType: PublicWritePrincipalType,
    val credentialId: UUID,
    val appId: UUID?,
    val grantId: UUID?,
    val userId: UUID,
    val grantedScopes: String,
    val requestMethod: String,
    val requestPath: String,
    val idempotencyKey: String,
    val requestFingerprint: String,
)

@Table("public_write_idempotency_records")
data class PublicWriteIdempotencyRecord(
    @Id
    val id: UUID? = null,
    @Column("principal_type")
    val principalType: PublicWritePrincipalType,
    @Column("credential_id")
    val credentialId: UUID,
    @Column("app_id")
    val appId: UUID? = null,
    @Column("grant_id")
    val grantId: UUID? = null,
    @Column("user_id")
    val userId: UUID,
    @Column("request_method")
    val requestMethod: String,
    @Column("request_path")
    val requestPath: String,
    @Column("idempotency_key")
    val idempotencyKey: String,
    @Column("request_fingerprint")
    val requestFingerprint: String,
    @Column("response_status")
    val responseStatus: Int,
    @Column("response_body")
    val responseBody: String,
    @Column("created_at")
    val createdAt: Instant = Instant.now(),
)

data class PublicWriteIdempotencyClaim(
    val principalType: PublicWritePrincipalType,
    val credentialId: UUID,
    val appId: UUID?,
    val grantId: UUID?,
    val userId: UUID,
    val requestMethod: String,
    val requestPath: String,
    val idempotencyKey: String,
    val requestFingerprint: String,
)

@Table("public_write_audit")
data class PublicWriteAuditEntry(
    @Id
    val id: UUID? = null,
    @Column("principal_type")
    val principalType: PublicWritePrincipalType,
    @Column("credential_id")
    val credentialId: UUID,
    @Column("app_id")
    val appId: UUID? = null,
    @Column("grant_id")
    val grantId: UUID? = null,
    @Column("user_id")
    val userId: UUID,
    @Column("granted_scopes")
    val grantedScopes: String,
    @Column("request_method")
    val requestMethod: String,
    @Column("request_path")
    val requestPath: String,
    @Column("idempotency_key")
    val idempotencyKey: String,
    @Column("response_status")
    val responseStatus: Int,
    @Column("created_at")
    val createdAt: Instant = Instant.now(),
)

interface PublicWriteIdempotencyRecordRepository :
    CoroutineCrudRepository<PublicWriteIdempotencyRecord, UUID>,
    PublicWriteIdempotencyRecordRepositoryCustom {
    suspend fun findByPrincipalTypeAndCredentialIdAndRequestMethodAndRequestPathAndIdempotencyKey(
        principalType: PublicWritePrincipalType,
        credentialId: UUID,
        requestMethod: String,
        requestPath: String,
        idempotencyKey: String,
    ): PublicWriteIdempotencyRecord?

    fun findAllByPrincipalTypeAndCredentialId(
        principalType: PublicWritePrincipalType,
        credentialId: UUID,
    ): Flow<PublicWriteIdempotencyRecord>

    @Query(
        """
        SELECT *
        FROM public_write_idempotency_records
        WHERE principal_type = 'PARTNER_APP' AND credential_id = :grantId
        """,
    )
    fun findAllByGrantId(grantId: UUID): Flow<PublicWriteIdempotencyRecord>

    @Query(
        """
        SELECT *
        FROM public_write_idempotency_records
        WHERE principal_type = 'PARTNER_APP'
          AND credential_id = :grantId
          AND request_method = :requestMethod
          AND request_path = :requestPath
          AND idempotency_key = :idempotencyKey
        LIMIT 1
        """,
    )
    suspend fun findByGrantIdAndRequestMethodAndRequestPathAndIdempotencyKey(
        grantId: UUID,
        requestMethod: String,
        requestPath: String,
        idempotencyKey: String,
    ): PublicWriteIdempotencyRecord?
}

interface PublicWriteIdempotencyRecordRepositoryCustom {
    suspend fun claim(claim: PublicWriteIdempotencyClaim): PublicWriteIdempotencyRecord?
}

@Repository
class PublicWriteIdempotencyRecordRepositoryImpl(
    private val databaseClient: DatabaseClient,
) : PublicWriteIdempotencyRecordRepositoryCustom {
    override suspend fun claim(claim: PublicWriteIdempotencyClaim): PublicWriteIdempotencyRecord? =
        databaseClient
            .sql(CLAIM_PUBLIC_WRITE_IDEMPOTENCY_RECORD_SQL)
            .bind("principalType", claim.principalType.name)
            .bind("credentialId", claim.credentialId)
            .bindNullableUuid("appId", claim.appId)
            .bindNullableUuid("grantId", claim.grantId)
            .bind("userId", claim.userId)
            .bind("requestMethod", claim.requestMethod)
            .bind("requestPath", claim.requestPath)
            .bind("idempotencyKey", claim.idempotencyKey)
            .bind("requestFingerprint", claim.requestFingerprint)
            .map { row, _ -> toPublicWriteIdempotencyRecord(row) }
            .one()
            .awaitSingleOrNull()

    private fun toPublicWriteIdempotencyRecord(row: Row) =
        PublicWriteIdempotencyRecord(
            id = row.get("id", UUID::class.java),
            principalType = PublicWritePrincipalType.valueOf(row.get("principal_type", String::class.java)!!),
            credentialId = row.get("credential_id", UUID::class.java)!!,
            appId = row.get("app_id", UUID::class.java),
            grantId = row.get("grant_id", UUID::class.java),
            userId = row.get("user_id", UUID::class.java)!!,
            requestMethod = row.get("request_method", String::class.java)!!,
            requestPath = row.get("request_path", String::class.java)!!,
            idempotencyKey = row.get("idempotency_key", String::class.java)!!,
            requestFingerprint = row.get("request_fingerprint", String::class.java)!!,
            responseStatus = row.get("response_status", Integer::class.java)!!.toInt(),
            responseBody = row.get("response_body", String::class.java)!!,
            createdAt = row.get("created_at", Instant::class.java)!!,
        )
}

interface PublicWriteAuditRepository : CoroutineCrudRepository<PublicWriteAuditEntry, UUID> {
    fun findAllByPrincipalTypeAndCredentialId(
        principalType: PublicWritePrincipalType,
        credentialId: UUID,
    ): Flow<PublicWriteAuditEntry>

    @Query(
        """
        SELECT *
        FROM public_write_audit
        WHERE principal_type = 'PARTNER_APP' AND credential_id = :grantId
        """,
    )
    fun findAllByGrantId(grantId: UUID): Flow<PublicWriteAuditEntry>
}

@Service
class PublicWriteAuditService(
    private val publicWriteAuditRepository: PublicWriteAuditRepository,
) {
    suspend fun record(entry: PublicWriteAuditEntry) {
        publicWriteAuditRepository.save(entry)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    suspend fun recordFailure(entry: PublicWriteAuditEntry) {
        publicWriteAuditRepository.save(entry)
    }
}

@Service
class PublicWritePolicyService(
    private val idempotencyRecordRepository: PublicWriteIdempotencyRecordRepository,
    private val publicWriteAuditService: PublicWriteAuditService,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    suspend fun <T : Any> execute(
        publicWritePrincipal: PublicWritePrincipal,
        request: ServerRequest,
        successStatus: HttpStatus,
        requestFingerprintCodec: PublicWriteRequestFingerprintCodec,
        block: suspend (UUID) -> T,
    ): ServerResponse {
        val metadata = buildRequestMetadata(publicWritePrincipal, request, requestFingerprintCodec)
        val claimedRecord =
            idempotencyRecordRepository.claim(
                PublicWriteIdempotencyClaim(
                    principalType = metadata.principalType,
                    credentialId = metadata.credentialId,
                    appId = metadata.appId,
                    grantId = metadata.grantId,
                    userId = metadata.userId,
                    requestMethod = metadata.requestMethod,
                    requestPath = metadata.requestPath,
                    idempotencyKey = metadata.idempotencyKey,
                    requestFingerprint = metadata.requestFingerprint,
                ),
            )
        if (claimedRecord == null) {
            val existing = awaitCompletedRecord(metadata)
            publicWriteAuditService.record(buildAuditEntry(metadata, existing.responseStatus))
            return ServerResponse.status(existing.responseStatus)
                .bodyValueAndAwait(objectMapper.readTree(existing.responseBody))
        }

        return runCatching {
            val responseBody = block(metadata.userId)
            val responseStatus = successStatus.value()
            val serializedResponse = objectMapper.writeValueAsString(responseBody)

            idempotencyRecordRepository.save(
                claimedRecord.copy(
                    responseStatus = responseStatus,
                    responseBody = serializedResponse,
                ),
            )
            publicWriteAuditService.record(buildAuditEntry(metadata, responseStatus))

            ServerResponse.status(successStatus).bodyValueAndAwait(responseBody)
        }.getOrElse { failure ->
            val auditFailure =
                runCatching {
                    publicWriteAuditService.recordFailure(
                        buildAuditEntry(metadata, failure.toAuditResponseStatus()),
                    )
                }.exceptionOrNull()
            idempotencyRecordRepository.deleteById(requireNotNull(claimedRecord.id))
            auditFailure?.let(failure::addSuppressed)
            throw failure
        }
    }

    private fun buildRequestMetadata(
        publicWritePrincipal: PublicWritePrincipal,
        request: ServerRequest,
        requestFingerprintCodec: PublicWriteRequestFingerprintCodec,
    ) = PublicWriteRequestMetadata(
        principalType = publicWritePrincipal.principalType,
        credentialId = publicWritePrincipal.credentialId,
        appId = publicWritePrincipal.appId,
        grantId = publicWritePrincipal.grantId,
        userId = publicWritePrincipal.userId,
        grantedScopes = publicWritePrincipal.scopes.asGrantedScopes(),
        requestMethod = request.method().name(),
        requestPath = request.path(),
        idempotencyKey = requireIdempotencyKey(request),
        requestFingerprint = requestFingerprintCodec.encode(objectMapper),
    )

    private fun buildAuditEntry(
        metadata: PublicWriteRequestMetadata,
        responseStatus: Int,
    ) = PublicWriteAuditEntry(
        principalType = metadata.principalType,
        credentialId = metadata.credentialId,
        appId = metadata.appId,
        grantId = metadata.grantId,
        userId = metadata.userId,
        grantedScopes = metadata.grantedScopes,
        requestMethod = metadata.requestMethod,
        requestPath = metadata.requestPath,
        idempotencyKey = metadata.idempotencyKey,
        responseStatus = responseStatus,
    )

    private suspend fun awaitCompletedRecord(metadata: PublicWriteRequestMetadata): PublicWriteIdempotencyRecord {
        repeat(MAX_PENDING_RECORD_POLLS) {
            val record =
                idempotencyRecordRepository
                    .findByPrincipalTypeAndCredentialIdAndRequestMethodAndRequestPathAndIdempotencyKey(
                        principalType = metadata.principalType,
                        credentialId = metadata.credentialId,
                        requestMethod = metadata.requestMethod,
                        requestPath = metadata.requestPath,
                        idempotencyKey = metadata.idempotencyKey,
                    )
            if (record != null) {
                record.requireMatchingFingerprint(metadata.requestFingerprint)
                if (record.responseStatus != PENDING_RESPONSE_STATUS) {
                    return record
                }
            }
            delay(PENDING_RECORD_POLL_DELAY_MILLIS)
        }
        throw ConflictException("Idempotent request is still in progress")
    }
}

private fun PublicWriteIdempotencyRecord.hasUnverifiableLegacyFingerprint(): Boolean =
    responseStatus != PENDING_RESPONSE_STATUS &&
        (requestFingerprint == LEGACY_NO_FINGERPRINT_SENTINEL || requestFingerprint.isBlank())

private fun PublicWriteIdempotencyRecord.requireMatchingFingerprint(requestFingerprint: String) {
    val isMismatch =
        when {
            hasUnverifiableLegacyFingerprint() -> true
            responseStatus == PENDING_RESPONSE_STATUS ->
                this.requestFingerprint.isNotBlank() && this.requestFingerprint != requestFingerprint
            else -> this.requestFingerprint != requestFingerprint
        }
    if (isMismatch) {
        throw ConflictException(IDEMPOTENCY_PAYLOAD_MISMATCH_MESSAGE)
    }
}

private fun requireIdempotencyKey(request: ServerRequest): String =
    request.headers().firstHeader(IDEMPOTENCY_HEADER)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: throw BadRequestException("$IDEMPOTENCY_HEADER header required")

private fun Set<String>.asGrantedScopes(): String = toList().sorted().joinToString(" ")

private fun Throwable.toAuditResponseStatus(): Int =
    when (this) {
        is ForbiddenException, is InsufficientScopeException -> HttpStatus.FORBIDDEN.value()
        is NotFoundException -> HttpStatus.NOT_FOUND.value()
        is BadRequestException -> HttpStatus.BAD_REQUEST.value()
        is UnauthorizedException -> HttpStatus.UNAUTHORIZED.value()
        is ConflictException -> HttpStatus.CONFLICT.value()
        else -> HttpStatus.INTERNAL_SERVER_ERROR.value()
    }

private fun canonicalizeJson(node: JsonNode): JsonNode =
    when {
        node.isObject -> {
            val canonicalObject = ObjectNode(JsonNodeFactory.instance)
            node.fields().asSequence().sortedBy { it.key }.forEach { (key, value) ->
                canonicalObject.set<JsonNode>(key, canonicalizeJson(value))
            }
            canonicalObject
        }

        node.isArray -> {
            val canonicalArray = ArrayNode(JsonNodeFactory.instance)
            node.forEach { canonicalArray.add(canonicalizeJson(it)) }
            canonicalArray
        }

        node.isBigDecimal || node.isFloatingPointNumber ->
            DecimalNode.valueOf(node.decimalValue().stripTrailingZeros())

        else -> node.deepCopy<JsonNode>()
    }
