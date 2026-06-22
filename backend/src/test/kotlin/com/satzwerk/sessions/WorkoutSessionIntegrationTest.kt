package com.satzwerk.sessions

import com.satzwerk.auth.AuthResponse
import com.satzwerk.workouts.ExerciseResponse
import com.satzwerk.workouts.WorkoutGroupResponse
import com.satzwerk.workouts.WorkoutPlanResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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

@Suppress("LargeClass")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class WorkoutSessionIntegrationTest {
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
        authToken = registerAndLogin("session-$suffix@test.com", "password123", "Session User")
        exerciseId = createExercise(authToken, "Bench Press", "CHEST")
        val planId = createPlan(authToken, "Push Pull Legs")
        activatePlan(authToken, planId)
        workoutGroupId = createGroup(authToken, planId, "Push Day", exerciseId)
    }

    @Test
    fun `start session returns created open session`() {
        client
            .post()
            .uri("/api/sessions")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("workoutGroupId" to workoutGroupId))
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.id").isNotEmpty
            .jsonPath("$.workoutGroupId").isEqualTo(workoutGroupId.toString())
            .jsonPath("$.startedAt").isNotEmpty
            .jsonPath("$.completedAt").isEmpty
            .jsonPath("$.setLogs.length()").isEqualTo(0)
    }

    @Test
    fun `get start options returns active plan detail`() {
        client
            .get()
            .uri("/api/sessions/start-options")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isNotEmpty
            .jsonPath("$.isActive").isEqualTo(true)
            .jsonPath("$.groups.length()").isEqualTo(1)
            .jsonPath("$.groups[0].id").isEqualTo(workoutGroupId.toString())
            .jsonPath("$.groups[0].title").isEqualTo("Push Day")
    }

    @Test
    fun `get start options returns not found when no plan is active`() {
        val suffix = UUID.randomUUID()
        val token = registerAndLogin("inactive-$suffix@test.com", "password123", "Inactive User")
        createPlan(token, "Inactive Plan")

        client
            .get()
            .uri("/api/sessions/start-options")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `start session returns bad request when group belongs to inactive plan`() {
        val suffix = UUID.randomUUID()
        val token = registerAndLogin("inactive-group-$suffix@test.com", "password123", "Inactive Group User")
        val ownedExerciseId = createExercise(token, "Overhead Press", "SHOULDERS")
        val inactivePlanId = createPlan(token, "Inactive Push")
        val inactiveGroupId = createGroup(token, inactivePlanId, "Push Day", ownedExerciseId)

        client
            .post()
            .uri("/api/sessions")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("workoutGroupId" to inactiveGroupId))
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `start session rejects second open session`() {
        startSession()

        client
            .post()
            .uri("/api/sessions")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("workoutGroupId" to workoutGroupId))
            .exchange()
            .expectStatus().isEqualTo(409)
    }

    @Test
    fun `get open session returns current session`() {
        val session = startSession()

        client
            .get()
            .uri("/api/sessions/open")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(session.id.toString())
            .jsonPath("$.workoutGroupId").isEqualTo(workoutGroupId.toString())
            .jsonPath("$.completedAt").isEmpty
    }

    @Test
    fun `get open session without active session returns not found`() {
        client
            .get()
            .uri("/api/sessions/open")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `add set log returns created set log`() {
        val session = startSession()

        client
            .post()
            .uri("/api/sessions/${session.id}/set-logs")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "exerciseId" to exerciseId,
                    "setNumber" to 1,
                    "weight" to BigDecimal("80.0"),
                    "reps" to 5,
                ),
            ).exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.id").isNotEmpty
            .jsonPath("$.exerciseId").isEqualTo(exerciseId.toString())
            .jsonPath("$.setNumber").isEqualTo(1)
            .jsonPath("$.weight").isEqualTo(80)
            .jsonPath("$.reps").isEqualTo(5)
    }

    @Test
    fun `set log weight is stored in kilograms as provided`() {
        val session = startSession()
        addSetLog(session.id, BigDecimal("100.0"))

        val openSession =
            client
                .get()
                .uri("/api/sessions/open")
                .header("Authorization", "Bearer $authToken")
                .exchange()
                .expectStatus().isOk
                .expectBody(WorkoutSessionResponse::class.java)
                .returnResult()
                .responseBody!!

        assertEquals(BigDecimal("100.00"), openSession.setLogs.single().weight)
    }

    @Test
    fun `complete session marks it completed and stores notes`() {
        val session = startSession()

        val completedSession =
            client
                .post()
                .uri("/api/sessions/${session.id}/complete")
                .header("Authorization", "Bearer $authToken")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapOf("notes" to "Great session"))
                .exchange()
                .expectStatus().isOk
                .expectBody(WorkoutSessionResponse::class.java)
                .returnResult()
                .responseBody!!

        assertEquals(session.id, completedSession.id)
        assertEquals("Great session", completedSession.notes)
        assertNotNull(completedSession.completedAt)
    }

    @Test
    fun `cannot add set log to completed session`() {
        val session = startSession()
        completeSession(session.id)

        client
            .post()
            .uri("/api/sessions/${session.id}/set-logs")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "exerciseId" to exerciseId,
                    "setNumber" to 1,
                    "weight" to BigDecimal("80.0"),
                    "reps" to 5,
                ),
            ).exchange()
            .expectStatus().isEqualTo(409)
    }

    @Test
    fun `discard session removes open session`() {
        val session = startSession()
        addSetLog(session.id, BigDecimal("80.0"))

        client
            .delete()
            .uri("/api/sessions/${session.id}")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isNoContent

        client
            .get()
            .uri("/api/sessions/open")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `history returns completed sessions`() {
        val session = startSession()
        val completedSession = completeSession(session.id)

        client
            .get()
            .uri("/api/sessions/history")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].id").isEqualTo(completedSession.id.toString())
            .jsonPath("$[0].workoutGroupId").isEqualTo(workoutGroupId.toString())
            .jsonPath("$[0].completedAt").isNotEmpty
    }

    @Test
    fun `get session by id returns session with set logs`() {
        val session = startSession()
        addSetLog(session.id, BigDecimal("100.0"))
        val completedSession = completeSession(session.id)

        client
            .get()
            .uri("/api/sessions/${completedSession.id}")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(completedSession.id.toString())
            .jsonPath("$.workoutGroupId").isEqualTo(workoutGroupId.toString())
            .jsonPath("$.completedAt").isNotEmpty
            .jsonPath("$.notes").isEqualTo("Great session")
            .jsonPath("$.setLogs.length()").isEqualTo(1)
            .jsonPath("$.setLogs[0].exerciseId").isEqualTo(exerciseId.toString())
            .jsonPath("$.setLogs[0].weight").isEqualTo(100)
            .jsonPath("$.setLogs[0].reps").isEqualTo(5)
    }

    @Test
    fun `get session by id for another user returns not found`() {
        val session = startSession()
        completeSession(session.id)

        val otherToken = registerAndLogin("other-${UUID.randomUUID()}@test.com", "password123", "Other User")

        client
            .get()
            .uri("/api/sessions/${session.id}")
            .header("Authorization", "Bearer $otherToken")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `update set log returns updated set log`() {
        val session = startSession()
        val setLog = addSetLog(session.id, BigDecimal("80.0"))

        client
            .patch()
            .uri("/api/sessions/${session.id}/set-logs/${setLog.id}")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "weight" to BigDecimal("90.0"),
                    "reps" to 8,
                ),
            ).exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(setLog.id.toString())
            .jsonPath("$.weight").isEqualTo(90)
            .jsonPath("$.reps").isEqualTo(8)
            .jsonPath("$.setNumber").isEqualTo(1)
    }

    @Test
    fun `cannot update set log on completed session`() {
        val session = startSession()
        val setLog = addSetLog(session.id, BigDecimal("80.0"))
        completeSession(session.id)

        client
            .patch()
            .uri("/api/sessions/${session.id}/set-logs/${setLog.id}")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "weight" to BigDecimal("90.0"),
                    "reps" to 8,
                ),
            ).exchange()
            .expectStatus().isEqualTo(409)
    }

    @Test
    fun `update set log returns not found for nonexistent set log`() {
        val session = startSession()
        addSetLog(session.id, BigDecimal("80.0"))

        client
            .patch()
            .uri("/api/sessions/${session.id}/set-logs/${UUID.randomUUID()}")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "weight" to BigDecimal("90.0"),
                    "reps" to 8,
                ),
            ).exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `start session without authentication returns unauthorized`() {
        client
            .post()
            .uri("/api/sessions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("workoutGroupId" to workoutGroupId))
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `set log with zero reps is rejected with bad request`() {
        val session = startSession()

        client
            .post()
            .uri("/api/sessions/${session.id}/set-logs")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "exerciseId" to exerciseId,
                    "setNumber" to 1,
                    "weight" to BigDecimal("100.0"),
                    "reps" to 0,
                ),
            ).exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `set log with higher weight to reps ratio is marked as personal record even when absolute weight is lower`() {
        val firstSession = startSession()
        addSetLog(firstSession.id, BigDecimal("100.0"), reps = 10)
        completeSession(firstSession.id)

        val secondSession = startSession()
        addSetLog(secondSession.id, BigDecimal("60.0"), reps = 3)
        completeSession(secondSession.id)

        client
            .get()
            .uri("/api/analytics/personal-records")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)
            .jsonPath("$[0].weightKg").isEqualTo(60.0)
            .jsonPath("$[0].reps").isEqualTo(3)
    }

    @Test
    fun `set log with lower weight to reps ratio than existing pr is not marked as personal record`() {
        val firstSession = startSession()
        addSetLog(firstSession.id, BigDecimal("100.0"), reps = 5)
        completeSession(firstSession.id)

        val secondSession = startSession()
        addSetLog(secondSession.id, BigDecimal("80.0"), reps = 5)
        completeSession(secondSession.id)

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
    fun `update set log recalculates isPr when weight ratio is updated above existing records`() {
        val firstSession = startSession()
        addSetLog(firstSession.id, BigDecimal("100.0"), reps = 5)
        completeSession(firstSession.id)

        val secondSession = startSession()
        val setLog = addSetLog(secondSession.id, BigDecimal("80.0"), reps = 5)
        updateSetLog(secondSession.id, setLog.id, BigDecimal("120.0"), reps = 5)
        completeSession(secondSession.id)

        client
            .get()
            .uri("/api/analytics/personal-records")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)
            .jsonPath("$[0].weightKg").isEqualTo(120.0)
            .jsonPath("$[0].reps").isEqualTo(5)
    }

    @Test
    fun `update set log recalculates isPr when weight ratio is updated below existing records`() {
        val firstSession = startSession()
        addSetLog(firstSession.id, BigDecimal("100.0"), reps = 5)
        completeSession(firstSession.id)

        val secondSession = startSession()
        val setLog = addSetLog(secondSession.id, BigDecimal("120.0"), reps = 5)
        updateSetLog(secondSession.id, setLog.id, BigDecimal("80.0"), reps = 5)
        completeSession(secondSession.id)

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
    fun `reference weights returns null values when exercise has no history`() {
        val session = startSession()

        client
            .get()
            .uri("/api/sessions/${session.id}/reference-weights")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].exerciseId").isEqualTo(exerciseId.toString())
            .jsonPath("$[0].previousWeightKg").isEmpty
            .jsonPath("$[0].prWeightKg").isEmpty
            .jsonPath("$[0].estimatedOneRepMaxKg").isEmpty
            .jsonPath("$[0].suggestedWeightKg").isEmpty
    }

    @Test
    fun `reference weights returns previous weight from most recent completed session`() {
        val completedSession = startSession()
        addSetLog(completedSession.id, BigDecimal("85.0"))
        completeSession(completedSession.id)

        val currentSession = startSession()

        client
            .get()
            .uri("/api/sessions/${currentSession.id}/reference-weights")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].exerciseId").isEqualTo(exerciseId.toString())
            .jsonPath("$[0].previousWeightKg").isEqualTo(85)
    }

    @Test
    fun `reference weights excludes current open session from previous weight`() {
        val completedSession = startSession()
        addSetLog(completedSession.id, BigDecimal("90.0"))
        completeSession(completedSession.id)

        val currentSession = startSession()
        addSetLog(currentSession.id, BigDecimal("120.0"))

        client
            .get()
            .uri("/api/sessions/${currentSession.id}/reference-weights")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].exerciseId").isEqualTo(exerciseId.toString())
            .jsonPath("$[0].previousWeightKg").isEqualTo(90)
            .jsonPath("$[0].prWeightKg").isEqualTo(120)
    }

    @Test
    fun `reference weights returns pr as max weight across all sessions`() {
        val firstCompletedSession = startSession()
        addSetLog(firstCompletedSession.id, BigDecimal("90.0"))
        completeSession(firstCompletedSession.id)

        val secondCompletedSession = startSession()
        addSetLog(secondCompletedSession.id, BigDecimal("110.0"))
        completeSession(secondCompletedSession.id)

        val currentSession = startSession()
        addSetLog(currentSession.id, BigDecimal("105.0"))

        client
            .get()
            .uri("/api/sessions/${currentSession.id}/reference-weights")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].exerciseId").isEqualTo(exerciseId.toString())
            .jsonPath("$[0].prWeightKg").isEqualTo(110)
    }

    @Test
    fun `reference weights updates pr from current open session`() {
        val completedSession = startSession()
        addSetLog(completedSession.id, BigDecimal("100.0"))
        completeSession(completedSession.id)

        val currentSession = startSession()
        addSetLog(currentSession.id, BigDecimal("120.0"))

        client
            .get()
            .uri("/api/sessions/${currentSession.id}/reference-weights")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].exerciseId").isEqualTo(exerciseId.toString())
            .jsonPath("$[0].previousWeightKg").isEqualTo(100)
            .jsonPath("$[0].prWeightKg").isEqualTo(120)
    }

    @Test
    fun `reference weights calculates estimated one rep max using epley formula`() {
        val session = startSession()
        addSetLog(session.id, BigDecimal("100.0"), reps = 5)

        client
            .get()
            .uri("/api/sessions/${session.id}/reference-weights")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].exerciseId").isEqualTo(exerciseId.toString())
            .jsonPath("$[0].prWeightKg").isEqualTo(100)
            .jsonPath("$[0].estimatedOneRepMaxKg").isEqualTo(116.67)
    }

    @Test
    fun `reference weights uses most recent completed session for previous weight instead of max`() {
        val firstCompletedSession = startSession()
        addSetLog(firstCompletedSession.id, BigDecimal("100.0"))
        completeSession(firstCompletedSession.id)

        val secondCompletedSession = startSession()
        addSetLog(secondCompletedSession.id, BigDecimal("80.0"))
        completeSession(secondCompletedSession.id)

        val currentSession = startSession()

        client
            .get()
            .uri("/api/sessions/${currentSession.id}/reference-weights")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].exerciseId").isEqualTo(exerciseId.toString())
            .jsonPath("$[0].previousWeightKg").isEqualTo(80)
            .jsonPath("$[0].prWeightKg").isEqualTo(100)
    }

    @Test
    fun `reference weights epley formula is accurate for single rep set`() {
        // 300kg x 1 rep: expected = 300 * (1 + 1/30) = 310.00 exactly
        val session = startSession()
        addSetLog(session.id, BigDecimal("300.0"), reps = 1)

        client
            .get()
            .uri("/api/sessions/${session.id}/reference-weights")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].estimatedOneRepMaxKg").isEqualTo(310.00)
    }

    @Test
    fun `reference weights without authentication returns unauthorized`() {
        val session = startSession()

        client
            .get()
            .uri("/api/sessions/${session.id}/reference-weights")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `reference weights for another user session returns not found`() {
        val session = startSession()
        val otherToken = registerAndLogin("other-${UUID.randomUUID()}@test.com", "password123", "Other User")

        client
            .get()
            .uri("/api/sessions/${session.id}/reference-weights")
            .header("Authorization", "Bearer $otherToken")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `reference weights calculates suggested weight using Epley inverse for no technique`() {
        // createGroup uses reps=8, no advancedTechnique
        // 100kg x 5 reps -> 1RM = 116.67 -> suggested for 8 reps = 92.11
        val completedSession = startSession()
        addSetLog(completedSession.id, BigDecimal("100.0"), reps = 5)
        completeSession(completedSession.id)

        val currentSession = startSession()

        client
            .get()
            .uri("/api/sessions/${currentSession.id}/reference-weights")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].suggestedWeightKg").isEqualTo(92.11)
    }

    @Test
    fun `reference weights calculates suggested weight at 55 percent for GIRONDA technique`() {
        // 100kg x 5 reps -> 1RM = 116.67 -> Gironda 55% = 64.17
        val suffix = UUID.randomUUID()
        val token = registerAndLogin("gironda-$suffix@test.com", "password123", "Gironda User")
        val eid = createExercise(token, "Cable Crossover", "CHEST")
        val planId = createPlan(token, "Gironda Plan")
        activatePlan(token, planId)
        val gid = createGroupWithTechnique(token, planId, eid, reps = 8, technique = "GIRONDA")

        val completedSession = startSessionFor(token, gid)
        addSetLogFor(token, completedSession.id, eid, BigDecimal("100.0"), reps = 5)
        completeSessionFor(token, completedSession.id)

        val currentSession = startSessionFor(token, gid)

        client
            .get()
            .uri("/api/sessions/${currentSession.id}/reference-weights")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].suggestedWeightKg").isEqualTo(64.17)
    }

    @Test
    fun `delete set log returns no content and removes it from session`() {
        val session = startSession()
        val setLog = addSetLog(session.id, BigDecimal("80.0"))

        client
            .delete()
            .uri("/api/sessions/${session.id}/set-logs/${setLog.id}")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isNoContent

        client
            .get()
            .uri("/api/sessions/open")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.setLogs.length()").isEqualTo(0)
            .jsonPath("$.setCount").isEqualTo(0)
    }

    @Test
    fun `delete set log returns not found for nonexistent set log`() {
        val session = startSession()

        client
            .delete()
            .uri("/api/sessions/${session.id}/set-logs/${UUID.randomUUID()}")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `cannot delete set log from completed session`() {
        val session = startSession()
        val setLog = addSetLog(session.id, BigDecimal("80.0"))
        completeSession(session.id)

        client
            .delete()
            .uri("/api/sessions/${session.id}/set-logs/${setLog.id}")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isEqualTo(409)
    }

    @Test
    fun `delete set log returns not found when session belongs to different user`() {
        val session = startSession()
        val setLog = addSetLog(session.id, BigDecimal("80.0"))
        val otherToken = registerAndLogin("other-delete-${UUID.randomUUID()}@test.com", "password123", "Other User")

        client
            .delete()
            .uri("/api/sessions/${session.id}/set-logs/${setLog.id}")
            .header("Authorization", "Bearer $otherToken")
            .exchange()
            .expectStatus().isNotFound
    }

    private fun createGroupWithTechnique(
        token: String,
        planId: UUID,
        exerciseId: UUID,
        reps: Int,
        technique: String,
    ): UUID {
        val groupResponse =
            client
                .post()
                .uri("/api/plans/$planId/groups")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapOf("title" to "Training Day"))
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
                    "reps" to reps,
                    "advancedTechnique" to technique,
                ),
            ).exchange()
            .expectStatus().isCreated

        return groupId
    }

    private fun startSessionFor(
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

    private fun addSetLogFor(
        token: String,
        sessionId: UUID,
        exerciseId: UUID,
        weight: BigDecimal,
        reps: Int,
    ): SetLogResponse =
        client
            .post()
            .uri("/api/sessions/$sessionId/set-logs")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "exerciseId" to exerciseId,
                    "setNumber" to 1,
                    "weight" to weight,
                    "reps" to reps,
                ),
            ).exchange()
            .expectStatus().isCreated
            .expectBody(SetLogResponse::class.java)
            .returnResult()
            .responseBody!!

    private fun completeSessionFor(
        token: String,
        sessionId: UUID,
    ): WorkoutSessionResponse =
        client
            .post()
            .uri("/api/sessions/$sessionId/complete")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("notes" to null))
            .exchange()
            .expectStatus().isOk
            .expectBody(WorkoutSessionResponse::class.java)
            .returnResult()
            .responseBody!!

    private fun startSession(): WorkoutSessionResponse =
        client
            .post()
            .uri("/api/sessions")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("workoutGroupId" to workoutGroupId))
            .exchange()
            .expectStatus().isCreated
            .expectBody(WorkoutSessionResponse::class.java)
            .returnResult()
            .responseBody!!

    private fun addSetLog(
        sessionId: UUID,
        weight: BigDecimal,
    ): SetLogResponse = addSetLog(sessionId, weight, reps = 5)

    private fun addSetLog(
        sessionId: UUID,
        weight: BigDecimal,
        reps: Int,
        setNumber: Int = 1,
    ): SetLogResponse =
        client
            .post()
            .uri("/api/sessions/$sessionId/set-logs")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "exerciseId" to exerciseId,
                    "setNumber" to setNumber,
                    "weight" to weight,
                    "reps" to reps,
                ),
            ).exchange()
            .expectStatus().isCreated
            .expectBody(SetLogResponse::class.java)
            .returnResult()
            .responseBody!!

    private fun updateSetLog(
        sessionId: UUID,
        setLogId: UUID,
        weight: BigDecimal,
        reps: Int,
    ): SetLogResponse =
        client
            .patch()
            .uri("/api/sessions/$sessionId/set-logs/$setLogId")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "weight" to weight,
                    "reps" to reps,
                ),
            ).exchange()
            .expectStatus().isOk
            .expectBody(SetLogResponse::class.java)
            .returnResult()
            .responseBody!!

    private fun completeSession(sessionId: UUID): WorkoutSessionResponse =
        client
            .post()
            .uri("/api/sessions/$sessionId/complete")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("notes" to "Great session"))
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

        return response.id
    }

    private fun activatePlan(
        token: String,
        planId: UUID,
    ) {
        client
            .post()
            .uri("/api/plans/$planId/activate")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isNoContent
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
