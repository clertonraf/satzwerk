package com.satzwerk.publicapi

import com.satzwerk.common.RequestContext
import com.satzwerk.common.UnauthorizedException
import com.satzwerk.partners.AppGrant
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
    fun `requireValidPrincipal returns the authenticated partner principal when the active grant matches`(): Unit =
        runBlocking {
            val request = request()
            val expected = RequestContext(request).requirePartnerAppPrincipal()
            val activeGrant =
                AppGrant(
                    id = GRANT_ID,
                    appId = APP_ID,
                    userId = USER_ID,
                    grantedScopes = "exercises:write",
                    accessTokenHash = "hash",
                )
            val validatedService =
                PartnerWritePrincipalValidationService(
                    mock {
                        onBlocking { resolveActiveGrant(APP_TOKEN) } doReturn activeGrant
                    },
                )

            val actual = validatedService.requireValidPrincipal(RequestContext(request))

            assertEquals(expected, actual)
        }

    @Test
    fun `requireValidPrincipal rejects a stale partner principal when the active grant changes`() {
        val activeGrant =
            AppGrant(
                id = UUID.randomUUID(),
                appId = APP_ID,
                userId = USER_ID,
                grantedScopes = "exercises:write",
                accessTokenHash = "hash",
            )
        val validatedService =
            PartnerWritePrincipalValidationService(
                mock {
                    onBlocking { resolveActiveGrant(APP_TOKEN) } doReturn activeGrant
                },
            )

        assertThrows<UnauthorizedException> {
            runBlocking { validatedService.requireValidPrincipal(RequestContext(request())) }
        }
    }

    private fun request(): ServerRequest {
        val headers =
            mock<ServerRequest.Headers> {
                on { firstHeader("X-App-Token") } doReturn APP_TOKEN
            }
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

        return mock {
            on { headers() } doReturn headers
            on { principal() } doReturn Mono.just(principal)
        }
    }

    companion object {
        private val USER_ID: UUID = UUID.randomUUID()
        private val APP_ID: UUID = UUID.randomUUID()
        private val GRANT_ID: UUID = UUID.randomUUID()
        private const val APP_TOKEN = "app-token"
    }
}
