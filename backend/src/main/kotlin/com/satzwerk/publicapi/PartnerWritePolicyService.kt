package com.satzwerk.publicapi

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.DecimalNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.satzwerk.common.BadRequestException
import com.satzwerk.common.ConflictException
import com.satzwerk.common.PartnerAppRequestPrincipal
import kotlinx.coroutines.delay
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
private const val IDEMPOTENCY_PAYLOAD_MISMATCH_MESSAGE = "Idempotency-Key already used with a different payload"

sealed interface PartnerWriteRequestFingerprintCodec {
    fun encode(objectMapper: ObjectMapper): String

    companion object {
        fun body(requestBody: Any): PartnerWriteRequestFingerprintCodec = Body(requestBody)

        fun stateless(command: String): PartnerWriteRequestFingerprintCodec = Stateless(command)
    }
}

private data class Body(
    private val requestBody: Any,
) : PartnerWriteRequestFingerprintCodec {
    override fun encode(objectMapper: ObjectMapper): String =
        objectMapper.valueToTree<JsonNode>(requestBody)
            .let(::canonicalizeJson)
            .let(objectMapper::writeValueAsString)
}

private data class Stateless(
    private val command: String,
) : PartnerWriteRequestFingerprintCodec {
    override fun encode(objectMapper: ObjectMapper): String =
        canonicalizeJson(
            JsonNodeFactory.instance.objectNode().put("command", command.trim()),
        ).let(objectMapper::writeValueAsString)
}

private data class PartnerWriteRequestMetadata(
    val grantId: UUID,
    val appId: UUID,
    val userId: UUID,
    val grantedScopes: String,
    val requestMethod: String,
    val requestPath: String,
    val idempotencyKey: String,
    val requestFingerprint: String,
)

@Table("idempotency_records")
data class IdempotencyRecord(
    @Id
    val id: UUID? = null,
    @Column("grant_id")
    val grantId: UUID,
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

@Table("partner_write_audit")
data class PartnerWriteAuditEntry(
    @Id
    val id: UUID? = null,
    @Column("grant_id")
    val grantId: UUID,
    @Column("app_id")
    val appId: UUID,
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

interface IdempotencyRecordRepository : CoroutineCrudRepository<IdempotencyRecord, UUID> {
    @Query(
        """
        INSERT INTO idempotency_records (
            grant_id,
            request_method,
            request_path,
            idempotency_key,
            request_fingerprint,
            response_status,
            response_body
        )
        VALUES (
            :grantId,
            :requestMethod,
            :requestPath,
            :idempotencyKey,
            :requestFingerprint,
            -1,
            '__pending__'
        )
        ON CONFLICT (grant_id, request_method, request_path, idempotency_key) DO NOTHING
        RETURNING id, grant_id, request_method, request_path, idempotency_key, request_fingerprint, response_status, response_body, created_at
        """,
    )
    suspend fun claim(
        grantId: UUID,
        requestMethod: String,
        requestPath: String,
        idempotencyKey: String,
        requestFingerprint: String,
    ): IdempotencyRecord?

    suspend fun findByGrantIdAndRequestMethodAndRequestPathAndIdempotencyKey(
        grantId: UUID,
        requestMethod: String,
        requestPath: String,
        idempotencyKey: String,
    ): IdempotencyRecord?

    fun findAllByGrantId(grantId: UUID): kotlinx.coroutines.flow.Flow<IdempotencyRecord>
}

interface PartnerWriteAuditRepository : CoroutineCrudRepository<PartnerWriteAuditEntry, UUID> {
    fun findAllByGrantId(grantId: UUID): kotlinx.coroutines.flow.Flow<PartnerWriteAuditEntry>
}

@Service
class PartnerWritePolicyService(
    private val idempotencyRecordRepository: IdempotencyRecordRepository,
    private val partnerWriteAuditRepository: PartnerWriteAuditRepository,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    suspend fun <T : Any> execute(
        partnerPrincipal: PartnerAppRequestPrincipal,
        request: ServerRequest,
        successStatus: HttpStatus,
        requestFingerprintCodec: PartnerWriteRequestFingerprintCodec,
        block: suspend (UUID) -> T,
    ): ServerResponse {
        val metadata =
            PartnerWriteRequestMetadata(
                grantId = partnerPrincipal.grantId,
                appId = partnerPrincipal.appId,
                userId = partnerPrincipal.userId,
                grantedScopes = partnerPrincipal.partnerPrincipal.grantedScopes,
                requestMethod = request.method().name(),
                requestPath = request.path(),
                idempotencyKey = requireIdempotencyKey(request),
                requestFingerprint = requestFingerprintCodec.encode(objectMapper),
            )
        val claimedRecord =
            idempotencyRecordRepository.claim(
                grantId = metadata.grantId,
                requestMethod = metadata.requestMethod,
                requestPath = metadata.requestPath,
                idempotencyKey = metadata.idempotencyKey,
                requestFingerprint = metadata.requestFingerprint,
            )
        if (claimedRecord == null) {
            val existing = awaitCompletedRecord(metadata)
            recordAudit(metadata, existing.responseStatus)
            return ServerResponse.status(existing.responseStatus)
                .bodyValueAndAwait(objectMapper.readTree(existing.responseBody))
        }

        return runCatching {
            val responseBody = block(metadata.userId)
            val responseStatus = successStatus.value()
            val serializedResponse = objectMapper.writeValueAsString(responseBody)

            idempotencyRecordRepository.save(
                claimedRecord.copy(
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

    private suspend fun recordAudit(
        metadata: PartnerWriteRequestMetadata,
        responseStatus: Int,
    ) {
        partnerWriteAuditRepository.save(
            PartnerWriteAuditEntry(
                grantId = metadata.grantId,
                appId = metadata.appId,
                userId = metadata.userId,
                grantedScopes = metadata.grantedScopes,
                requestMethod = metadata.requestMethod,
                requestPath = metadata.requestPath,
                idempotencyKey = metadata.idempotencyKey,
                responseStatus = responseStatus,
            ),
        )
    }

    private fun requireIdempotencyKey(request: ServerRequest): String =
        request.headers().firstHeader(IDEMPOTENCY_HEADER)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: throw BadRequestException("$IDEMPOTENCY_HEADER header required")

    private suspend fun awaitCompletedRecord(metadata: PartnerWriteRequestMetadata): IdempotencyRecord {
        repeat(MAX_PENDING_RECORD_POLLS) {
            val record =
                idempotencyRecordRepository.findByGrantIdAndRequestMethodAndRequestPathAndIdempotencyKey(
                    grantId = metadata.grantId,
                    requestMethod = metadata.requestMethod,
                    requestPath = metadata.requestPath,
                    idempotencyKey = metadata.idempotencyKey,
                )
            if (record != null && record.requestFingerprint != metadata.requestFingerprint) {
                throw ConflictException(IDEMPOTENCY_PAYLOAD_MISMATCH_MESSAGE)
            }
            if (record != null && record.responseStatus != PENDING_RESPONSE_STATUS) {
                return record
            }
            delay(PENDING_RECORD_POLL_DELAY_MILLIS)
        }
        throw ConflictException("Idempotent request is still in progress")
    }
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
