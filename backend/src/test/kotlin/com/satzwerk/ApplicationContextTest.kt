package com.satzwerk

import com.satzwerk.auth.AuthResponse
import com.satzwerk.auth.CreatedPersonalApiTokenResponse
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

@AutoConfigureObservability
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationContextTest : PostgresTestContainer() {
    @Autowired
    lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var meterRegistry: MeterRegistry

    @Test
    fun `context loads and health endpoint returns UP`() {
        webTestClient
            .get().uri("/actuator/health")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
    }

    @Test
    fun `prometheus endpoint requires authentication`() {
        webTestClient
            .get().uri("/actuator/prometheus")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `prometheus endpoint rejects personal api token authentication`() {
        val jwt = registerAndLogin()
        val personalApiToken = createPersonalApiToken(jwt)

        webTestClient
            .get()
            .uri("/actuator/prometheus")
            .header("Authorization", "Bearer $personalApiToken")
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `jwt-authenticated prometheus endpoint exposes concrete r2dbc jvm and http metrics`() {
        val jwt = registerAndLogin()

        webTestClient
            .get().uri("/actuator/health")
            .exchange()
            .expectStatus().isOk

        webTestClient
            .post()
            .uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "email" to "metrics@example.com",
                    "password" to "wrong-password",
                ),
            ).exchange()
            .expectStatus().isUnauthorized

        val body =
            webTestClient
                .get()
                .uri("/actuator/prometheus")
                .header("Authorization", "Bearer $jwt")
                .exchange()
                .expectStatus().isOk
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_PLAIN)
                .expectBody(String::class.java)
                .returnResult()
                .responseBody!!

        assertThat(body).contains("r2dbc_pool_acquired_connections")
        assertThat(body).contains("r2dbc_pool_pending_connections")
        assertThat(body).contains("r2dbc_pool_max_allocated_connections")
        assertThat(body).contains("jvm_memory_used_bytes")
        assertThat(body).contains("http_server_requests_seconds")
        assertThat(body).contains("uri=\"/api/auth/login\"")
        assertThat(body).contains("uri=\"/actuator/health\"")

        assertThat(meterRegistry.meters.map { it.id.name })
            .contains("http.server.requests")
            .contains("r2dbc.pool.acquired", "r2dbc.pool.pending", "r2dbc.pool.max.allocated")
    }

    private fun registerAndLogin(): String =
        webTestClient
            .post()
            .uri("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "email" to "metrics-${System.nanoTime()}@example.com",
                    "password" to "password123",
                    "displayName" to "Metrics Tester",
                ),
            ).exchange()
            .expectStatus().isCreated
            .expectBody(AuthResponse::class.java)
            .returnResult()
            .responseBody!!
            .accessToken

    private fun createPersonalApiToken(jwt: String): String =
        webTestClient
            .post()
            .uri("/api/tokens")
            .header("Authorization", "Bearer $jwt")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "name" to "Metrics PAT",
                    "scopes" to listOf("exercises:read"),
                ),
            ).exchange()
            .expectStatus().isCreated
            .expectBody(CreatedPersonalApiTokenResponse::class.java)
            .returnResult()
            .responseBody!!
            .token
}
