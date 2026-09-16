package com.satzwerk.auth

import com.satzwerk.PostgresTestContainer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.http.ResponseCookie
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal
import java.time.Instant
import java.util.UUID

private const val TEST_REFRESH_COOKIE_NAME = "refresh_token"
private const val TEST_CSRF_COOKIE_NAME = "refresh_csrf"
private const val TEST_CSRF_HEADER_NAME = "X-CSRF-Token"
private const val TEST_REFRESH_MAX_AGE_SECONDS = 604800L

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(AuthIntegrationTest.TestProtectedPingController::class)
class AuthIntegrationTest : PostgresTestContainer() {
    @Autowired
    lateinit var client: WebTestClient

    @Autowired
    lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    lateinit var jwtService: JwtService

    @Test
    fun `register creates user, returns csrf token, and sets auth cookies`() {
        val result =
            client
                .post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    mapOf(
                        "email" to "ana@example.com",
                        "password" to "password123",
                        "displayName" to "Ana",
                    ),
                ).exchange()
                .expectStatus().isCreated
                .expectBody(AuthResponse::class.java)
                .returnResult()

        val response = result.responseBody!!
        assertTrue(response.accessToken.isNotBlank())
        assertTrue(response.csrfToken.isNotBlank())
        assertCookiePair(
            result.responseCookies.getFirst(TEST_REFRESH_COOKIE_NAME),
            result.responseCookies.getFirst(TEST_CSRF_COOKIE_NAME),
        )
        assertEquals(response.csrfToken, result.responseCookies.getFirst(TEST_CSRF_COOKIE_NAME)?.value)
    }

    @Test
    fun `register rejects duplicate email`() {
        val body =
            mapOf(
                "email" to "bia@example.com",
                "password" to "password123",
                "displayName" to "Bia",
            )

        client
            .post()
            .uri("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isCreated

        client
            .post()
            .uri("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isEqualTo(409)
    }

    @Test
    fun `register validates request body`() {
        client
            .post()
            .uri("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "email" to "not-an-email",
                    "password" to "short",
                    "displayName" to "",
                ),
            ).exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.errors.email").isNotEmpty
            .jsonPath("$.errors.password").isNotEmpty
            .jsonPath("$.errors.displayName").isNotEmpty
    }

    @Test
    fun `login returns access token, csrf token, and auth cookies for valid credentials`() {
        registerUser("cora@example.com")

        val loggedIn = loginUser("cora@example.com")

        assertNotEquals("", loggedIn.body.accessToken)
        assertNotEquals("", loggedIn.body.csrfToken)
        assertCookiePair(loggedIn.refreshCookie, loggedIn.csrfCookie)
        assertEquals(loggedIn.body.csrfToken, loggedIn.csrfCookie.value)
    }

    @Test
    fun `login rejects wrong password`() {
        registerUser("dani@example.com")

        client
            .post()
            .uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "email" to "dani@example.com",
                    "password" to "wrong-password",
                ),
            ).exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `refresh rotates refresh token and csrf token when csrf header matches cookie`() {
        val registered = registerUser("erika@example.com")

        val refreshed = refreshUser(registered)

        assertNotEquals(registered.refreshCookie.value, refreshed.refreshCookie.value)
        assertNotEquals(registered.csrfCookie.value, refreshed.csrfCookie.value)
        assertNotEquals(registered.body.accessToken, refreshed.body.accessToken)
        assertEquals(refreshed.body.csrfToken, refreshed.csrfCookie.value)

        client
            .post()
            .uri("/api/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .cookie(TEST_REFRESH_COOKIE_NAME, registered.refreshCookie.value)
            .cookie(TEST_CSRF_COOKIE_NAME, registered.csrfCookie.value)
            .header(TEST_CSRF_HEADER_NAME, registered.body.csrfToken)
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.error").isEqualTo("Invalid refresh token")
    }

    @Test
    fun `refresh rejects csrf token mismatch`() {
        val registered = registerUser("fran@example.com")

        client
            .post()
            .uri("/api/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .cookie(TEST_REFRESH_COOKIE_NAME, registered.refreshCookie.value)
            .cookie(TEST_CSRF_COOKIE_NAME, registered.csrfCookie.value)
            .header(TEST_CSRF_HEADER_NAME, "wrong-token")
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.error").isEqualTo("CSRF token mismatch")
    }

    @Test
    fun `refresh rejects legacy body-only client with clear csrf error`() {
        val registered = registerUser("legacy@example.com")

        client
            .post()
            .uri("/api/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("refreshToken" to registered.refreshCookie.value))
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.error").isEqualTo("Missing CSRF token header")
    }

    @Test
    fun `refresh rejects missing csrf header gracefully`() {
        val registered = registerUser("gina@example.com")

        client
            .post()
            .uri("/api/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .cookie(TEST_REFRESH_COOKIE_NAME, registered.refreshCookie.value)
            .cookie(TEST_CSRF_COOKIE_NAME, registered.csrfCookie.value)
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.error").isEqualTo("Missing CSRF token header")
    }

    @Test
    fun `logout revokes the refresh token and clears auth cookies`() {
        val registered = registerUser("helen@example.com")

        val result =
            client
                .post()
                .uri("/api/auth/logout")
                .cookie(TEST_REFRESH_COOKIE_NAME, registered.refreshCookie.value)
                .cookie(TEST_CSRF_COOKIE_NAME, registered.csrfCookie.value)
                .exchange()
                .expectStatus().isNoContent
                .expectBody()
                .returnResult()

        assertClearedCookie(result.responseCookies.getFirst(TEST_REFRESH_COOKIE_NAME), httpOnly = true)
        assertClearedCookie(result.responseCookies.getFirst(TEST_CSRF_COOKIE_NAME), httpOnly = false)

        client
            .post()
            .uri("/api/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .cookie(TEST_REFRESH_COOKIE_NAME, registered.refreshCookie.value)
            .cookie(TEST_CSRF_COOKIE_NAME, registered.csrfCookie.value)
            .header(TEST_CSRF_HEADER_NAME, registered.body.csrfToken)
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.error").isEqualTo("Invalid refresh token")
    }

    @Test
    fun `protected route rejects missing token`() {
        client
            .get()
            .uri("/api/protected-ping")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `protected route accepts valid token`() {
        val registered = registerUser("fabi@example.com")

        client
            .get()
            .uri("/api/protected-ping")
            .header("Authorization", "Bearer ${registered.body.accessToken}")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.ok").isEqualTo(true)
            .jsonPath("$.user").isNotEmpty
    }

    @Test
    fun `refresh cleans up expired tokens older than 30 days`(): Unit =
        runBlocking {
            val suffix = UUID.randomUUID()
            val registered = registerUser("cleanup-$suffix@test.com")

            refreshTokenRepository.save(
                RefreshToken(
                    userId = jwtService.validateAccessToken(registered.body.accessToken),
                    tokenHash = "stale-hash-$suffix",
                    csrfTokenHash = "stale-csrf-hash-$suffix",
                    expiresAt = Instant.now().minusSeconds(31L * 86400L),
                ),
            )
            val beforeCount = refreshTokenRepository.count()

            client
                .post()
                .uri("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(TEST_REFRESH_COOKIE_NAME, registered.refreshCookie.value)
                .cookie(TEST_CSRF_COOKIE_NAME, registered.csrfCookie.value)
                .header(TEST_CSRF_HEADER_NAME, registered.body.csrfToken)
                .exchange()
                .expectStatus().isOk

            val afterCount = refreshTokenRepository.count()
            assertEquals(beforeCount, afterCount)
        }

    @RestController
    @TestConfiguration
    class TestProtectedPingController {
        @GetMapping("/api/protected-ping")
        suspend fun ping(principal: Principal): Map<String, Any> =
            mapOf(
                "ok" to true,
                "user" to principal.name,
            )
    }

    private fun registerUser(email: String): AuthSession {
        val result =
            client
                .post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    mapOf(
                        "email" to email,
                        "password" to "password123",
                        "displayName" to "Test User",
                    ),
                ).exchange()
                .expectStatus().isCreated
                .expectBody(AuthResponse::class.java)
                .returnResult()

        return result.toAuthSession()
    }

    private fun loginUser(email: String): AuthSession {
        val result =
            client
                .post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    mapOf(
                        "email" to email,
                        "password" to "password123",
                    ),
                ).exchange()
                .expectStatus().isOk
                .expectBody(AuthResponse::class.java)
                .returnResult()

        return result.toAuthSession()
    }

    private fun refreshUser(session: AuthSession): AuthSession {
        val result =
            client
                .post()
                .uri("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(TEST_REFRESH_COOKIE_NAME, session.refreshCookie.value)
                .cookie(TEST_CSRF_COOKIE_NAME, session.csrfCookie.value)
                .header(TEST_CSRF_HEADER_NAME, session.body.csrfToken)
                .exchange()
                .expectStatus().isOk
                .expectBody(AuthResponse::class.java)
                .returnResult()

        return result.toAuthSession()
    }

    private fun EntityExchangeResult<AuthResponse>.toAuthSession(): AuthSession =
        AuthSession(
            body = responseBody!!,
            refreshCookie =
                requireNotNull(responseCookies.getFirst(TEST_REFRESH_COOKIE_NAME)),
            csrfCookie =
                requireNotNull(responseCookies.getFirst(TEST_CSRF_COOKIE_NAME)),
        )

    private fun assertCookiePair(
        refreshCookie: ResponseCookie?,
        csrfCookie: ResponseCookie?,
    ) {
        val refresh = requireNotNull(refreshCookie)
        val csrf = requireNotNull(csrfCookie)

        assertEquals(TEST_REFRESH_COOKIE_NAME, refresh.name)
        assertTrue(refresh.isHttpOnly)
        assertEquals("Lax", refresh.sameSite)
        assertEquals("/api/auth", refresh.path)
        assertFalse(refresh.isSecure)
        assertEquals(TEST_REFRESH_MAX_AGE_SECONDS, refresh.maxAge.seconds)
        assertTrue(refresh.value.isNotBlank())

        assertEquals(TEST_CSRF_COOKIE_NAME, csrf.name)
        assertFalse(csrf.isHttpOnly)
        assertEquals("Lax", csrf.sameSite)
        assertEquals("/api/auth", csrf.path)
        assertFalse(csrf.isSecure)
        assertEquals(TEST_REFRESH_MAX_AGE_SECONDS, csrf.maxAge.seconds)
        assertTrue(csrf.value.isNotBlank())
    }

    private fun assertClearedCookie(
        cookie: ResponseCookie?,
        httpOnly: Boolean,
    ) {
        val actual = requireNotNull(cookie)
        assertEquals("/api/auth", actual.path)
        assertEquals("Lax", actual.sameSite)
        assertEquals(0, actual.maxAge.seconds)
        assertEquals(httpOnly, actual.isHttpOnly)
        assertFalse(actual.isSecure)
        assertEquals("", actual.value)
    }

    private data class AuthSession(
        val body: AuthResponse,
        val refreshCookie: ResponseCookie,
        val csrfCookie: ResponseCookie,
    )
}
