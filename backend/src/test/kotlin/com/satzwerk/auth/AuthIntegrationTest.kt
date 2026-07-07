package com.satzwerk.auth

import com.satzwerk.PostgresTestContainer
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(AuthIntegrationTest.TestProtectedPingController::class)
class AuthIntegrationTest : PostgresTestContainer() {
    @Autowired
    lateinit var client: WebTestClient

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
}
