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
    fun `update set log returns not found for wrong session`() {
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
    ): SetLogResponse =
        client
            .post()
            .uri("/api/sessions/$sessionId/set-logs")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "exerciseId" to exerciseId,
                    "setNumber" to 1,
                    "weight" to weight,
                    "reps" to 5,
                ),
            ).exchange()
            .expectStatus().isCreated
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
