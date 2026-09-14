package com.satzwerk.workouts

import com.satzwerk.PostgresTestContainer
import com.satzwerk.auth.AuthResponse
import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID

@AutoConfigureObservability
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ExerciseCacheIntegrationTest : PostgresTestContainer() {
    @Autowired
    lateinit var client: WebTestClient

    @Autowired
    lateinit var meterRegistry: MeterRegistry

    private lateinit var authToken: String

    @BeforeEach
    fun setup() {
        authToken = registerAndLogin("exercise-cache-${UUID.randomUUID()}@test.com", "password123", "Cache User")
    }

    @Test
    fun `exercise list uses cache and invalidates after writes`() {
        createExercise("Bench Press", "CHEST")

        val missBefore = cacheCounter("exercise-catalog", "miss")
        val hitBefore = cacheCounter("exercise-catalog", "hit")

        listExercises(expectedCount = 1)
        listExercises(expectedCount = 1)

        assertEquals(1.0, cacheCounter("exercise-catalog", "miss") - missBefore)
        assertEquals(1.0, cacheCounter("exercise-catalog", "hit") - hitBefore)

        createExercise("Incline Bench Press", "CHEST")
        listExercises(expectedCount = 2)

        assertEquals(2.0, cacheCounter("exercise-catalog", "miss") - missBefore)
        assertEquals(1.0, cacheCounter("exercise-catalog", "hit") - hitBefore)
    }

    private fun listExercises(expectedCount: Int) {
        client
            .get()
            .uri("/api/exercises")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(expectedCount)
    }

    private fun createExercise(
        name: String,
        muscleGroup: String,
    ) {
        client
            .post()
            .uri("/api/exercises")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "name" to name,
                    "muscleGroup" to muscleGroup,
                ),
            ).exchange()
            .expectStatus().isCreated
    }

    private fun registerAndLogin(
        email: String,
        password: String,
        displayName: String,
    ): String =
        client
            .post()
            .uri("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "email" to email,
                    "password" to password,
                    "displayName" to displayName,
                ),
            ).exchange()
            .expectStatus().isCreated
            .expectBody(AuthResponse::class.java)
            .returnResult()
            .responseBody!!
            .accessToken

    private fun cacheCounter(
        cacheName: String,
        result: String,
    ): Double =
        meterRegistry
            .find("satzwerk.cache.requests")
            .tags("cache", cacheName, "result", result)
            .counter()
            ?.count() ?: 0.0
}
