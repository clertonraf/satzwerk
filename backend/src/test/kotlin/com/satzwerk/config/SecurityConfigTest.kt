package com.satzwerk.config

import com.satzwerk.PostgresTestContainer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SecurityConfigTest : PostgresTestContainer() {
    @Autowired
    lateinit var client: WebTestClient

    @Test
    fun `unauthenticated request returns 401 without WWW-Authenticate header`(): Unit =
        run {
            client
                .get()
                .uri("/api/exercises")
                .exchange()
                .expectStatus().isUnauthorized
                .expectHeader().doesNotExist(HttpHeaders.WWW_AUTHENTICATE)
                .expectBody()
                .jsonPath("$.message").isEqualTo("Unauthorized")
                .jsonPath("$.error").isEqualTo("Unauthorized")
        }
}
