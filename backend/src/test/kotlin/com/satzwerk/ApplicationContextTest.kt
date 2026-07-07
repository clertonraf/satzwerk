package com.satzwerk

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationContextTest : PostgresTestContainer() {
    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `context loads and health endpoint returns UP`() {
        webTestClient
            .get().uri("/actuator/health")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
    }
}
