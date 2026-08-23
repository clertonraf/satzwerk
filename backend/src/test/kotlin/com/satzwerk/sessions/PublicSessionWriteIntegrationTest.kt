package com.satzwerk.sessions

import com.satzwerk.PostgresTestContainer
import com.satzwerk.auth.AuthResponse
import com.satzwerk.partners.AppGrantResponse
import com.satzwerk.partners.PartnerAppRegistrationResponse
import com.satzwerk.publicapi.IdempotencyRecordRepository
import com.satzwerk.publicapi.PartnerWriteAuditRepository
import com.satzwerk.workouts.ExerciseResponse
import com.satzwerk.workouts.WorkoutGroupResponse
import com.satzwerk.workouts.WorkoutPlanResponse
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.returnResult
import java.math.BigDecimal
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PublicSessionWriteIntegrationTest : PostgresTestContainer() {
    @Autowired
    lateinit var client: WebTestClient

    @Autowired
    lateinit var idempotencyRecordRepository: IdempotencyRecordRepository

    @Autowired
    lateinit var partnerWriteAuditRepository: PartnerWriteAuditRepository

    @Test
    fun `partner app with sessions write scope can start WorkoutSession`() {
        val token = registerAndLogin()
        val exerciseId = createExercise(token, "Bench Press", "CHEST")
        val planId = createPlan(token, "Push Pull Legs")
        activatePlan(token, planId)
        val workoutGroupId = createGroup(token, planId, "Push Day", exerciseId)
        val grant = grantAccess(token, registerApp(token).clientId)

        client
            .post()
            .uri("/api/public/sessions")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("workoutGroupId" to workoutGroupId))
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.id").isNotEmpty
            .jsonPath("$.workoutGroupId").isEqualTo(workoutGroupId.toString())
            .jsonPath("$.completedAt").isEmpty
            .jsonPath("$.setLogs.length()").isEqualTo(0)
    }

    @Test
    fun `partner app can append and update SetLog on owned open WorkoutSession`() {
        val token = registerAndLogin()
        val exerciseId = createExercise(token, "Squat", "LEGS")
        val planId = createPlan(token, "Leg Day")
        activatePlan(token, planId)
        val workoutGroupId = createGroup(token, planId, "Heavy Legs", exerciseId)
        val grant = grantAccess(token, registerApp(token).clientId)

        val session = startPublicSession(grant.accessToken, workoutGroupId, UUID.randomUUID().toString())
        val setLog = addPublicSetLog(grant.accessToken, session.id, exerciseId, BigDecimal("140.0"), reps = 5)
        val updated = updatePublicSetLog(grant.accessToken, session.id, setLog.id, BigDecimal("145.0"), reps = 4)

        assertEquals(BigDecimal("145.0"), updated.weight)
        assertEquals(4, updated.reps)
    }

    @Test
    fun `partner app can complete WorkoutSession`() {
        val token = registerAndLogin()
        val exerciseId = createExercise(token, "Overhead Press", "SHOULDERS")
        val planId = createPlan(token, "Push Plan")
        activatePlan(token, planId)
        val workoutGroupId = createGroup(token, planId, "Press Day", exerciseId)
        val grant = grantAccess(token, registerApp(token).clientId)
        val session = startPublicSession(grant.accessToken, workoutGroupId, UUID.randomUUID().toString())

        client
            .post()
            .uri("/api/public/sessions/${session.id}/complete")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("notes" to "Synced from partner"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(session.id.toString())
            .jsonPath("$.notes").isEqualTo("Synced from partner")
            .jsonPath("$.completedAt").isNotEmpty
    }

    @Test
    fun `same Idempotency-Key replays the original WorkoutSession start response and records audit twice`() {
        val token = registerAndLogin()
        val exerciseId = createExercise(token, "Bench Press", "CHEST")
        val planId = createPlan(token, "Replay Plan")
        activatePlan(token, planId)
        val workoutGroupId = createGroup(token, planId, "Replay Day", exerciseId)
        val grant = grantAccess(token, registerApp(token).clientId)
        val idempotencyKey = UUID.randomUUID().toString()

        val first = startPublicSession(grant.accessToken, workoutGroupId, idempotencyKey)
        val replayed = startPublicSession(grant.accessToken, workoutGroupId, idempotencyKey)

        assertEquals(first, replayed)
        runBlocking {
            val records = idempotencyRecordRepository.findAllByGrantId(grant.grantId).toList()
            val audits = partnerWriteAuditRepository.findAllByGrantId(grant.grantId).toList()
            assertEquals(1, records.size)
            assertEquals(2, audits.size)
            assertEquals("sessions:write", audits.first().grantedScopes)
            assertEquals("""{"workoutGroupId":"$workoutGroupId"}""", records.first().requestFingerprint)
        }
    }

    @Test
    fun `reusing an Idempotency-Key with a different WorkoutSession start payload returns 409`() {
        val token = registerAndLogin()
        val firstExerciseId = createExercise(token, "Row", "BACK")
        val secondExerciseId = createExercise(token, "Pulldown", "BACK")
        val planId = createPlan(token, "Pull Plan")
        activatePlan(token, planId)
        val firstWorkoutGroupId = createGroup(token, planId, "Rows", firstExerciseId)
        val secondWorkoutGroupId = createGroup(token, planId, "Pulldowns", secondExerciseId)
        val grant = grantAccess(token, registerApp(token).clientId)
        val idempotencyKey = UUID.randomUUID().toString()

        client
            .post()
            .uri("/api/public/sessions")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("workoutGroupId" to firstWorkoutGroupId))
            .exchange()
            .expectStatus().isCreated

        client
            .post()
            .uri("/api/public/sessions")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("workoutGroupId" to secondWorkoutGroupId))
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.error").isEqualTo("Idempotency-Key already used with a different payload")
    }

    private fun startPublicSession(
        accessToken: String,
        workoutGroupId: UUID,
        idempotencyKey: String,
    ): WorkoutSessionResponse =
        client
            .post()
            .uri("/api/public/sessions")
            .header("X-App-Token", accessToken)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("workoutGroupId" to workoutGroupId))
            .exchange()
            .expectStatus().isCreated
            .returnResult<WorkoutSessionResponse>()
            .responseBody
            .blockFirst()!!

    private fun addPublicSetLog(
        accessToken: String,
        sessionId: UUID,
        exerciseId: UUID,
        weight: BigDecimal,
        reps: Int,
    ): SetLogResponse =
        client
            .post()
            .uri("/api/public/sessions/$sessionId/set-logs")
            .header("X-App-Token", accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
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
            .returnResult<SetLogResponse>()
            .responseBody
            .blockFirst()!!

    private fun updatePublicSetLog(
        accessToken: String,
        sessionId: UUID,
        setLogId: UUID,
        weight: BigDecimal,
        reps: Int,
    ): SetLogResponse =
        client
            .patch()
            .uri("/api/public/sessions/$sessionId/set-logs/$setLogId")
            .header("X-App-Token", accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("weight" to weight, "reps" to reps))
            .exchange()
            .expectStatus().isOk
            .returnResult<SetLogResponse>()
            .responseBody
            .blockFirst()!!

    private fun createExercise(
        token: String,
        name: String,
        muscleGroup: String,
    ): UUID =
        client
            .post()
            .uri("/api/exercises")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to name, "muscleGroup" to muscleGroup))
            .exchange()
            .expectStatus().isCreated
            .returnResult<ExerciseResponse>()
            .responseBody
            .blockFirst()!!
            .id

    private fun createPlan(
        token: String,
        name: String,
    ): UUID =
        client
            .post()
            .uri("/api/plans")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to name))
            .exchange()
            .expectStatus().isCreated
            .returnResult<WorkoutPlanResponse>()
            .responseBody
            .blockFirst()!!
            .id

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
        val groupId =
            client
                .post()
                .uri("/api/plans/$planId/groups")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapOf("title" to title))
                .exchange()
                .expectStatus().isCreated
                .returnResult<WorkoutGroupResponse>()
                .responseBody
                .blockFirst()!!
                .id

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
                    "orderIndex" to 0,
                ),
            ).exchange()
            .expectStatus().isCreated

        return groupId
    }

    private fun registerAndLogin(suffix: String = UUID.randomUUID().toString()): String =
        client
            .post()
            .uri("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "email" to "pub-session-$suffix@example.com",
                    "password" to "password123",
                    "displayName" to "Public Session Tester",
                ),
            ).exchange()
            .expectStatus().isCreated
            .returnResult<AuthResponse>()
            .responseBody
            .blockFirst()!!
            .accessToken

    private fun registerApp(
        token: String,
        scopes: String = "sessions:write",
    ): PartnerAppRegistrationResponse =
        client
            .post()
            .uri("/api/partner-apps")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer $token")
            .bodyValue(
                mapOf(
                    "name" to "Session App ${UUID.randomUUID()}",
                    "description" to "Integration test WorkoutSession partner app",
                    "redirectUri" to "https://example.com/callback",
                    "scopes" to scopes,
                ),
            ).exchange()
            .expectStatus().isCreated
            .returnResult<PartnerAppRegistrationResponse>()
            .responseBody
            .blockFirst()!!

    private fun grantAccess(
        token: String,
        clientId: String,
        scopes: String = "sessions:write",
    ): AppGrantResponse =
        client
            .post()
            .uri("/api/partner-grants")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer $token")
            .bodyValue(mapOf("clientId" to clientId, "grantedScopes" to scopes))
            .exchange()
            .expectStatus().isCreated
            .returnResult<AppGrantResponse>()
            .responseBody
            .blockFirst()!!
}
