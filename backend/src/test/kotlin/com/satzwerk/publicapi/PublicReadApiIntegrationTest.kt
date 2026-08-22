package com.satzwerk.publicapi

import com.satzwerk.PostgresTestContainer
import com.satzwerk.auth.AuthResponse
import com.satzwerk.auth.CreatedPersonalApiTokenResponse
import com.satzwerk.auth.TokenScope
import com.satzwerk.partners.AppGrantResponse
import com.satzwerk.partners.PartnerAppRegistrationResponse
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
import org.springframework.test.web.reactive.server.WebTestClient
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PublicReadApiIntegrationTest : PostgresTestContainer() {
    @Autowired
    lateinit var client: WebTestClient

    private lateinit var ownerJwt: String
    private lateinit var otherJwt: String
    private lateinit var ownerExerciseId: UUID
    private lateinit var ownerPlanId: UUID
    private lateinit var ownerWorkoutGroupId: UUID
    private lateinit var completedSession: WorkoutSessionResponse

    @BeforeEach
    fun setup() {
        val suffix = UUID.randomUUID()
        ownerJwt = registerAndLogin("public-owner-$suffix@test.com", "password123", "Public Owner")
        otherJwt = registerAndLogin("public-other-$suffix@test.com", "password123", "Other User")

        ownerExerciseId = createExercise(ownerJwt, "Bench Press", "CHEST")
        ownerPlanId = createPlan(ownerJwt, "Push Pull Legs")
        activatePlan(ownerJwt, ownerPlanId)
        ownerWorkoutGroupId = createGroup(ownerJwt, ownerPlanId, "Push Day", ownerExerciseId)

        completedSession = startSession(ownerJwt, ownerWorkoutGroupId)
        addSetLog(
            ownerJwt,
            completedSession.id,
            PublicSetLogFixture(
                exerciseId = ownerExerciseId,
                setNumber = 1,
                weight = BigDecimal("100.0"),
                reps = 5,
            ),
        )
        completedSession = completeSession(ownerJwt, completedSession.id)

        val otherExerciseId = createExercise(otherJwt, "Deadlift", "BACK")
        val otherPlanId = createPlan(otherJwt, "Other Plan")
        activatePlan(otherJwt, otherPlanId)
        val otherGroupId = createGroup(otherJwt, otherPlanId, "Pull Day", otherExerciseId)
        val otherSession = startSession(otherJwt, otherGroupId)
        addSetLog(
            otherJwt,
            otherSession.id,
            PublicSetLogFixture(
                exerciseId = otherExerciseId,
                setNumber = 1,
                weight = BigDecimal("140.0"),
                reps = 3,
            ),
        )
        completeSession(otherJwt, otherSession.id)
    }

    @Test
    fun `partner token reads exercises and plans`() {
        val grant =
            grantPartnerAccess(
                ownerJwt,
                declaredScopes = listOf(TokenScope.EXERCISES_READ, TokenScope.PLANS_READ),
                grantedScopes = listOf(TokenScope.EXERCISES_READ, TokenScope.PLANS_READ),
            )

        client.get()
            .uri("/api/public/exercises")
            .header("X-App-Token", grant.accessToken)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].id").isEqualTo(ownerExerciseId.toString())
            .jsonPath("$[0].name").isEqualTo("Bench Press")

        client.get()
            .uri("/api/public/exercises/$ownerExerciseId")
            .header("X-App-Token", grant.accessToken)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(ownerExerciseId.toString())
            .jsonPath("$.muscleGroup").isEqualTo("CHEST")

        client.get()
            .uri("/api/public/plans")
            .header("X-App-Token", grant.accessToken)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].id").isEqualTo(ownerPlanId.toString())
            .jsonPath("$[0].isActive").isEqualTo(true)

        client.get()
            .uri("/api/public/plans/$ownerPlanId")
            .header("X-App-Token", grant.accessToken)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(ownerPlanId.toString())
            .jsonPath("$.groups.length()").isEqualTo(1)
            .jsonPath("$.groups[0].id").isEqualTo(ownerWorkoutGroupId.toString())
            .jsonPath("$.groups[0].exercises[0].exerciseId").isEqualTo(ownerExerciseId.toString())
    }

    @Test
    fun `partner token reads sessions and analytics`() {
        val grant =
            grantPartnerAccess(
                ownerJwt,
                declaredScopes = listOf(TokenScope.SESSIONS_READ, TokenScope.ANALYTICS_READ),
                grantedScopes = listOf(TokenScope.SESSIONS_READ, TokenScope.ANALYTICS_READ),
            )
        val today = LocalDate.now(ZoneOffset.UTC)

        client.get()
            .uri("/api/public/sessions/history")
            .header("X-App-Token", grant.accessToken)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].id").isEqualTo(completedSession.id.toString())

        client.get()
            .uri("/api/public/sessions/${completedSession.id}")
            .header("X-App-Token", grant.accessToken)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.setLogs.length()").isEqualTo(1)
            .jsonPath("$.setLogs[0].weight").isEqualTo(100)

        client.get()
            .uri("/api/public/analytics/summary")
            .header("X-App-Token", grant.accessToken)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.totalSessions").isEqualTo(1)

        client.get()
            .uri("/api/public/analytics/weekly-trend?weeks=4")
            .header("X-App-Token", grant.accessToken)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(4)

        client.get()
            .uri("/api/public/analytics/personal-records?limit=5")
            .header("X-App-Token", grant.accessToken)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].exerciseId").isEqualTo(ownerExerciseId.toString())

        client.get()
            .uri("/api/public/analytics/heatmap?from=$today&to=$today")
            .header("X-App-Token", grant.accessToken)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].count").isEqualTo(1)
    }

    @Test
    fun `personal token with wrong scope gets explicit 403 on public route`() {
        val token = createPersonalToken(ownerJwt, "Exercises only", listOf(TokenScope.EXERCISES_READ))

        client.get()
            .uri("/api/public/plans")
            .header("Authorization", "Bearer ${token.token}")
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.error").isEqualTo("Required scope: ${TokenScope.PLANS_READ}")
    }

    @Test
    fun `revoked partner token is rejected immediately on public routes`() {
        val grant =
            grantPartnerAccess(
                ownerJwt,
                declaredScopes = listOf(TokenScope.EXERCISES_READ),
                grantedScopes = listOf(TokenScope.EXERCISES_READ),
            )

        client.delete()
            .uri("/api/partner-grants/${grant.grantId}")
            .header("Authorization", "Bearer $ownerJwt")
            .exchange()
            .expectStatus().isNoContent

        client.get()
            .uri("/api/public/exercises")
            .header("X-App-Token", grant.accessToken)
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `public read routes stay scoped to the consenting user`() {
        val grant =
            grantPartnerAccess(
                ownerJwt,
                declaredScopes = listOf(TokenScope.EXERCISES_READ, TokenScope.SESSIONS_READ),
                grantedScopes = listOf(TokenScope.EXERCISES_READ, TokenScope.SESSIONS_READ),
            )

        client.get()
            .uri("/api/public/exercises")
            .header("X-App-Token", grant.accessToken)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].name").isEqualTo("Bench Press")
            .jsonPath("$[?(@.name == 'Deadlift')]").doesNotExist()

        client.get()
            .uri("/api/public/sessions/history")
            .header("X-App-Token", grant.accessToken)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].id").isEqualTo(completedSession.id.toString())
    }

    @Test
    fun `missing public credential returns 401`() {
        client.get()
            .uri("/api/public/analytics/summary")
            .exchange()
            .expectStatus().isUnauthorized
    }

    private fun registerAndLogin(
        email: String,
        password: String,
        displayName: String,
    ): String =
        client.post().uri("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("email" to email, "password" to password, "displayName" to displayName))
            .exchange()
            .expectStatus().isCreated
            .expectBody(AuthResponse::class.java)
            .returnResult()
            .responseBody!!
            .accessToken

    private fun createPersonalToken(
        jwt: String,
        name: String,
        scopes: List<String>,
    ): CreatedPersonalApiTokenResponse =
        client.post().uri("/api/tokens")
            .header("Authorization", "Bearer $jwt")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to name, "scopes" to scopes))
            .exchange()
            .expectStatus().isCreated
            .expectBody(CreatedPersonalApiTokenResponse::class.java)
            .returnResult()
            .responseBody!!

    private fun grantPartnerAccess(
        jwt: String,
        declaredScopes: List<String>,
        grantedScopes: List<String>,
    ): AppGrantResponse {
        val app = registerPartnerApp(jwt, declaredScopes)
        return client.post().uri("/api/partner-grants")
            .header("Authorization", "Bearer $jwt")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "clientId" to app.clientId,
                    "grantedScopes" to grantedScopes.joinToString(" "),
                ),
            ).exchange()
            .expectStatus().isCreated
            .expectBody(AppGrantResponse::class.java)
            .returnResult()
            .responseBody!!
    }

    private fun registerPartnerApp(
        jwt: String,
        scopes: List<String>,
    ): PartnerAppRegistrationResponse =
        client.post().uri("/api/partner-apps")
            .header("Authorization", "Bearer $jwt")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "name" to "Public Reader ${UUID.randomUUID()}",
                    "description" to "Public read integration test app",
                    "redirectUri" to "https://example.com/callback",
                    "scopes" to scopes.joinToString(" "),
                ),
            ).exchange()
            .expectStatus().isCreated
            .expectBody(PartnerAppRegistrationResponse::class.java)
            .returnResult()
            .responseBody!!

    private fun createExercise(
        token: String,
        name: String,
        muscleGroup: String,
    ): UUID =
        client.post().uri("/api/exercises")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to name, "muscleGroup" to muscleGroup))
            .exchange()
            .expectStatus().isCreated
            .expectBody(ExerciseResponse::class.java)
            .returnResult()
            .responseBody!!
            .id

    private fun createPlan(
        token: String,
        name: String,
    ): UUID =
        client.post().uri("/api/plans")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to name))
            .exchange()
            .expectStatus().isCreated
            .expectBody(WorkoutPlanResponse::class.java)
            .returnResult()
            .responseBody!!
            .id

    private fun activatePlan(
        token: String,
        planId: UUID,
    ) {
        client.post().uri("/api/plans/$planId/activate")
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
            client.post().uri("/api/plans/$planId/groups")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapOf("title" to title))
                .exchange()
                .expectStatus().isCreated
                .expectBody(WorkoutGroupResponse::class.java)
                .returnResult()
                .responseBody!!
                .id

        client.post().uri("/api/plans/$planId/groups/$groupId/exercises")
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

    private fun startSession(
        token: String,
        groupId: UUID,
    ): WorkoutSessionResponse =
        client.post().uri("/api/sessions")
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
        setLog: PublicSetLogFixture,
    ): SetLogResponse =
        client.post().uri("/api/sessions/$sessionId/set-logs")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "exerciseId" to setLog.exerciseId,
                    "setNumber" to setLog.setNumber,
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
        client.post().uri("/api/sessions/$sessionId/complete")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("notes" to "Completed via seed"))
            .exchange()
            .expectStatus().isOk
            .expectBody(WorkoutSessionResponse::class.java)
            .returnResult()
            .responseBody!!

    private data class PublicSetLogFixture(
        val exerciseId: UUID,
        val setNumber: Int,
        val weight: BigDecimal,
        val reps: Int,
    )
}
