package com.satzwerk.analytics

import com.satzwerk.PostgresTestContainer
import com.satzwerk.auth.AuthResponse
import com.satzwerk.sessions.SetLogResponse
import com.satzwerk.sessions.WorkoutSessionResponse
import com.satzwerk.workouts.ExerciseResponse
import com.satzwerk.workouts.WorkoutGroupResponse
import com.satzwerk.workouts.WorkoutPlanResponse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AnalyticsExerciseProgressIntegrationTest : PostgresTestContainer() {
    @Autowired
    lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var databaseClient: DatabaseClient

    @Test
    fun `exercise progress returns 200 with empty points and recentSessions for exercise with no completed sessions`() {
        val token = registerAndLogin()
        val exerciseId = createExercise(token, "Overhead Press")

        webTestClient.get()
            .uri("/api/analytics/exercises/$exerciseId/progress")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.exerciseId").isEqualTo(exerciseId.toString())
            .jsonPath("$.exerciseName").isEqualTo("Overhead Press")
            .jsonPath("$.points").isEmpty
            .jsonPath("$.recentSessions").isEmpty
    }

    @Test
    fun `exercise progress returns top set, estimated one rep max, and session context`() {
        val token = registerAndLogin()
        val exerciseId = createExercise(token, "Bench Press")
        val groupId = createWorkoutGroup(token, exerciseId, sets = 3, reps = 8)

        createCompletedSession(
            token = token,
            groupId = groupId,
            exerciseId = exerciseId,
            window =
                ProgressSessionWindow(
                    startedAt = Instant.parse("2026-08-01T08:00:00Z"),
                    completedAt = Instant.parse("2026-08-01T09:00:00Z"),
                ),
            logs =
                listOf(
                    setLog(weight = BigDecimal("80.00"), reps = 8, setNumber = 1),
                    setLog(weight = BigDecimal("82.50"), reps = 8, setNumber = 2),
                    setLog(weight = BigDecimal("85.00"), reps = 6, setNumber = 3),
                ),
        )

        webTestClient.get()
            .uri("/api/analytics/exercises/$exerciseId/progress")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.exerciseId").isEqualTo(exerciseId)
            .jsonPath("$.points[0].topSetWeightKg").isEqualTo(85.00)
            .jsonPath("$.points[0].topSetReps").isEqualTo(6)
            .jsonPath("$.points[0].estimatedOneRepMaxKg").isEqualTo(102.00)
            .jsonPath("$.recentSessions[0].sessionDate").isEqualTo("2026-08-01")
            .jsonPath("$.recentSessions[0].topSetLabel").isEqualTo("85 kg × 6")
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun registerAndLogin(): String {
        val suffix = UUID.randomUUID()
        return webTestClient
            .post()
            .uri("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "email" to "progress-$suffix@test.com",
                    "password" to "password123",
                    "displayName" to "Progress User",
                ),
            ).exchange()
            .expectStatus().isCreated
            .expectBody(AuthResponse::class.java)
            .returnResult()
            .responseBody!!
            .accessToken
    }

    private fun createExercise(
        token: String,
        name: String,
    ): UUID =
        webTestClient
            .post()
            .uri("/api/exercises")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to name, "muscleGroup" to "CHEST"))
            .exchange()
            .expectStatus().isCreated
            .expectBody(ExerciseResponse::class.java)
            .returnResult()
            .responseBody!!
            .id

    private fun createWorkoutGroup(
        token: String,
        exerciseId: UUID,
        sets: Int,
        reps: Int,
    ): UUID {
        val planId =
            webTestClient
                .post()
                .uri("/api/plans")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapOf("name" to "Progress Plan"))
                .exchange()
                .expectStatus().isCreated
                .expectBody(WorkoutPlanResponse::class.java)
                .returnResult()
                .responseBody!!
                .id

        webTestClient
            .post()
            .uri("/api/plans/$planId/activate")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isNoContent

        val groupId =
            webTestClient
                .post()
                .uri("/api/plans/$planId/groups")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapOf("title" to "Progress Day"))
                .exchange()
                .expectStatus().isCreated
                .expectBody(WorkoutGroupResponse::class.java)
                .returnResult()
                .responseBody!!
                .id

        webTestClient
            .post()
            .uri("/api/plans/$planId/groups/$groupId/exercises")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "exerciseId" to exerciseId,
                    "sets" to sets,
                    "reps" to reps,
                ),
            ).exchange()
            .expectStatus().isCreated

        return groupId
    }

    private fun createCompletedSession(
        token: String,
        groupId: UUID,
        exerciseId: UUID,
        window: ProgressSessionWindow,
        logs: List<ProgressSetLogFixture>,
    ) {
        val session =
            webTestClient
                .post()
                .uri("/api/sessions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapOf("workoutGroupId" to groupId))
                .exchange()
                .expectStatus().isCreated
                .expectBody(WorkoutSessionResponse::class.java)
                .returnResult()
                .responseBody!!

        for (log in logs) {
            webTestClient
                .post()
                .uri("/api/sessions/${session.id}/set-logs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    mapOf(
                        "exerciseId" to exerciseId,
                        "setNumber" to log.setNumber,
                        "weight" to log.weight,
                        "reps" to log.reps,
                    ),
                ).exchange()
                .expectStatus().isCreated
                .expectBody(SetLogResponse::class.java)
                .returnResult()
                .responseBody!!
        }

        webTestClient
            .post()
            .uri("/api/sessions/${session.id}/complete")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("notes" to ""))
            .exchange()
            .expectStatus().isOk

        databaseClient
            .sql(
                "UPDATE workout_sessions SET started_at = :startedAt, completed_at = :completedAt " +
                    "WHERE id = :id",
            ).bind("startedAt", window.startedAt)
            .bind("completedAt", window.completedAt)
            .bind("id", session.id)
            .fetch().rowsUpdated().block()

        databaseClient
            .sql("UPDATE set_logs SET logged_at = :loggedAt WHERE workout_session_id = :id")
            .bind("loggedAt", window.completedAt)
            .bind("id", session.id)
            .fetch().rowsUpdated().block()
    }

    private fun setLog(
        weight: BigDecimal,
        reps: Int,
        setNumber: Int,
    ): ProgressSetLogFixture = ProgressSetLogFixture(weight = weight, reps = reps, setNumber = setNumber)
}

private data class ProgressSetLogFixture(
    val weight: BigDecimal,
    val reps: Int,
    val setNumber: Int,
)

private data class ProgressSessionWindow(
    val startedAt: Instant,
    val completedAt: Instant,
)
