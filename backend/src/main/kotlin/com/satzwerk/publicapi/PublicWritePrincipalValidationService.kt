package com.satzwerk.publicapi

import com.satzwerk.common.PartnerAppRequestPrincipal
import com.satzwerk.common.PersonalApiTokenRequestPrincipal
import com.satzwerk.common.RequestContext
import com.satzwerk.common.UnauthorizedException
import com.satzwerk.partners.PartnerAppService
import org.springframework.stereotype.Service

private const val APP_TOKEN_HEADER = "X-App-Token"

@Service
class PublicWritePrincipalValidationService(
    private val partnerAppService: PartnerAppService,
) {
    suspend fun requireValidPrincipal(ctx: RequestContext): PublicWritePrincipal =
        when (val principal = ctx.principal()) {
            is PersonalApiTokenRequestPrincipal ->
                PublicWritePrincipal(
                    principalType = PublicWritePrincipalType.PERSONAL_API_TOKEN,
                    userId = principal.userId,
                    credentialId = principal.tokenId,
                    scopes = principal.scopes,
                )

            is PartnerAppRequestPrincipal -> {
                revalidateActiveGrant(ctx, principal)
                principal.toPublicWritePrincipal()
            }

            else -> throw UnauthorizedException()
        }

    private suspend fun revalidateActiveGrant(
        ctx: RequestContext,
        partnerPrincipal: PartnerAppRequestPrincipal,
    ) {
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

private fun PartnerAppRequestPrincipal.toPublicWritePrincipal(): PublicWritePrincipal =
    PublicWritePrincipal(
        principalType = PublicWritePrincipalType.PARTNER_APP,
        userId = userId,
        credentialId = grantId,
        scopes = scopes,
        appId = appId,
        grantId = grantId,
    )
