package com.satzwerk.publicapi

import com.satzwerk.PostgresTestContainer
import com.satzwerk.auth.AuthResponse
import com.satzwerk.auth.CreatedPersonalApiTokenResponse
import com.satzwerk.sessions.SetLogResponse
import com.satzwerk.sessions.WorkoutSessionResponse
import com.satzwerk.workouts.ExerciseResponse
import com.satzwerk.workouts.WorkoutGroupResponse
import com.satzwerk.workouts.WorkoutPlanResponse
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

    @Test
    fun `public heatmap returns stable payload for analytics read token`() {
        val jwt = registerAndGetJwt("public-read-${UUID.randomUUID()}@test.com")
        val token = createToken(jwt, "Heatmap Reader", listOf(PublicScope.ANALYTICS_READ))

        client
            .get()
            .uri("/api/public/analytics/heatmap?from=2026-01-01&to=2026-01-03")
            .header("Authorization", "Bearer ${token.token}")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(3)
            .jsonPath("$[0].date").isEqualTo("2026-01-01")
            .jsonPath("$[0].count").isEqualTo(0)
            .jsonPath("$[0].intensity").isEqualTo(0)
            .jsonPath("$[1].date").isEqualTo("2026-01-02")
            .jsonPath("$[1].count").isEqualTo(0)
            .jsonPath("$[1].intensity").isEqualTo(0)
            .jsonPath("$[2].date").isEqualTo("2026-01-03")
            .jsonPath("$[2].count").isEqualTo(0)
            .jsonPath("$[2].intensity").isEqualTo(0)
    }

    @Test
    fun `public heatmap is scoped to token owner`() {
        val ownerJwt = registerAndGetJwt("public-owner-${UUID.randomUUID()}@test.com")
        val ownerToken = createToken(ownerJwt, "Owner Analytics", listOf(PublicScope.ANALYTICS_READ))
        val ownerExerciseId = createExercise(ownerJwt, "Bench Press", "CHEST")
        val ownerPlanId = createPlan(ownerJwt, "Owner Plan")
        val ownerGroupId = createGroup(ownerJwt, ownerPlanId, "Owner Push", ownerExerciseId)

        val otherJwt = registerAndGetJwt("public-other-${UUID.randomUUID()}@test.com")
        val otherExerciseId = createExercise(otherJwt, "Squat", "LEGS")
        val otherPlanId = createPlan(otherJwt, "Other Plan")
        val otherGroupId = createGroup(otherJwt, otherPlanId, "Other Legs", otherExerciseId)

        val today = LocalDate.now(ZoneOffset.UTC)
        val ownerSession = startSession(ownerJwt, ownerGroupId)
        repeat(2) { index ->
            addSetLog(ownerJwt, ownerSession.id, ownerExerciseId, index + 1)
        }
        completeSession(ownerJwt, ownerSession.id)

        val otherSession = startSession(otherJwt, otherGroupId)
        repeat(4) { index ->
            addSetLog(otherJwt, otherSession.id, otherExerciseId, index + 1)
        }
        completeSession(otherJwt, otherSession.id)

        client
            .get()
            .uri("/api/public/analytics/heatmap?from=$today&to=$today")
            .header("Authorization", "Bearer ${ownerToken.token}")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].date").isEqualTo(today.toString())
            .jsonPath("$[0].count").isEqualTo(2)
            .jsonPath("$[0].intensity").isEqualTo(1)
    }

    private fun registerAndGetJwt(email: String): String =
        client
            .post()
            .uri("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("email" to email, "password" to "password123", "displayName" to "Test"))
            .exchange()
            .expectStatus().isCreated
            .expectBody(AuthResponse::class.java)
            .returnResult()
            .responseBody!!
            .accessToken

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
            .expectBody(CreatedPersonalApiTokenResponse::class.java)
            .returnResult()
            .responseBody!!

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
        setLog: SetLogFixture = SetLogFixture(),
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
    ): UUID =
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
            .id

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
}

private data class SetLogFixture(
    val weight: BigDecimal = BigDecimal("80.0"),
    val reps: Int = 5,
)
