package com.satzwerk.analytics

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
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class AnalyticsIntegrationTest {
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

    @Autowired
    lateinit var databaseClient: DatabaseClient

    private lateinit var authToken: String
    private lateinit var workoutGroupId: UUID
    private lateinit var exerciseId: UUID

    @BeforeEach
    fun setup() {
        val suffix = UUID.randomUUID()
        authToken = registerAndLogin("analytics-$suffix@test.com", "password123", "Analytics User")
        exerciseId = createExercise(authToken, "Bench Press", "CHEST")
        val planId = createPlan(authToken, "Push Pull Legs")
        workoutGroupId = createGroup(authToken, planId, "Push Day", exerciseId)
    }

    @Test
    fun `heatmap returns zero filled range with no data`() {
        client
            .get()
            .uri("/api/analytics/heatmap?from=2026-01-01&to=2026-01-07")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(7)
            .jsonPath("$[0].date").isEqualTo("2026-01-01")
            .jsonPath("$[0].count").isEqualTo(0)
            .jsonPath("$[0].intensity").isEqualTo(0)
            .jsonPath("$[6].date").isEqualTo("2026-01-07")
            .jsonPath("$[6].count").isEqualTo(0)
            .jsonPath("$[6].intensity").isEqualTo(0)
    }

    @Test
    fun `heatmap counts sets correctly`() {
        val session = startSession(authToken, workoutGroupId)
        repeat(3) { index ->
            addSetLog(authToken, session.id, exerciseId, index + 1)
        }
        completeSession(authToken, session.id)

        val today = LocalDate.now(ZoneOffset.UTC)

        client
            .get()
            .uri("/api/analytics/heatmap?from=$today&to=$today")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].date").isEqualTo(today.toString())
            .jsonPath("$[0].count").isEqualTo(3)
            .jsonPath("$[0].intensity").isEqualTo(1)
    }

    @Test
    fun `heatmap reaches intensity tier four at fifteen sets`() {
        val session = startSession(authToken, workoutGroupId)
        repeat(15) { index ->
            addSetLog(authToken, session.id, exerciseId, index + 1)
        }
        completeSession(authToken, session.id)

        val today = LocalDate.now(ZoneOffset.UTC)

        client
            .get()
            .uri("/api/analytics/heatmap?from=$today&to=$today")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].count").isEqualTo(15)
            .jsonPath("$[0].intensity").isEqualTo(4)
    }

    @Test
    fun `heatmap reaches intensity tier ten at thirty-seven sets`() {
        val fixedDate = LocalDate.of(2025, 6, 1)
        logSetsOnDate(authToken, workoutGroupId, exerciseId, fixedDate, 37)

        client
            .get()
            .uri("/api/analytics/heatmap?from=$fixedDate&to=$fixedDate")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].count").isEqualTo(37)
            .jsonPath("$[0].intensity").isEqualTo(10)
    }

    @Test
    fun `heatmap is scoped to authenticated user`() {
        val otherToken = registerAndLogin("other-${UUID.randomUUID()}@test.com", "password123", "Other User")
        val otherExerciseId = createExercise(otherToken, "Squat", "LEGS")
        val otherPlanId = createPlan(otherToken, "Other Plan")
        val otherWorkoutGroupId = createGroup(otherToken, otherPlanId, "Leg Day", otherExerciseId)

        val ownSession = startSession(authToken, workoutGroupId)
        repeat(2) { index ->
            addSetLog(authToken, ownSession.id, exerciseId, index + 1)
        }
        completeSession(authToken, ownSession.id)

        val otherSession = startSession(otherToken, otherWorkoutGroupId)
        repeat(4) { index ->
            addSetLog(otherToken, otherSession.id, otherExerciseId, index + 1)
        }
        completeSession(otherToken, otherSession.id)

        val today = LocalDate.now(ZoneOffset.UTC)

        client
            .get()
            .uri("/api/analytics/heatmap?from=$today&to=$today")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].count").isEqualTo(2)
            .jsonPath("$[0].intensity").isEqualTo(1)
    }

    @Test
    fun `streak returns zeroes with no activity`() {
        client
            .get()
            .uri("/api/analytics/streak")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.currentStreak").isEqualTo(0)
            .jsonPath("$.longestStreak").isEqualTo(0)
    }

    @Test
    fun `streak counts consecutive days ending today`() {
        val today = LocalDate.now(ZoneOffset.UTC)
        logSetsOnDate(authToken, workoutGroupId, exerciseId, today, 1)
        logSetsOnDate(authToken, workoutGroupId, exerciseId, today.minusDays(1), 1)

        client
            .get()
            .uri("/api/analytics/streak")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.currentStreak").isEqualTo(2)
            .jsonPath("$.longestStreak").isEqualTo(2)
    }

    @Test
    fun `streak is broken when there is a gap`() {
        val today = LocalDate.now(ZoneOffset.UTC)
        logSetsOnDate(authToken, workoutGroupId, exerciseId, today.minusDays(3), 1)
        logSetsOnDate(authToken, workoutGroupId, exerciseId, today.minusDays(5), 1)

        client
            .get()
            .uri("/api/analytics/streak")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.currentStreak").isEqualTo(0)
            .jsonPath("$.longestStreak").isEqualTo(1)
    }

    @Test
    fun `heatmap defaults date range when query params are omitted`() {
        val entries =
            client
                .get()
                .uri("/api/analytics/heatmap")
                .header("Authorization", "Bearer $authToken")
                .exchange()
                .expectStatus().isOk
                .expectBodyList(HeatmapEntry::class.java)
                .returnResult()
                .responseBody!!

        // Derive `to` from the response so the test is not sensitive to UTC midnight
        val toDate = entries.last().date
        val expectedDays = toDate.minusMonths(3).datesUntil(toDate.plusDays(1)).count().toInt()
        assertEquals(expectedDays, entries.size)
    }

    @Test
    fun `heatmap requires authentication`() {
        client
            .get()
            .uri("/api/analytics/heatmap")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `heatmap returns 400 for invalid from date`() {
        client
            .get()
            .uri("/api/analytics/heatmap?from=2026-99-99")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `heatmap returns 400 for invalid to date`() {
        client
            .get()
            .uri("/api/analytics/heatmap?to=not-a-date")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isBadRequest
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
        client
            .get()
            .uri("/api/analytics/summary")
            .header("Authorization", "Bearer $authToken")
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
    fun `summary requires authentication`() {
        client
            .get()
            .uri("/api/analytics/summary")
            .exchange()
            .expectStatus().isUnauthorized
    }

    // ── /analytics/weekly-trend ─────────────────────────────────────────────

    @Test
    fun `weekly trend returns requested number of weeks`() {
        client
            .get()
            .uri("/api/analytics/weekly-trend?weeks=4")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(4)
    }

    @Test
    fun `weekly trend returns 400 for weeks=0`() {
        client
            .get()
            .uri("/api/analytics/weekly-trend?weeks=0")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `weekly trend returns 400 for weeks exceeding max`() {
        client
            .get()
            .uri("/api/analytics/weekly-trend?weeks=53")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `weekly trend includes set count for current week`() {
        val session = startSession(authToken, workoutGroupId)
        repeat(5) { index -> addSetLog(authToken, session.id, exerciseId, index + 1) }
        completeSession(authToken, session.id)
        alignSessionToNow(session.id)

        val entries =
            client
                .get()
                .uri("/api/analytics/weekly-trend?weeks=1")
                .header("Authorization", "Bearer $authToken")
                .exchange()
                .expectStatus().isOk
                .expectBodyList(WeeklyTrendEntry::class.java)
                .returnResult()
                .responseBody!!

        assertEquals(5, entries.last().setCount)
        assertEquals(1, entries.last().sessionCount)
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
                    "weight" to BigDecimal("80.0"),
                    "reps" to 5,
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

    private fun logSetsOnDate(
        token: String,
        groupId: UUID,
        exerciseId: UUID,
        date: LocalDate,
        count: Int,
    ) {
        val session = startSession(token, groupId)
        repeat(count) { index ->
            addSetLog(token, session.id, exerciseId, index + 1)
        }

        databaseClient
            .sql("UPDATE set_logs SET logged_at = :loggedAt WHERE workout_session_id = :sessionId")
            .bind("loggedAt", date.atTime(12, 0).toInstant(ZoneOffset.UTC))
            .bind("sessionId", session.id)
            .fetch()
            .rowsUpdated()
            .block()

        completeSession(token, session.id)
    }

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

        return response.id
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
