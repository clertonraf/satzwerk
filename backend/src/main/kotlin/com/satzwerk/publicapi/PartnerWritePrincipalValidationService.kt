package com.satzwerk.publicapi

import com.satzwerk.common.PartnerAppRequestPrincipal
import com.satzwerk.common.RequestContext
import com.satzwerk.common.UnauthorizedException
import com.satzwerk.partners.PartnerAppService
import org.springframework.stereotype.Service

private const val APP_TOKEN_HEADER = "X-App-Token"

@Service
class PartnerWritePrincipalValidationService(
    private val partnerAppService: PartnerAppService,
) {
    suspend fun requireValidPrincipal(ctx: RequestContext): PartnerAppRequestPrincipal =
        ctx.requirePartnerAppPrincipal().also { partnerPrincipal ->
            val appToken = ctx.header(APP_TOKEN_HEADER)?.trim().orEmpty()
            if (appToken.isBlank()) {
                throw UnauthorizedException()
            }

            val activeGrant = partnerAppService.resolveActiveGrant(appToken) ?: throw UnauthorizedException()
            val activeGrantId = activeGrant.id ?: throw UnauthorizedException()
            if (
                activeGrantId != partnerPrincipal.grantId ||
                activeGrant.appId != partnerPrincipal.appId ||
                activeGrant.userId != partnerPrincipal.userId
            ) {
                throw UnauthorizedException()
            }
        }
}
