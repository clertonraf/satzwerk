package com.satzwerk.publicapi

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.DecimalNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.satzwerk.common.BadRequestException
import com.satzwerk.common.ConflictException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import org.springframework.data.annotation.Id
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
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
private const val IDEMPOTENCY_PAYLOAD_MISMATCH_MESSAGE =
    "Idempotency-Key already used with a different payload"

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
    CoroutineCrudRepository<PublicWriteIdempotencyRecord, UUID> {
    @Query(
        """
        INSERT INTO public_write_idempotency_records (
            principal_type,
            credential_id,
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
            :requestMethod,
            :requestPath,
            :idempotencyKey,
            '',
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
        RETURNING id, principal_type, credential_id, request_method, request_path, idempotency_key,
            request_fingerprint, response_status, response_body, created_at
        """,
    )
    suspend fun claim(
        principalType: PublicWritePrincipalType,
        credentialId: UUID,
        requestMethod: String,
        requestPath: String,
        idempotencyKey: String,
    ): PublicWriteIdempotencyRecord?

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
class PublicWritePolicyService(
    private val idempotencyRecordRepository: PublicWriteIdempotencyRecordRepository,
    private val publicWriteAuditRepository: PublicWriteAuditRepository,
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
                principalType = metadata.principalType,
                credentialId = metadata.credentialId,
                requestMethod = metadata.requestMethod,
                requestPath = metadata.requestPath,
                idempotencyKey = metadata.idempotencyKey,
            )
        if (claimedRecord == null) {
            val existing = awaitCompletedRecord(metadata)
            recordAudit(metadata, existing.responseStatus)
            return ServerResponse.status(existing.responseStatus)
                .bodyValueAndAwait(objectMapper.readTree(existing.responseBody))
        }

        return runCatching {
            val initializedRecord =
                idempotencyRecordRepository.save(
                    claimedRecord.copy(requestFingerprint = metadata.requestFingerprint),
                )
            val responseBody = block(metadata.userId)
            val responseStatus = successStatus.value()
            val serializedResponse = objectMapper.writeValueAsString(responseBody)

            idempotencyRecordRepository.save(
                initializedRecord.copy(
                    requestFingerprint = metadata.requestFingerprint,
                    responseStatus = responseStatus,
                    responseBody = serializedResponse,
                ),
            )
            recordAudit(metadata, responseStatus)

            ServerResponse.status(successStatus).bodyValueAndAwait(responseBody)
        }.getOrElse { failure ->
            idempotencyRecordRepository.deleteById(requireNotNull(claimedRecord.id))
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

    private suspend fun recordAudit(
        metadata: PublicWriteRequestMetadata,
        responseStatus: Int,
    ) {
        publicWriteAuditRepository.save(
            PublicWriteAuditEntry(
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
            ),
        )
    }

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
            if (record != null && record.requestFingerprint.isNotBlank()) {
                if (record.requestFingerprint != metadata.requestFingerprint) {
                    throw ConflictException(IDEMPOTENCY_PAYLOAD_MISMATCH_MESSAGE)
                }
            }
            if (record != null && record.responseStatus != PENDING_RESPONSE_STATUS) {
                return record
            }
            delay(PENDING_RECORD_POLL_DELAY_MILLIS)
        }
        throw ConflictException("Idempotent request is still in progress")
    }
}

private fun requireIdempotencyKey(request: ServerRequest): String =
    request.headers().firstHeader(IDEMPOTENCY_HEADER)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: throw BadRequestException("$IDEMPOTENCY_HEADER header required")

private fun Set<String>.asGrantedScopes(): String = toList().sorted().joinToString(" ")

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
