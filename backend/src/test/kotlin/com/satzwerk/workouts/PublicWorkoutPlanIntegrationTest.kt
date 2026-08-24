package com.satzwerk.workouts

import com.satzwerk.PostgresTestContainer
import com.satzwerk.auth.AuthResponse
import com.satzwerk.auth.CreatedPersonalApiTokenResponse
import com.satzwerk.partners.AppGrantResponse
import com.satzwerk.partners.PartnerAppRegistrationResponse
import com.satzwerk.publicapi.PublicScope
import com.satzwerk.publicapi.PublicWriteAuditRepository
import com.satzwerk.publicapi.PublicWriteIdempotencyRecordRepository
import com.satzwerk.publicapi.PublicWritePrincipalType
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
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PublicWorkoutPlanIntegrationTest : PostgresTestContainer() {
    @Autowired
    lateinit var client: WebTestClient

    @Autowired
    lateinit var publicWriteIdempotencyRecordRepository: PublicWriteIdempotencyRecordRepository

    @Autowired
    lateinit var publicWriteAuditRepository: PublicWriteAuditRepository

    @Test
    fun `partner app with plans write scope can create a WorkoutPlan structure`() {
        val token = registerAndLogin()
        val exercise = createExercise(token, "Bench Press", "CHEST")
        val app = registerApp(token)
        val grant = grantAccess(token, app.clientId)

        val plan = createPublicPlan(grant, "Partner Plan")
        val group = createPublicGroup(grant, plan.id, "Treino A")
        val workoutExercise = createPublicWorkoutExercise(grant, plan.id, group.id, exercise.id)

        client
            .get()
            .uri("/api/plans/${plan.id}")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.name").isEqualTo("Partner Plan")
            .jsonPath("$.groups[0].title").isEqualTo("Treino A")
            .jsonPath("$.groups[0].exercises[0].id").isEqualTo(workoutExercise.id.toString())
            .jsonPath("$.groups[0].exercises[0].exerciseId").isEqualTo(exercise.id.toString())
    }

    @Test
    fun `partner app with plans write scope can update a WorkoutPlan structure`() {
        val token = registerAndLogin()
        val exercise = createExercise(token, "Bench Press", "CHEST")
        val app = registerApp(token)
        val grant = grantAccess(token, app.clientId)
        val plan = createPublicPlan(grant, "Partner Plan")
        val group = createPublicGroup(grant, plan.id, "Treino A")
        val workoutExercise = createPublicWorkoutExercise(grant, plan.id, group.id, exercise.id)

        updatePublicPlan(grant, plan.id, "Partner Plan Updated")
        updatePublicGroup(grant, plan.id, group.id, "Treino A Updated")
        updatePublicWorkoutExercise(
            grant,
            plan.id,
            group.id,
            workoutExercise.id,
            WorkoutExerciseUpdate(sets = 5, reps = 10),
        )

        client
            .get()
            .uri("/api/plans/${plan.id}")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.name").isEqualTo("Partner Plan Updated")
            .jsonPath("$.groups[0].title").isEqualTo("Treino A Updated")
            .jsonPath("$.groups[0].exercises[0].sets").isEqualTo(5)
            .jsonPath("$.groups[0].exercises[0].reps").isEqualTo(10)
    }

    @Test
    fun `public activation keeps only one WorkoutPlan active`() {
        val token = registerAndLogin()
        val app = registerApp(token)
        val grant = grantAccess(token, app.clientId)
        val planA = createPlan(token, "Plan A")
        val planB = createPlan(token, "Plan B")

        client
            .post()
            .uri("/api/public/plans/$planA/activate")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .exchange()
            .expectStatus().isOk

        client
            .post()
            .uri("/api/public/plans/$planB/activate")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .exchange()
            .expectStatus().isOk

        client
            .get()
            .uri("/api/plans")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[?(@.id == '$planA')].isActive").isEqualTo(listOf(false))
            .jsonPath("$[?(@.id == '$planB')].isActive").isEqualTo(listOf(true))
    }

    @Test
    fun `same Idempotency-Key replays the original WorkoutPlan activation response and records audit twice`() {
        val token = registerAndLogin()
        val app = registerApp(token)
        val grant = grantAccess(token, app.clientId)
        val plan = createPlan(token, "Replay Plan")
        val idempotencyKey = UUID.randomUUID().toString()

        val first = activatePublicPlan(grant, plan, idempotencyKey)
        val replayed = activatePublicPlan(grant, plan, idempotencyKey)

        assertEquals(first, replayed)
        runBlocking {
            val records =
                publicWriteIdempotencyRecordRepository
                    .findAllByPrincipalTypeAndCredentialId(PublicWritePrincipalType.PARTNER_APP, grant.grantId)
                    .toList()
            val audits =
                publicWriteAuditRepository
                    .findAllByPrincipalTypeAndCredentialId(PublicWritePrincipalType.PARTNER_APP, grant.grantId)
                    .toList()
            assertEquals(1, records.size)
            assertEquals(2, audits.size)
            assertEquals(PublicScope.PLANS_WRITE, audits.first().grantedScopes)
            assertEquals("""{"command":"activate-workout-plan"}""", records.first().requestFingerprint)
        }
    }

    @Test
    fun `PAT with plans write scope replays the original WorkoutPlan activation response`() {
        val token = registerAndLogin()
        val personalToken = createToken(token, "Plan Activator", listOf(PublicScope.PLANS_WRITE))
        val plan = createPlan(token, "PAT Replay Plan")
        val idempotencyKey = UUID.randomUUID().toString()

        val first = activatePublicPlan("Bearer ${personalToken.token}", "Authorization", plan, idempotencyKey)
        val replayed = activatePublicPlan("Bearer ${personalToken.token}", "Authorization", plan, idempotencyKey)

        assertEquals(first, replayed)
        runBlocking {
            val records =
                publicWriteIdempotencyRecordRepository
                    .findAllByPrincipalTypeAndCredentialId(
                        PublicWritePrincipalType.PERSONAL_API_TOKEN,
                        personalToken.id,
                    ).toList()
            val audits =
                publicWriteAuditRepository
                    .findAllByPrincipalTypeAndCredentialId(
                        PublicWritePrincipalType.PERSONAL_API_TOKEN,
                        personalToken.id,
                    ).toList()
            assertEquals(1, records.size)
            assertEquals(2, audits.size)
            assertEquals(PublicScope.PLANS_WRITE, audits.first().grantedScopes)
            assertEquals("""{"command":"activate-workout-plan"}""", records.first().requestFingerprint)
            assertEquals(PublicWritePrincipalType.PERSONAL_API_TOKEN, records.first().principalType)
            assertEquals(personalToken.id, records.first().credentialId)
            assertEquals(PublicWritePrincipalType.PERSONAL_API_TOKEN, audits.first().principalType)
            assertEquals(personalToken.id, audits.first().credentialId)
            assertEquals(null, audits.first().appId)
            assertEquals(null, audits.first().grantId)
        }
    }

    @Test
    fun `PAT without plans write scope is rejected with 403 on WorkoutPlan activation`() {
        val token = registerAndLogin()
        val personalToken = createToken(token, "Plan Reader", listOf(PublicScope.EXERCISES_WRITE))
        val plan = createPlan(token, "Blocked PAT Plan")

        client
            .post()
            .uri("/api/public/plans/$plan/activate")
            .header("Authorization", "Bearer ${personalToken.token}")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.error").isEqualTo("Required scope: ${PublicScope.PLANS_WRITE}")
    }

    @Test
    fun `partner app without plans write scope is rejected with 403 on WorkoutPlan create`() {
        val token = registerAndLogin()
        val app = registerApp(token, scopes = "exercises:write")
        val grant = grantAccess(token, app.clientId, scopes = "exercises:write")

        client
            .post()
            .uri("/api/public/plans")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to "Blocked Plan"))
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.error").isEqualTo("Required scope: plans:write")
    }

    @Test
    fun `partner app cannot update a WorkoutPlan owned by a different user`() {
        val tokenA = registerAndLogin(uniqueSuffix("plan-own-a"))
        val tokenB = registerAndLogin(uniqueSuffix("plan-own-b"))
        val planId = createPlan(tokenA, "Owner Plan")
        val appB = registerApp(tokenB)
        val grantB = grantAccess(tokenB, appB.clientId)

        client
            .put()
            .uri("/api/public/plans/$planId")
            .header("X-App-Token", grantB.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to "Hijacked Plan"))
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `partner app cannot add a WorkoutExercise using an Exercise owned by a different user`() {
        val tokenA = registerAndLogin(uniqueSuffix("plan-exercise-own-a"))
        val tokenB = registerAndLogin(uniqueSuffix("plan-exercise-own-b"))
        val foreignExercise = createExercise(tokenA, "Foreign Bench", "CHEST")
        val planId = createPlan(tokenB, "Receiver Plan")
        val groupId = createGroup(tokenB, planId, "Treino B")
        val appB = registerApp(tokenB)
        val grantB = grantAccess(tokenB, appB.clientId)

        client
            .post()
            .uri("/api/public/plans/$planId/groups/$groupId/exercises")
            .header("X-App-Token", grantB.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "exerciseId" to foreignExercise.id,
                    "sets" to 4,
                    "reps" to 8,
                ),
            ).exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `public WorkoutPlan create with invalid data returns 400`() {
        val token = registerAndLogin()
        val app = registerApp(token)
        val grant = grantAccess(token, app.clientId)

        client
            .post()
            .uri("/api/public/plans")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to ""))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.errors.name").exists()
    }

    private fun createExercise(
        token: String,
        name: String,
        muscleGroup: String,
    ): ExerciseResponse =
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
            .returnResult<ExerciseResponse>()
            .responseBody
            .blockFirst()!!

    private fun createPublicPlan(
        grant: AppGrantResponse,
        name: String,
    ): WorkoutPlanResponse =
        client
            .post()
            .uri("/api/public/plans")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to name))
            .exchange()
            .expectStatus().isCreated
            .returnResult<WorkoutPlanResponse>()
            .responseBody
            .blockFirst()!!

    private fun createPublicGroup(
        grant: AppGrantResponse,
        planId: UUID,
        title: String,
    ): WorkoutGroupResponse =
        client
            .post()
            .uri("/api/public/plans/$planId/groups")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("title" to title, "orderIndex" to 0))
            .exchange()
            .expectStatus().isCreated
            .returnResult<WorkoutGroupResponse>()
            .responseBody
            .blockFirst()!!

    private fun createPublicWorkoutExercise(
        grant: AppGrantResponse,
        planId: UUID,
        groupId: UUID,
        exerciseId: UUID,
    ): WorkoutExerciseResponse =
        client
            .post()
            .uri("/api/public/plans/$planId/groups/$groupId/exercises")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
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
            .returnResult<WorkoutExerciseResponse>()
            .responseBody
            .blockFirst()!!

    private fun updatePublicPlan(
        grant: AppGrantResponse,
        planId: UUID,
        name: String,
    ) {
        client
            .put()
            .uri("/api/public/plans/$planId")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to name))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.name").isEqualTo(name)
    }

    private fun updatePublicGroup(
        grant: AppGrantResponse,
        planId: UUID,
        groupId: UUID,
        title: String,
    ) {
        client
            .put()
            .uri("/api/public/plans/$planId/groups/$groupId")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("title" to title))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.title").isEqualTo(title)
    }

    private fun updatePublicWorkoutExercise(
        grant: AppGrantResponse,
        planId: UUID,
        groupId: UUID,
        workoutExerciseId: UUID,
        update: WorkoutExerciseUpdate,
    ) {
        client
            .put()
            .uri("/api/public/plans/$planId/groups/$groupId/exercises/$workoutExerciseId")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("sets" to update.sets, "reps" to update.reps))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.sets").isEqualTo(update.sets)
            .jsonPath("$.reps").isEqualTo(update.reps)
    }

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

    private fun createGroup(
        token: String,
        planId: UUID,
        title: String,
    ): UUID =
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

    private fun activatePublicPlan(
        grant: AppGrantResponse,
        planId: UUID,
        idempotencyKey: String,
    ): String = activatePublicPlan(grant.accessToken, "X-App-Token", planId, idempotencyKey)

    private fun activatePublicPlan(
        credential: String,
        headerName: String,
        planId: UUID,
        idempotencyKey: String,
    ): String =
        client
            .post()
            .uri("/api/public/plans/$planId/activate")
            .header(headerName, credential)
            .header("Idempotency-Key", idempotencyKey)
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody!!

    private fun registerAndLogin(suffix: String = UUID.randomUUID().toString()): String {
        val auth =
            client
                .post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    mapOf(
                        "email" to "pub-plan-$suffix@example.com",
                        "password" to "password123",
                        "displayName" to "Public Plan Tester",
                    ),
                ).exchange()
                .expectStatus().isCreated
                .returnResult<AuthResponse>()
                .responseBody
                .blockFirst()!!
        return auth.accessToken
    }

    private fun uniqueSuffix(prefix: String): String = "$prefix-${UUID.randomUUID().toString().take(8)}"

    private data class WorkoutExerciseUpdate(
        val sets: Int,
        val reps: Int,
    )

    private fun registerApp(
        token: String,
        scopes: String = "plans:write",
    ): PartnerAppRegistrationResponse =
        client
            .post()
            .uri("/api/partner-apps")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer $token")
            .bodyValue(
                mapOf(
                    "name" to "Plan App ${UUID.randomUUID()}",
                    "description" to "Integration test WorkoutPlan partner app",
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
        scopes: String = "plans:write",
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

    private fun createToken(
        jwt: String,
        name: String,
        scopes: List<String>,
    ): CreatedPersonalApiTokenResponse =
        client
            .post()
            .uri("/api/tokens")
            .header("Authorization", "Bearer $jwt")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to name, "scopes" to scopes))
            .exchange()
            .expectStatus().isCreated
            .returnResult<CreatedPersonalApiTokenResponse>()
            .responseBody
            .blockFirst()!!
}
