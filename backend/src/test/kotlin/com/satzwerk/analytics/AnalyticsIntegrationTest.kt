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
        client
            .get()
            .uri("/api/analytics/heatmap")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(365)
    }

    @Test
    fun `heatmap requires authentication`() {
        client
            .get()
            .uri("/api/analytics/heatmap")
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
