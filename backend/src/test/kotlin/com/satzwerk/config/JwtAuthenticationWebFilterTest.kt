package com.satzwerk.config

import com.satzwerk.auth.JwtService
import com.satzwerk.auth.PersonalApiToken
import com.satzwerk.common.PersonalApiTokenRequestPrincipal
import com.satzwerk.common.RequestContext
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.mono
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.http.HttpHeaders
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.server.WebFilterChain
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

class JwtAuthenticationWebFilterTest {
    @Test
    fun `personal api token authentication is visible to downstream request context`() {
        val userId = UUID.randomUUID()
        val tokenId = UUID.randomUUID()
        val personalApiToken =
            PersonalApiToken(
                id = tokenId,
                userId = userId,
                name = "Automation token",
                tokenHash = "hash",
                scopesRaw = "analytics:read,exercises:write",
            )
        val filter =
            JwtAuthenticationWebFilter(
                jwtService = mock<JwtService>(),
                personalApiTokenService =
                    mock {
                        onBlocking { resolve("satzwerk_token") } doReturn personalApiToken
                    },
            )
        val exchange =
            MockServerWebExchange.from(
                MockServerHttpRequest.get("/")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer satzwerk_token"),
            )
        val observedPrincipal = AtomicReference<PersonalApiTokenRequestPrincipal>()
        val chain =
            WebFilterChain { currentExchange ->
                mono {
                    val securityContext = ReactiveSecurityContextHolder.getContext().awaitSingle()
                    val authentication = securityContext.authentication
                    val request =
                        mock<ServerRequest> {
                            on { principal() } doReturn mono { authentication }
                        }
                    observedPrincipal.set(RequestContext(request).principal() as PersonalApiTokenRequestPrincipal)
                }.then()
            }

        filter.filter(exchange, chain).block()

        assertEquals(
            PersonalApiTokenRequestPrincipal(
                userId = userId,
                tokenId = tokenId,
                scopes = setOf("analytics:read", "exercises:write"),
            ),
            observedPrincipal.get(),
        )
    }
}
