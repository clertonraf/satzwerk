package com.satzwerk.workouts

import com.satzwerk.auth.AuthResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class WorkoutPlanIntegrationTest {
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

    private lateinit var authToken: String
    private lateinit var otherUserToken: String
    private lateinit var exerciseId: UUID

    @BeforeEach
    fun setup() {
        val suffix = UUID.randomUUID()
        authToken = registerAndLogin("workout-$suffix@test.com", "password123", "Workout User")
        otherUserToken = registerAndLogin("other-$suffix@test.com", "password123", "Other User")
        exerciseId = createExercise(authToken, "Bench Press", "CHEST")
    }

    @Test
    fun `create plan returns created plan for authenticated user`() {
        client
            .post()
            .uri("/api/plans")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to "PPL"))
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.id").isNotEmpty
            .jsonPath("$.name").isEqualTo("PPL")
            .jsonPath("$.isActive").isEqualTo(false)
            .jsonPath("$.source").isEqualTo("MANUAL")
    }

    @Test
    fun `list plans returns created plan`() {
        createPlan(authToken, "PPL")

        client
            .get()
            .uri("/api/plans")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].name").isEqualTo("PPL")
            .jsonPath("$[0].source").isEqualTo("MANUAL")
    }

    @Test
    fun `get plan detail returns empty groups`() {
        val planId = createPlan(authToken, "PPL")

        client
            .get()
            .uri("/api/plans/$planId")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(planId.toString())
            .jsonPath("$.name").isEqualTo("PPL")
            .jsonPath("$.groups.length()").isEqualTo(0)
    }

    @Test
    fun `update plan changes name`() {
        val planId = createPlan(authToken, "PPL")

        client
            .patch()
            .uri("/api/plans/$planId")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to "Push Pull Legs"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(planId.toString())
            .jsonPath("$.name").isEqualTo("Push Pull Legs")
    }

    @Test
    fun `delete plan removes it`() {
        val planId = createPlan(authToken, "PPL")

        client
            .delete()
            .uri("/api/plans/$planId")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isNoContent

        client
            .get()
            .uri("/api/plans/$planId")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `activate plan keeps only one plan active`() {
        val planA = createPlan(authToken, "Plan A")
        val planB = createPlan(authToken, "Plan B")

        client
            .post()
            .uri("/api/plans/$planA/activate")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isNoContent

        client
            .post()
            .uri("/api/plans/$planB/activate")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isNoContent

        client
            .get()
            .uri("/api/plans")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[?(@.id == '" + planA + "')].isActive").isEqualTo(listOf(false))
            .jsonPath("$[?(@.id == '" + planB + "')].isActive").isEqualTo(listOf(true))
    }

    @Test
    fun `create group adds it to plan detail`() {
        val planId = createPlan(authToken, "PPL")

        client
            .post()
            .uri("/api/plans/$planId/groups")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("title" to "Treino A"))
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.id").isNotEmpty
            .jsonPath("$.title").isEqualTo("Treino A")

        client
            .get()
            .uri("/api/plans/$planId")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.groups.length()").isEqualTo(1)
            .jsonPath("$.groups[0].title").isEqualTo("Treino A")
    }

    @Test
    fun `update and delete group manage workout groups`() {
        val planId = createPlan(authToken, "PPL")
        val groupId = createGroup(authToken, planId, "Treino A")

        client
            .patch()
            .uri("/api/plans/$planId/groups/$groupId")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("title" to "Treino B"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(groupId.toString())
            .jsonPath("$.title").isEqualTo("Treino B")

        client
            .delete()
            .uri("/api/plans/$planId/groups/$groupId")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isNoContent

        client
            .get()
            .uri("/api/plans/$planId")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.groups.length()").isEqualTo(0)
    }

    @Test
    fun `create workout exercise returns created item`() {
        val planId = createPlan(authToken, "PPL")
        val groupId = createGroup(authToken, planId, "Treino A")

        client
            .post()
            .uri("/api/plans/$planId/groups/$groupId/exercises")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "exerciseId" to exerciseId,
                    "sets" to 4,
                    "reps" to 8,
                ),
            ).exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.id").isNotEmpty
            .jsonPath("$.exerciseId").isEqualTo(exerciseId.toString())
            .jsonPath("$.sets").isEqualTo(4)
            .jsonPath("$.reps").isEqualTo(8)
    }

    @Test
    fun `create workout exercise with advanced technique returns it`() {
        val planId = createPlan(authToken, "PPL")
        val groupId = createGroup(authToken, planId, "Treino A")

        client
            .post()
            .uri("/api/plans/$planId/groups/$groupId/exercises")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "exerciseId" to exerciseId,
                    "sets" to 4,
                    "reps" to 8,
                    "advancedTechnique" to "REST_PAUSE",
                ),
            ).exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.advancedTechnique").isEqualTo("REST_PAUSE")
    }

    @Test
    fun `update and delete workout exercise manage nested exercise`() {
        val planId = createPlan(authToken, "PPL")
        val groupId = createGroup(authToken, planId, "Treino A")
        val workoutExerciseId = createWorkoutExercise(authToken, planId, groupId, exerciseId)

        client
            .patch()
            .uri("/api/plans/$planId/groups/$groupId/exercises/$workoutExerciseId")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("sets" to 5))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(workoutExerciseId.toString())
            .jsonPath("$.sets").isEqualTo(5)

        client
            .delete()
            .uri("/api/plans/$planId/groups/$groupId/exercises/$workoutExerciseId")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isNoContent

        client
            .get()
            .uri("/api/plans/$planId")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.groups[0].exercises.length()").isEqualTo(0)
    }

    @Test
    fun `reorder workout exercise moves item down`() {
        val planId = createPlan(authToken, "PPL")
        val groupId = createGroup(authToken, planId, "Treino A")
        val firstExerciseId = createWorkoutExercise(authToken, planId, groupId, exerciseId, 0)
        val secondCatalogExerciseId = createExercise(authToken, "Incline Bench Press", "CHEST")
        val secondExerciseId = createWorkoutExercise(authToken, planId, groupId, secondCatalogExerciseId, 1)

        client
            .patch()
            .uri("/api/plans/$planId/groups/$groupId/exercises/$firstExerciseId/order")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("direction" to "DOWN"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)
            .jsonPath("$[0].id").isEqualTo(secondExerciseId.toString())
            .jsonPath("$[0].orderIndex").isEqualTo(0)
            .jsonPath("$[1].id").isEqualTo(firstExerciseId.toString())
            .jsonPath("$[1].orderIndex").isEqualTo(1)
    }

    @Test
    fun `reorder workout exercise moves item up`() {
        val planId = createPlan(authToken, "PPL")
        val groupId = createGroup(authToken, planId, "Treino A")
        val firstExerciseId = createWorkoutExercise(authToken, planId, groupId, exerciseId, 0)
        val secondCatalogExerciseId = createExercise(authToken, "Incline Bench Press", "CHEST")
        val secondExerciseId = createWorkoutExercise(authToken, planId, groupId, secondCatalogExerciseId, 1)

        client
            .patch()
            .uri("/api/plans/$planId/groups/$groupId/exercises/$secondExerciseId/order")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("direction" to "UP"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)
            .jsonPath("$[0].id").isEqualTo(secondExerciseId.toString())
            .jsonPath("$[0].orderIndex").isEqualTo(0)
            .jsonPath("$[1].id").isEqualTo(firstExerciseId.toString())
            .jsonPath("$[1].orderIndex").isEqualTo(1)
    }

    @Test
    fun `reorder workout exercise is no-op at top`() {
        val planId = createPlan(authToken, "PPL")
        val groupId = createGroup(authToken, planId, "Treino A")
        val firstExerciseId = createWorkoutExercise(authToken, planId, groupId, exerciseId, 0)
        val secondCatalogExerciseId = createExercise(authToken, "Incline Bench Press", "CHEST")
        val secondExerciseId = createWorkoutExercise(authToken, planId, groupId, secondCatalogExerciseId, 1)

        client
            .patch()
            .uri("/api/plans/$planId/groups/$groupId/exercises/$firstExerciseId/order")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("direction" to "UP"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)
            .jsonPath("$[0].id").isEqualTo(firstExerciseId.toString())
            .jsonPath("$[0].orderIndex").isEqualTo(0)
            .jsonPath("$[1].id").isEqualTo(secondExerciseId.toString())
            .jsonPath("$[1].orderIndex").isEqualTo(1)
    }

    @Test
    fun `reorder workout exercise is no-op at bottom`() {
        val planId = createPlan(authToken, "PPL")
        val groupId = createGroup(authToken, planId, "Treino A")
        val firstExerciseId = createWorkoutExercise(authToken, planId, groupId, exerciseId, 0)
        val secondCatalogExerciseId = createExercise(authToken, "Incline Bench Press", "CHEST")
        val secondExerciseId = createWorkoutExercise(authToken, planId, groupId, secondCatalogExerciseId, 1)

        client
            .patch()
            .uri("/api/plans/$planId/groups/$groupId/exercises/$secondExerciseId/order")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("direction" to "DOWN"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)
            .jsonPath("$[0].id").isEqualTo(firstExerciseId.toString())
            .jsonPath("$[0].orderIndex").isEqualTo(0)
            .jsonPath("$[1].id").isEqualTo(secondExerciseId.toString())
            .jsonPath("$[1].orderIndex").isEqualTo(1)
    }

    @Test
    fun `reorder workout exercise requires plan ownership`() {
        val planId = createPlan(authToken, "PPL")
        val groupId = createGroup(authToken, planId, "Treino A")
        val workoutExerciseId = createWorkoutExercise(authToken, planId, groupId, exerciseId, 0)

        client
            .patch()
            .uri("/api/plans/$planId/groups/$groupId/exercises/$workoutExerciseId/order")
            .header("Authorization", "Bearer $otherUserToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("direction" to "DOWN"))
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `get other user's plan returns forbidden`() {
        val planId = createPlan(authToken, "PPL")

        client
            .get()
            .uri("/api/plans/$planId")
            .header("Authorization", "Bearer $otherUserToken")
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `list plans without authentication returns unauthorized`() {
        client
            .get()
            .uri("/api/plans")
            .exchange()
            .expectStatus().isUnauthorized
    }

    private fun createPlan(
        token: String,
        name: String,
    ): UUID {
        val response =
            client
                .post()
                .uri("/api/plans")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapOf("name" to name))
                .exchange()
                .expectStatus().isCreated
                .expectBody(WorkoutPlanResponse::class.java)
                .returnResult()
                .responseBody!!

        return response.id
    }

    private fun createGroup(
        token: String,
        planId: UUID,
        title: String,
    ): UUID {
        val response =
            client
                .post()
                .uri("/api/plans/$planId/groups")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapOf("title" to title))
                .exchange()
                .expectStatus().isCreated
                .expectBody(WorkoutGroupResponse::class.java)
                .returnResult()
                .responseBody!!

        return response.id
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

    private fun createWorkoutExercise(
        token: String,
        planId: UUID,
        groupId: UUID,
        exerciseId: UUID,
        orderIndex: Int = 0,
    ): UUID {
        val response =
            client
                .post()
                .uri("/api/plans/$planId/groups/$groupId/exercises")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    mapOf(
                        "exerciseId" to exerciseId,
                        "sets" to 4,
                        "reps" to 8,
                        "orderIndex" to orderIndex,
                    ),
                ).exchange()
                .expectStatus().isCreated
                .expectBody(WorkoutExerciseResponse::class.java)
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
