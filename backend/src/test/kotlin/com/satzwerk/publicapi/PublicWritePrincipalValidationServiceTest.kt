package com.satzwerk.publicapi

import com.satzwerk.auth.PersonalApiToken
import com.satzwerk.common.RequestContext
import com.satzwerk.common.UnauthorizedException
import com.satzwerk.config.AUTHORITY_JWT_SESSION
import com.satzwerk.partners.AppGrant
import com.satzwerk.partners.PartnerPrincipal
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyBlocking
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.web.reactive.function.server.ServerRequest
import reactor.core.publisher.Mono
import java.util.UUID

class PublicWritePrincipalValidationServiceTest {
    @Test
    fun `requireValidPrincipal returns a personal token principal without partner revalidation`(): Unit =
        runBlocking {
            val partnerAppService = mock<com.satzwerk.partners.PartnerAppService>()
            val validatedService = PublicWritePrincipalValidationService(partnerAppService)

            val actual = validatedService.requireValidPrincipal(RequestContext(personalApiTokenRequest()))

            assertEquals(
                PublicWritePrincipal(
                    principalType = PublicWritePrincipalType.PERSONAL_API_TOKEN,
                    userId = USER_ID,
                    credentialId = TOKEN_ID,
                    scopes = setOf("exercises:write"),
                    appId = null,
                    grantId = null,
                ),
                actual,
            )
            verifyBlocking(partnerAppService, never()) { resolveActiveGrant(org.mockito.kotlin.any()) }
        }

    @Test
    fun `requireValidPrincipal returns a public-write principal when the active partner grant matches`(): Unit =
        runBlocking {
            val activeGrant =
                AppGrant(
                    id = GRANT_ID,
                    appId = APP_ID,
                    userId = USER_ID,
                    grantedScopes = "exercises:write",
                    accessTokenHash = "hash",
                )
            val validatedService =
                PublicWritePrincipalValidationService(
                    mock {
                        onBlocking { resolveActiveGrant(APP_TOKEN) } doReturn activeGrant
                    },
                )

            val actual = validatedService.requireValidPrincipal(RequestContext(partnerAppRequest()))

            assertEquals(
                PublicWritePrincipal(
                    principalType = PublicWritePrincipalType.PARTNER_APP,
                    userId = USER_ID,
                    credentialId = GRANT_ID,
                    scopes = setOf("exercises:write"),
                    appId = APP_ID,
                    grantId = GRANT_ID,
                ),
                actual,
            )
        }

    @Test
    fun `requireValidPrincipal rejects partner principals when the active grant was revoked`() {
        val validatedService =
            PublicWritePrincipalValidationService(
                mock {
                    onBlocking { resolveActiveGrant(APP_TOKEN) } doReturn null
                },
            )

        assertThrows<UnauthorizedException> {
            runBlocking { validatedService.requireValidPrincipal(RequestContext(partnerAppRequest())) }
        }
    }

    @Test
    fun `requireValidPrincipal rejects partner principals when the active grant no longer matches`() {
        val mismatchedGrant =
            AppGrant(
                id = OTHER_GRANT_ID,
                appId = APP_ID,
                userId = USER_ID,
                grantedScopes = "exercises:write",
                accessTokenHash = "hash",
            )
        val validatedService =
            PublicWritePrincipalValidationService(
                mock {
                    onBlocking { resolveActiveGrant(APP_TOKEN) } doReturn mismatchedGrant
                },
            )

        assertThrows<UnauthorizedException> {
            runBlocking { validatedService.requireValidPrincipal(RequestContext(partnerAppRequest())) }
        }
    }

    @Test
    fun `requireValidPrincipal rejects jwt session principals`() {
        val validatedService = PublicWritePrincipalValidationService(mock())

        assertThrows<UnauthorizedException> {
            runBlocking { validatedService.requireValidPrincipal(RequestContext(jwtSessionRequest())) }
        }
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

    private fun jwtSessionRequest(): ServerRequest {
        val principal =
            UsernamePasswordAuthenticationToken(
                USER_ID.toString(),
                "jwt-token",
                listOf(SimpleGrantedAuthority(AUTHORITY_JWT_SESSION)),
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
        private val OTHER_GRANT_ID: UUID = UUID.randomUUID()
        private const val APP_TOKEN = "app-token"
    }
}
