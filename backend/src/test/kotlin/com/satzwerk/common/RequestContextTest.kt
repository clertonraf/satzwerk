package com.satzwerk.common

import com.satzwerk.config.AUTHORITY_JWT_SESSION
import com.satzwerk.partners.PartnerPrincipal
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.core.codec.DecodingException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.web.reactive.function.server.ServerRequest
import reactor.core.publisher.Mono
import java.security.Principal
import java.util.Optional
import java.util.UUID

class RequestContextTest {
    data class Payload(val value: String)

    private val request = mock(ServerRequest::class.java)
    private val ctx = RequestContext(request)

    @Test
    fun `userId returns UUID from principal name`() {
        val id = UUID.randomUUID()
        val principal = mock(Principal::class.java)
        `when`(principal.name).thenReturn(id.toString())
        `when`(request.principal()).thenReturn(Mono.just(principal))

        val result = runBlocking { ctx.userId() }

        assertEquals(id, result)
    }

    @Test
    fun `userId throws BadRequestException for invalid principal name`() {
        val principal = mock(Principal::class.java)
        `when`(principal.name).thenReturn("not-a-uuid")
        `when`(request.principal()).thenReturn(Mono.just(principal))

        val ex =
            assertThrows<BadRequestException> {
                runBlocking { ctx.userId() }
            }
        assertEquals("Invalid UUID: not-a-uuid", ex.message)
    }

    @Test
    fun `principal resolves partner app principal metadata`() {
        val userId = UUID.randomUUID()
        val appId = UUID.randomUUID()
        val grantId = UUID.randomUUID()
        val authentication =
            UsernamePasswordAuthenticationToken(
                userId.toString(),
                PartnerPrincipal(
                    userId = userId.toString(),
                    appId = appId.toString(),
                    grantId = grantId.toString(),
                    grantedScopes = "exercises:write",
                ),
                listOf(SimpleGrantedAuthority("exercises:write")),
            )
        `when`(request.principal()).thenReturn(Mono.just(authentication))

        val principal = runBlocking { ctx.principal() } as PartnerAppRequestPrincipal

        assertEquals(RequestPrincipalKind.PARTNER_APP, principal.kind)
        assertEquals(userId, principal.userId)
        assertEquals(appId, principal.appId)
        assertEquals(grantId, principal.grantId)
        assertEquals(setOf("exercises:write"), principal.scopes)
    }

    @Test
    fun `principal resolves jwt session authentication`() {
        val userId = UUID.randomUUID()
        val authentication =
            UsernamePasswordAuthenticationToken(
                userId.toString(),
                "jwt-token",
                listOf(SimpleGrantedAuthority(AUTHORITY_JWT_SESSION)),
            )
        `when`(request.principal()).thenReturn(Mono.just(authentication))

        val principal = runBlocking { ctx.principal() }

        assertEquals(JwtSessionRequestPrincipal(userId), principal)
    }

    @Test
    fun `principal resolves personal api token authentication scopes`() {
        val userId = UUID.randomUUID()
        val authentication =
            UsernamePasswordAuthenticationToken(
                userId.toString(),
                "satzwerk_token",
                listOf(
                    SimpleGrantedAuthority("analytics:read"),
                    SimpleGrantedAuthority("exercises:write"),
                ),
            )
        `when`(request.principal()).thenReturn(Mono.just(authentication))

        val principal = runBlocking { ctx.principal() }

        assertEquals(RequestPrincipalKind.PERSONAL_API_TOKEN, principal.kind)
        assertEquals(userId, principal.userId)
        assertEquals(setOf("analytics:read", "exercises:write"), principal.scopes)
    }

    @Test
    fun `requirePartnerAppPrincipal rejects non-partner authentication`() {
        val userId = UUID.randomUUID()
        val authentication =
            UsernamePasswordAuthenticationToken(
                userId.toString(),
                "jwt-token",
                listOf(SimpleGrantedAuthority(AUTHORITY_JWT_SESSION)),
            )
        `when`(request.principal()).thenReturn(Mono.just(authentication))

        assertThrows<UnauthorizedException> {
            runBlocking { ctx.requirePartnerAppPrincipal() }
        }
    }

    @Test
    fun `body returns deserialised object`() {
        val payload = Payload("hello")
        `when`(request.bodyToMono(Payload::class.java)).thenReturn(Mono.just(payload))

        val result = runBlocking { ctx.body<Payload>() }

        assertEquals(payload, result)
    }

    @Test
    fun `body throws BadRequestException on deserialization failure`() {
        `when`(request.bodyToMono(Payload::class.java)).thenReturn(
            Mono.error(DecodingException("bad json")),
        )

        assertThrows<BadRequestException> {
            runBlocking { ctx.body<Payload>() }
        }
    }

    @Test
    fun `pathId returns UUID from path variable`() {
        val id = UUID.randomUUID()
        `when`(request.pathVariable("id")).thenReturn(id.toString())

        assertEquals(id, ctx.pathId("id"))
    }

    @Test
    fun `pathId throws BadRequestException for invalid path variable`() {
        `when`(request.pathVariable("id")).thenReturn("not-a-uuid")

        assertThrows<BadRequestException> { ctx.pathId("id") }
    }

    @Test
    fun `queryParam returns value when present`() {
        `when`(request.queryParam("filter")).thenReturn(Optional.of("someValue"))

        assertEquals("someValue", ctx.queryParam("filter"))
    }

    @Test
    fun `header returns first header value when present`() {
        val headers = mock(ServerRequest.Headers::class.java)
        `when`(headers.firstHeader("X-Test")).thenReturn("value")
        `when`(request.headers()).thenReturn(headers)

        assertEquals("value", ctx.header("X-Test"))
    }

    @Test
    fun `queryParam returns null when absent`() {
        `when`(request.queryParam("filter")).thenReturn(Optional.empty())

        assertNull(ctx.queryParam("filter"))
    }

    @Test
    fun `body throws BadRequestException when body is absent`() {
        `when`(request.bodyToMono(Payload::class.java)).thenReturn(Mono.empty())

        val ex =
            assertThrows<BadRequestException> {
                runBlocking { ctx.body<Payload>() }
            }
        assertEquals("Request body is required", ex.message)
    }
}
