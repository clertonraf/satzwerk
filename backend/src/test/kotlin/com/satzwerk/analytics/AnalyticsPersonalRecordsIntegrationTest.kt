package com.satzwerk.analytics

import com.satzwerk.auth.AuthResponse
import com.satzwerk.sessions.SetLogResponse
import com.satzwerk.sessions.WorkoutSessionResponse
import com.satzwerk.workouts.ExerciseResponse
import com.satzwerk.workouts.WorkoutGroupResponse
import com.satzwerk.workouts.WorkoutPlanResponse
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
import java.math.BigDecimal
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class AnalyticsPersonalRecordsIntegrationTest {
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
    private lateinit var workoutGroupId: UUID
    private lateinit var exerciseId: UUID

    @BeforeEach
    fun setup() {
        val suffix = UUID.randomUUID()
        authToken = registerAndLogin("pr-$suffix@test.com", "password123", "PR User")
        exerciseId = createExercise(authToken, "Bench Press", "CHEST")
        val planId = createPlan(authToken, "Push Pull Legs")
        workoutGroupId = createGroup(authToken, planId, "Push Day", exerciseId)
    }

    // ── /analytics/personal-records ─────────────────────────────────────────

    @Test
    fun `personal records returns empty list with no PRs`() {
        client
            .get()
            .uri("/api/analytics/personal-records")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(0)
    }

    @Test
    fun `personal records returns 400 for limit=0`() {
        client
            .get()
            .uri("/api/analytics/personal-records?limit=0")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `personal records detects PR on first set for exercise`() {
        val session = startSession(authToken, workoutGroupId)
        addSetLog(authToken, session.id, exerciseId, 1)
        completeSession(authToken, session.id)

        client
            .get()
            .uri("/api/analytics/personal-records?limit=5")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].exerciseId").isEqualTo(exerciseId.toString())
            .jsonPath("$[0].weightKg").isEqualTo(80.0)
    }

    @Test
    fun `personal records is scoped to authenticated user`() {
        val otherToken = registerAndLogin("pr-other-${UUID.randomUUID()}@test.com", "password123", "Other")
        val otherExerciseId = createExercise(otherToken, "Deadlift", "BACK")
        val otherPlanId = createPlan(otherToken, "Plan")
        val otherGroupId = createGroup(otherToken, otherPlanId, "Day", otherExerciseId)

        val otherSession = startSession(otherToken, otherGroupId)
        addSetLog(otherToken, otherSession.id, otherExerciseId, 1)
        completeSession(otherToken, otherSession.id)

        client
            .get()
            .uri("/api/analytics/personal-records")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(0)
    }

    @Test
    fun `personal records endpoint returns reps field`() {
        val session = startSession(authToken, workoutGroupId)
        addSetLog(
            authToken,
            session.id,
            exerciseId,
            setNumber = 1,
            setLog = PrSetLogFixture(weight = BigDecimal("100.0"), reps = 5),
        )
        completeSession(authToken, session.id)

        client
            .get()
            .uri("/api/analytics/personal-records")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].weightKg").isEqualTo(100.0)
            .jsonPath("$[0].reps").isEqualTo(5)
    }

    @Test
    fun `personal records endpoint returns multiple prs in order`() {
        val firstSession = startSession(authToken, workoutGroupId)
        addSetLog(
            authToken,
            firstSession.id,
            exerciseId,
            setNumber = 1,
            setLog = PrSetLogFixture(weight = BigDecimal("100.0"), reps = 5),
        )
        completeSession(authToken, firstSession.id)

        val secondSession = startSession(authToken, workoutGroupId)
        addSetLog(
            authToken,
            secondSession.id,
            exerciseId,
            setNumber = 1,
            setLog = PrSetLogFixture(weight = BigDecimal("60.0"), reps = 2),
        )
        completeSession(authToken, secondSession.id)

        client
            .get()
            .uri("/api/analytics/personal-records")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)
            .jsonPath("$[0].weightKg").isEqualTo(60.0)
            .jsonPath("$[0].reps").isEqualTo(2)
            .jsonPath("$[1].weightKg").isEqualTo(100.0)
            .jsonPath("$[1].reps").isEqualTo(5)
    }

    private fun startSession(
        token: String,
        groupId: UUID,
    ): WorkoutSessionResponse =
        client
            .post()
            .uri("/api/sessions")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("workoutGroupId" to groupId))
            .exchange()
            .expectStatus().isCreated
            .expectBody(WorkoutSessionResponse::class.java)
            .returnResult()
            .responseBody!!

    private fun addSetLog(
        token: String,
        sessionId: UUID,
        exerciseId: UUID,
        setNumber: Int,
        setLog: PrSetLogFixture = PrSetLogFixture(),
    ): SetLogResponse =
        client
            .post()
            .uri("/api/sessions/$sessionId/set-logs")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "exerciseId" to exerciseId,
                    "setNumber" to setNumber,
                    "weight" to setLog.weight,
                    "reps" to setLog.reps,
                ),
            ).exchange()
            .expectStatus().isCreated
            .expectBody(SetLogResponse::class.java)
            .returnResult()
            .responseBody!!

    private fun completeSession(
        token: String,
        sessionId: UUID,
    ): WorkoutSessionResponse =
        client
            .post()
            .uri("/api/sessions/$sessionId/complete")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("notes" to "Complete"))
            .exchange()
            .expectStatus().isOk
            .expectBody(WorkoutSessionResponse::class.java)
            .returnResult()
            .responseBody!!

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

        val planId = response.id
        client
            .post()
            .uri("/api/plans/$planId/activate")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isNoContent
        return planId
    }

    private fun createGroup(
        token: String,
        planId: UUID,
        title: String,
        exerciseId: UUID,
    ): UUID {
        val groupResponse =
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

        val groupId = groupResponse.id

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
                ),
            ).exchange()
            .expectStatus().isCreated

        return groupId
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
}

private data class PrSetLogFixture(
    val weight: BigDecimal = BigDecimal("80.0"),
    val reps: Int = 5,
)
