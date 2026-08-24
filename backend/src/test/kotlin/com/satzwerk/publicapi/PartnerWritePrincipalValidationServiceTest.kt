package com.satzwerk.publicapi

import com.satzwerk.auth.PersonalApiToken
import com.satzwerk.common.PartnerAppRequestPrincipal
import com.satzwerk.common.RequestContext
import com.satzwerk.common.UnauthorizedException
import com.satzwerk.partners.AppGrant
import com.satzwerk.partners.PartnerAppService
import com.satzwerk.partners.PartnerPrincipal
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.web.reactive.function.server.ServerRequest
import reactor.core.publisher.Mono
import java.util.UUID

class PartnerWritePrincipalValidationServiceTest {
    @Test
    fun `requireValidPrincipal returns the partner request principal for valid partner requests`(): Unit =
        runBlocking {
            val validatedService =
                PartnerWritePrincipalValidationService(
                    PublicWritePrincipalValidationService(
                        mock<PartnerAppService> {
                            onBlocking { resolveActiveGrant(APP_TOKEN) } doReturn activeGrant()
                        },
                    ),
                )

            val actual = validatedService.requireValidPrincipal(RequestContext(partnerAppRequest()))

            assertEquals(
                PartnerAppRequestPrincipal(
                    userId = USER_ID,
                    appId = APP_ID,
                    grantId = GRANT_ID,
                    scopes = setOf("exercises:write"),
                    partnerPrincipal =
                        PartnerPrincipal(
                            userId = USER_ID.toString(),
                            appId = APP_ID.toString(),
                            grantId = GRANT_ID.toString(),
                            grantedScopes = "exercises:write",
                        ),
                ),
                actual,
            )
        }

    @Test
    fun `requireValidPrincipal rejects non-partner principals even when public writes are allowed`() {
        val validatedService =
            PartnerWritePrincipalValidationService(
                PublicWritePrincipalValidationService(mock()),
            )

        assertThrows<UnauthorizedException> {
            runBlocking { validatedService.requireValidPrincipal(RequestContext(personalApiTokenRequest())) }
        }
    }

    private fun activeGrant(): AppGrant =
        AppGrant(
            id = GRANT_ID,
            appId = APP_ID,
            userId = USER_ID,
            grantedScopes = "exercises:write",
            accessTokenHash = "hash",
        )

    private fun partnerAppRequest(): ServerRequest {
        val principal =
            UsernamePasswordAuthenticationToken(
                USER_ID.toString(),
                PartnerPrincipal(
                    userId = USER_ID.toString(),
                    appId = APP_ID.toString(),
                    grantId = GRANT_ID.toString(),
                    grantedScopes = "exercises:write",
                ),
                listOf(SimpleGrantedAuthority("exercises:write")),
            )
        return request(principal, APP_TOKEN)
    }

    private fun personalApiTokenRequest(): ServerRequest {
        val token =
            PersonalApiToken(
                id = TOKEN_ID,
                userId = USER_ID,
                name = "Automation token",
                tokenHash = "hash",
                scopesRaw = "exercises:write",
            )
        val principal =
            UsernamePasswordAuthenticationToken(
                USER_ID.toString(),
                token,
                listOf(SimpleGrantedAuthority("exercises:write")),
            )
        return request(principal)
    }

    private fun request(
        principal: UsernamePasswordAuthenticationToken,
        appToken: String? = null,
    ): ServerRequest {
        val headers =
            mock<ServerRequest.Headers> {
                on { firstHeader("X-App-Token") } doReturn appToken
            }

        return mock {
            on { headers() } doReturn headers
            on { principal() } doReturn Mono.just(principal)
        }
    }

    companion object {
        private val USER_ID: UUID = UUID.randomUUID()
        private val TOKEN_ID: UUID = UUID.randomUUID()
        private val APP_ID: UUID = UUID.randomUUID()
        private val GRANT_ID: UUID = UUID.randomUUID()
        private const val APP_TOKEN = "app-token"
    }
}
