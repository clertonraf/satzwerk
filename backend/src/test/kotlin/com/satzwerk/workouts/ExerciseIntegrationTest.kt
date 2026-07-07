package com.satzwerk.workouts

import com.satzwerk.PostgresTestContainer
import com.satzwerk.auth.AuthResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ExerciseIntegrationTest : PostgresTestContainer() {
    @Autowired
    lateinit var client: WebTestClient

    private lateinit var authToken: String
    private lateinit var otherUserToken: String

    @BeforeEach
    fun setup() {
        val suffix = UUID.randomUUID()
        authToken = registerAndLogin("user1-$suffix@test.com", "password123", "User One")
        otherUserToken = registerAndLogin("user2-$suffix@test.com", "password123", "User Two")
    }

    @Test
    fun `create exercise returns created exercise for authenticated user`() {
        client
            .post()
            .uri("/api/exercises")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "name" to "Bench Press",
                    "muscleGroup" to "CHEST",
                    "description" to "Flat barbell bench press",
                ),
            ).exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.id").isNotEmpty
            .jsonPath("$.name").isEqualTo("Bench Press")
            .jsonPath("$.muscleGroup").isEqualTo("CHEST")
    }

    @Test
    fun `list exercises returns only authenticated user's exercises`() {
        createExercise(authToken, "Bench Press", "CHEST")
        createExercise(authToken, "Incline Dumbbell Press", "CHEST")
        createExercise(otherUserToken, "Barbell Row", "BACK")

        client
            .get()
            .uri("/api/exercises")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)
            .jsonPath("$[0].name").isEqualTo("Bench Press")
            .jsonPath("$[1].name").isEqualTo("Incline Dumbbell Press")
    }

    @Test
    fun `list exercises filters by muscle group`() {
        createExercise(authToken, "Bench Press", "CHEST")
        createExercise(authToken, "Barbell Row", "BACK")

        client
            .get()
            .uri("/api/exercises?muscleGroup=CHEST")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].name").isEqualTo("Bench Press")
            .jsonPath("$[0].muscleGroup").isEqualTo("CHEST")
    }

    @Test
    fun `get exercise by id returns exercise details`() {
        val exerciseId = createExercise(authToken, "Bench Press", "CHEST")

        client
            .get()
            .uri("/api/exercises/$exerciseId")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(exerciseId.toString())
            .jsonPath("$.name").isEqualTo("Bench Press")
            .jsonPath("$.muscleGroup").isEqualTo("CHEST")
    }

    @Test
    fun `get other user's exercise returns forbidden`() {
        val exerciseId = createExercise(otherUserToken, "Barbell Row", "BACK")

        client
            .get()
            .uri("/api/exercises/$exerciseId")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `update exercise changes only provided fields`() {
        val exerciseId = createExercise(authToken, "Bench Press", "CHEST")

        client
            .patch()
            .uri("/api/exercises/$exerciseId")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to "Paused Bench Press"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(exerciseId.toString())
            .jsonPath("$.name").isEqualTo("Paused Bench Press")
            .jsonPath("$.muscleGroup").isEqualTo("CHEST")
            .jsonPath("$.description").doesNotExist()
    }

    @Test
    fun `delete exercise removes it and subsequent get returns not found`() {
        val exerciseId = createExercise(authToken, "Bench Press", "CHEST")

        client
            .delete()
            .uri("/api/exercises/$exerciseId")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isNoContent

        client
            .get()
            .uri("/api/exercises/$exerciseId")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `delete other user's exercise returns forbidden`() {
        val exerciseId = createExercise(otherUserToken, "Barbell Row", "BACK")

        client
            .delete()
            .uri("/api/exercises/$exerciseId")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `create exercise with missing required fields returns bad request`() {
        client
            .post()
            .uri("/api/exercises")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(emptyMap<String, String>())
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `list exercises without authentication returns unauthorized`() {
        client
            .get()
            .uri("/api/exercises")
            .exchange()
            .expectStatus().isUnauthorized
    }

    private fun createExercise(
        token: String,
        name: String,
        muscleGroup: String,
    ): UUID {
        val response =
            client
                .post()
                .uri("/api/exercises")
                .header("Authorization", "Bearer $token")
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

        return response.id
    }

    private fun registerAndLogin(
        email: String,
        password: String,
        displayName: String,
    ): String {
        val response =
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

        return response.accessToken
    }
}
