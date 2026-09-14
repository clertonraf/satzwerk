package com.satzwerk

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
    fun `prometheus endpoint exposes r2dbc jvm and http metrics`() {
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
                .get().uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_PLAIN)
                .expectBody(String::class.java)
                .returnResult()
                .responseBody!!

        assertThat(body).contains("r2dbc_pool")
        assertThat(body).contains("jvm_memory_used_bytes")
        assertThat(body).contains("http_server_requests_seconds")
        assertThat(body).contains("uri=\"/api/auth/login\"")
        assertThat(body).contains("uri=\"/actuator/health\"")

        assertThat(meterRegistry.meters.map { it.id.name })
            .contains("http.server.requests")
            .anyMatch { it.startsWith("r2dbc.pool.") }
    }
}
