package com.satzwerk.analytics

import com.satzwerk.PostgresTestContainer
import com.satzwerk.auth.AuthResponse
import com.satzwerk.sessions.SetLogResponse
import com.satzwerk.sessions.WorkoutSessionResponse
import com.satzwerk.workouts.ExerciseResponse
import com.satzwerk.workouts.WorkoutGroupResponse
import com.satzwerk.workouts.WorkoutPlanResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.math.BigDecimal
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AnalyticsSummaryIntegrationTest : PostgresTestContainer() {
    @Autowired
    lateinit var client: WebTestClient

    @Autowired
    lateinit var databaseClient: DatabaseClient

    private lateinit var authToken: String
    private lateinit var workoutGroupId: UUID
    private lateinit var exerciseId: UUID

    @BeforeEach
    fun setup() {
        val suffix = UUID.randomUUID()
        authToken = registerAndLogin("summary-$suffix@test.com", "password123", "Summary User")
        exerciseId = createExercise(authToken, "Bench Press", "CHEST")
        val planId = createPlan(authToken, "Push Pull Legs")
        workoutGroupId = createGroup(authToken, planId, "Push Day", exerciseId)
    }

    // ── /analytics/summary ──────────────────────────────────────────────────

    @Test
    fun `summary returns zeroes with no activity`() {
        client
            .get()
            .uri("/api/analytics/summary")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.totalSessions").isEqualTo(0)
            .jsonPath("$.sessionsThisMonth").isEqualTo(0)
            .jsonPath("$.setsThisWeek").isEqualTo(0)
            .jsonPath("$.prsThisMonth").isEqualTo(0)
            .jsonPath("$.currentStreak").isEqualTo(0)
            .jsonPath("$.longestStreak").isEqualTo(0)
    }

    @Test
    fun `summary activePlanDays is null when no active plan`() {
        val suffix = UUID.randomUUID()
        val noActivePlanToken = registerAndLogin("no-active-$suffix@test.com", "password123", "No Plan User")
        client
            .get()
            .uri("/api/analytics/summary")
            .header("Authorization", "Bearer $noActivePlanToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.activePlanDays").value<Any?> { assertEquals(null, it) }
    }

    @Test
    fun `summary counts completed sessions and sets`() {
        val session = startSession(authToken, workoutGroupId)
        repeat(3) { index -> addSetLog(authToken, session.id, exerciseId, index + 1) }
        completeSession(authToken, session.id)
        alignSessionToNow(session.id)

        client
            .get()
            .uri("/api/analytics/summary")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.totalSessions").isEqualTo(1)
            .jsonPath("$.setsThisWeek").isEqualTo(3)
    }

    @Test
    fun `summary avgSessionDurationMinutes is null when no completed sessions`() {
        val suffix = UUID.randomUUID()
        val noSessionToken = registerAndLogin("no-session-$suffix@test.com", "password123", "No Session User")
        client
            .get()
            .uri("/api/analytics/summary")
            .header("Authorization", "Bearer $noSessionToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.avgSessionDurationMinutes").value<Any?> { assertEquals(null, it) }
    }

    @Test
    fun `summary avgSessionDurationMinutes computes average over completed sessions`() {
        val session = startSession(authToken, workoutGroupId)
        addSetLog(authToken, session.id, exerciseId, 1)
        completeSession(authToken, session.id)
        databaseClient
            .sql(
                "UPDATE workout_sessions SET started_at = NOW() - INTERVAL '50 minutes', " +
                    "completed_at = NOW() WHERE id = :id",
            ).bind("id", session.id)
            .fetch().rowsUpdated().block()

        client
            .get()
            .uri("/api/analytics/summary")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.avgSessionDurationMinutes").isEqualTo(50)
    }

    @Test
    fun `summary requires authentication`() {
        client
            .get()
            .uri("/api/analytics/summary")
            .exchange()
            .expectStatus().isUnauthorized
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
        setLog: SummarySetLogFixture = SummarySetLogFixture(),
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

    private fun alignSessionToNow(sessionId: UUID) {
        databaseClient
            .sql("UPDATE set_logs SET logged_at = NOW() WHERE workout_session_id = :sessionId")
            .bind("sessionId", sessionId)
            .fetch().rowsUpdated().block()
        databaseClient
            .sql("UPDATE workout_sessions SET completed_at = NOW(), started_at = NOW() WHERE id = :sessionId")
            .bind("sessionId", sessionId)
            .fetch().rowsUpdated().block()
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

private data class SummarySetLogFixture(
    val weight: BigDecimal = BigDecimal("80.0"),
    val reps: Int = 5,
)
