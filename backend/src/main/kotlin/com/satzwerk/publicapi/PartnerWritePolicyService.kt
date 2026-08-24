package com.satzwerk.publicapi

import com.satzwerk.common.PartnerAppRequestPrincipal
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import java.util.UUID

typealias PartnerWriteRequestFingerprintCodec = PublicWriteRequestFingerprintCodec
typealias IdempotencyRecord = PublicWriteIdempotencyRecord
typealias IdempotencyRecordRepository = PublicWriteIdempotencyRecordRepository
typealias PartnerWriteAuditEntry = PublicWriteAuditEntry
typealias PartnerWriteAuditRepository = PublicWriteAuditRepository

@Service
class PartnerWritePolicyService(
    private val publicWritePolicyService: PublicWritePolicyService,
) {
    suspend fun <T : Any> execute(
        partnerPrincipal: PartnerAppRequestPrincipal,
        request: ServerRequest,
        successStatus: HttpStatus,
        requestFingerprintCodec: PartnerWriteRequestFingerprintCodec,
        block: suspend (UUID) -> T,
    ): ServerResponse =
        publicWritePolicyService.execute(
            publicWritePrincipal =
                PublicWritePrincipal(
                    principalType = PublicWritePrincipalType.PARTNER_APP,
                    userId = partnerPrincipal.userId,
                    credentialId = partnerPrincipal.grantId,
                    scopes = partnerPrincipal.scopes,
                    appId = partnerPrincipal.appId,
                    grantId = partnerPrincipal.grantId,
                ),
            request = request,
            successStatus = successStatus,
            requestFingerprintCodec = requestFingerprintCodec,
            block = block,
        )
}
