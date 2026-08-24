package com.satzwerk.publicapi

import com.satzwerk.common.PartnerAppRequestPrincipal
import com.satzwerk.common.RequestContext
import org.springframework.stereotype.Service

@Service
class PartnerWritePrincipalValidationService(
    private val publicWritePrincipalValidationService: PublicWritePrincipalValidationService,
) {
    suspend fun requireValidPrincipal(ctx: RequestContext): PartnerAppRequestPrincipal {
        // Delegate active-grant and token validation to PublicWritePrincipalValidationService
        publicWritePrincipalValidationService.requireValidPrincipal(ctx)

        // Ensure the principal is a PartnerAppRequestPrincipal and return it for router contract
        val principal = ctx.requirePartnerAppPrincipal()
        return principal
    }
}
