package com.satzwerk.workouts

import com.satzwerk.PostgresTestContainer
import com.satzwerk.auth.AuthResponse
import com.satzwerk.users.UserRepository
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant
import java.util.UUID

@AutoConfigureObservability
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ExerciseCacheIntegrationTest : PostgresTestContainer() {
    @Autowired
    lateinit var client: WebTestClient

    @Autowired
    lateinit var meterRegistry: MeterRegistry

    @Autowired
    lateinit var exerciseCatalogCache: ExerciseCatalogCache

    @Autowired
    lateinit var userRepository: UserRepository

    private lateinit var authToken: String
    private lateinit var userEmail: String

    @BeforeEach
    fun setup() {
        userEmail = "exercise-cache-${UUID.randomUUID()}@test.com"
        authToken = registerAndLogin(userEmail, "password123", "Cache User")
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

    @Test
    fun `exercise cache ignores stale write from older version after invalidation`(): Unit =
        runBlocking {
            val userId = requireNotNull(userRepository.findByEmail(userEmail)?.id)
            val staleLookup = exerciseCatalogCache.lookup(userId, muscleGroup = null)

            exerciseCatalogCache.invalidateUser(userId)
            exerciseCatalogCache.put(
                userId = userId,
                muscleGroup = null,
                version = staleLookup.version,
                exercises =
                    listOf(
                        ExerciseResponse(
                            id = UUID.randomUUID(),
                            name = "Stale Bench",
                            muscleGroup = "CHEST",
                            description = null,
                            videoUrl = null,
                            equipment = null,
                            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                            updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
                        ),
                    ),
            )

            val freshLookup = exerciseCatalogCache.lookup(userId, muscleGroup = null)

            assertEquals(staleLookup.version + 1, freshLookup.version)
            assertNull(freshLookup.value)
        }

    @Test
    fun `exercise list invalidates after update`() {
        val exercise = createExercise("Bench Press", "CHEST")
        val missBefore = cacheCounter("exercise-catalog", "miss")
        val hitBefore = cacheCounter("exercise-catalog", "hit")

        listExercises(expectedCount = 1)
        listExercises(expectedCount = 1)

        updateExercise(exercise.id, "Paused Bench Press")

        client
            .get()
            .uri("/api/exercises")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].name").isEqualTo("Paused Bench Press")

        assertEquals(2.0, cacheCounter("exercise-catalog", "miss") - missBefore)
        assertEquals(1.0, cacheCounter("exercise-catalog", "hit") - hitBefore)
    }

    @Test
    fun `exercise list invalidates after delete`() {
        val exercise = createExercise("Bench Press", "CHEST")
        val missBefore = cacheCounter("exercise-catalog", "miss")
        val hitBefore = cacheCounter("exercise-catalog", "hit")

        listExercises(expectedCount = 1)
        listExercises(expectedCount = 1)

        deleteExercise(exercise.id)
        listExercises(expectedCount = 0)

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
    ): ExerciseResponse =
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
            .expectBody(ExerciseResponse::class.java)
            .returnResult()
            .responseBody!!

    private fun updateExercise(
        exerciseId: UUID,
        name: String,
    ) {
        client
            .patch()
            .uri("/api/exercises/$exerciseId")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to name))
            .exchange()
            .expectStatus().isOk
    }

    private fun deleteExercise(exerciseId: UUID) {
        client
            .delete()
            .uri("/api/exercises/$exerciseId")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isNoContent
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
