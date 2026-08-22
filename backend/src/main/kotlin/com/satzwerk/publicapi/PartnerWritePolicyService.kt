package com.satzwerk.publicapi

import com.fasterxml.jackson.databind.ObjectMapper
import com.satzwerk.common.BadRequestException
import com.satzwerk.common.parseUuid
import com.satzwerk.common.requirePartnerPrincipal
import org.springframework.data.annotation.Id
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

private data class PartnerWriteRequestMetadata(
    val grantId: UUID,
    val appId: UUID,
    val userId: UUID,
    val requestMethod: String,
    val requestPath: String,
    val idempotencyKey: String,
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
        request: ServerRequest,
        successStatus: HttpStatus,
        block: suspend (UUID) -> T,
    ): ServerResponse {
        val partnerPrincipal = requirePartnerPrincipal(request)
        val metadata =
            PartnerWriteRequestMetadata(
                grantId = parseUuid(partnerPrincipal.grantId),
                appId = parseUuid(partnerPrincipal.appId),
                userId = parseUuid(partnerPrincipal.userId),
                requestMethod = request.method().name(),
                requestPath = request.path(),
                idempotencyKey = requireIdempotencyKey(request),
            )

        val existing =
            idempotencyRecordRepository.findByGrantIdAndRequestMethodAndRequestPathAndIdempotencyKey(
                grantId = metadata.grantId,
                requestMethod = metadata.requestMethod,
                requestPath = metadata.requestPath,
                idempotencyKey = metadata.idempotencyKey,
            )
        if (existing != null) {
            recordAudit(metadata, existing.responseStatus)
            return ServerResponse.status(existing.responseStatus)
                .bodyValueAndAwait(objectMapper.readTree(existing.responseBody))
        }

        val responseBody = block(metadata.userId)
        val responseStatus = successStatus.value()
        val serializedResponse = objectMapper.writeValueAsString(responseBody)

        idempotencyRecordRepository.save(
            IdempotencyRecord(
                grantId = metadata.grantId,
                requestMethod = metadata.requestMethod,
                requestPath = metadata.requestPath,
                idempotencyKey = metadata.idempotencyKey,
                responseStatus = responseStatus,
                responseBody = serializedResponse,
            ),
        )
        recordAudit(metadata, responseStatus)

        return ServerResponse.status(successStatus).bodyValueAndAwait(responseBody)
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
}
