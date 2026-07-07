package com.satzwerk.auth

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.security.Principal
import java.time.Instant
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(AuthIntegrationTest.TestProtectedPingController::class)
@Testcontainers
class AuthIntegrationTest {
    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.r2dbc.url") {
                "r2dbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/${postgres.databaseName}"
            }
            registry.add("spring.r2dbc.username", postgres::getUsername)
            registry.add("spring.r2dbc.password", postgres::getPassword)
            registry.add("spring.flyway.url", postgres::getJdbcUrl)
            registry.add("spring.flyway.user", postgres::getUsername)
            registry.add("spring.flyway.password", postgres::getPassword)
        }
    }

    @Autowired
    lateinit var client: WebTestClient

    @Autowired
    lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    lateinit var jwtService: JwtService

    @Test
    fun `register creates user and returns token pair`() {
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
            .expectBody()
            .jsonPath("$.accessToken").isNotEmpty
            .jsonPath("$.refreshToken").isNotEmpty
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
    fun `login returns token pair for valid credentials`() {
        client
            .post()
            .uri("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "email" to "cora@example.com",
                    "password" to "password123",
                    "displayName" to "Cora",
                ),
            ).exchange()
            .expectStatus().isCreated

        client
            .post()
            .uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "email" to "cora@example.com",
                    "password" to "password123",
                ),
            ).exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.accessToken").isNotEmpty
            .jsonPath("$.refreshToken").isNotEmpty
    }

    @Test
    fun `login rejects wrong password`() {
        client
            .post()
            .uri("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "email" to "dani@example.com",
                    "password" to "password123",
                    "displayName" to "Dani",
                ),
            ).exchange()
            .expectStatus().isCreated

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
    fun `refresh rotates refresh token`() {
        val registered =
            client
                .post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    mapOf(
                        "email" to "erika@example.com",
                        "password" to "password123",
                        "displayName" to "Erika",
                    ),
                ).exchange()
                .expectStatus().isCreated
                .expectBody(AuthResponse::class.java)
                .returnResult()
                .responseBody!!

        val refreshed =
            client
                .post()
                .uri("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapOf("refreshToken" to registered.refreshToken))
                .exchange()
                .expectStatus().isOk
                .expectBody(AuthResponse::class.java)
                .returnResult()
                .responseBody!!

        assertNotEquals(registered.refreshToken, refreshed.refreshToken)
        assertNotEquals(registered.accessToken, refreshed.accessToken)

        client
            .post()
            .uri("/api/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("refreshToken" to registered.refreshToken))
            .exchange()
            .expectStatus().isUnauthorized
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
        val registered =
            client
                .post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    mapOf(
                        "email" to "fabi@example.com",
                        "password" to "password123",
                        "displayName" to "Fabi",
                    ),
                ).exchange()
                .expectStatus().isCreated
                .expectBody(AuthResponse::class.java)
                .returnResult()
                .responseBody!!

        client
            .get()
            .uri("/api/protected-ping")
            .header("Authorization", "Bearer ${registered.accessToken}")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.ok").isEqualTo(true)
            .jsonPath("$.user").isNotEmpty
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

    @Test
    fun `refresh cleans up expired tokens older than 30 days`(): Unit =
        runBlocking {
            val suffix = UUID.randomUUID()
            val registered = registerUser("cleanup-$suffix@test.com")

            // Seed a stale expired token for this user (>30 days old) directly.
            refreshTokenRepository.save(
                RefreshToken(
                    userId = jwtService.validateAccessToken(registered.accessToken),
                    tokenHash = "stale-hash-$suffix",
                    expiresAt = Instant.now().minusSeconds(31L * 86400L),
                ),
            )
            val beforeCount = refreshTokenRepository.count()

            client
                .post()
                .uri("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapOf("refreshToken" to registered.refreshToken))
                .exchange()
                .expectStatus().isOk

            val afterCount = refreshTokenRepository.count()
            // Cleanup deletes the stale token (-1) while refresh issues a new token (+1); net 0.
            // Without cleanup, afterCount would be beforeCount + 1 (stale token not removed).
            assertEquals(beforeCount, afterCount)
        }

    private fun registerUser(email: String): AuthResponse =
        client
            .post()
            .uri("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf("email" to email, "password" to "password123", "displayName" to "Cleanup User"),
            )
            .exchange()
            .expectStatus().isCreated
            .expectBody(AuthResponse::class.java)
            .returnResult()
            .responseBody!!
}
