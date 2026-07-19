package com.satzwerk.export

import com.satzwerk.PostgresTestContainer
import com.satzwerk.auth.AuthResponse
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
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ExportIntegrationTest : PostgresTestContainer() {
    @Autowired
    lateinit var client: WebTestClient

    @Test
    fun `export returns all user data including exercises plans and sessions`(): Unit =
        run {
            val token = registerAndLogin("export-${UUID.randomUUID()}@test.com", "password123", "Exporter")
            val exerciseId = createExercise(token, "Squat", "LEGS")
            val planId = createPlan(token, "Leg Day")
            activatePlan(token, planId)
            val groupId = createGroup(token, planId, "Leg Group", exerciseId)
            val sessionId = startSession(token, groupId)
            addSetLog(token, sessionId, exerciseId, BigDecimal("100.0"), 5)
            completeSession(token, sessionId)

            client
                .get()
                .uri("/api/export")
                .header("Authorization", "Bearer $token")
                .exchange()
                .expectStatus().isOk
                .expectHeader().valueMatches("Content-Disposition", "attachment.*satzwerk-export.json.*")
                .expectBody()
                .jsonPath("$.version").isEqualTo(2)
                .jsonPath("$.exportedAt").isNotEmpty
                .jsonPath("$.profile.email").isNotEmpty
                .jsonPath("$.exercises.length()").isEqualTo(1)
                .jsonPath("$.exercises[0].name").isEqualTo("Squat")
                .jsonPath("$.workoutPlans.length()").isEqualTo(1)
                .jsonPath("$.workoutPlans[0].groups.length()").isEqualTo(1)
                .jsonPath("$.workoutPlans[0].groups[0].exercises.length()").isEqualTo(1)
                .jsonPath("$.workoutSessions.length()").isEqualTo(1)
                .jsonPath("$.workoutSessions[0].setLogs.length()").isEqualTo(1)
        }

    @Test
    fun `export does not include password hash`(): Unit =
        run {
            val token = registerAndLogin("nopwd-${UUID.randomUUID()}@test.com", "password123", "NoPass")

            client
                .get()
                .uri("/api/export")
                .header("Authorization", "Bearer $token")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.profile.passwordHash").doesNotExist()
                .jsonPath("$.profile.password").doesNotExist()
        }

    @Test
    fun `import adds exercises plans sessions and returns summary`(): Unit =
        run {
            val exportToken = registerAndLogin("imp-src-${UUID.randomUUID()}@test.com", "password123", "Source")
            val exerciseId = createExercise(exportToken, "Deadlift", "BACK")
            val planId = createPlan(exportToken, "Power Plan")
            activatePlan(exportToken, planId)
            val groupId = createGroup(exportToken, planId, "Power Group", exerciseId)
            val sessionId = startSession(exportToken, groupId)
            addSetLog(exportToken, sessionId, exerciseId, BigDecimal("150.0"), 3)
            completeSession(exportToken, sessionId)

            val exportBody = fetchExport(exportToken)

            val importToken = registerAndLogin("imp-dst-${UUID.randomUUID()}@test.com", "password123", "Dest")

            client
                .post()
                .uri("/api/import")
                .header("Authorization", "Bearer $importToken")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(exportBody)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.importedExercises").isEqualTo(1)
                .jsonPath("$.importedWorkoutPlans").isEqualTo(1)
                .jsonPath("$.importedWorkoutSessions").isEqualTo(1)
                .jsonPath("$.importedSetLogs").isEqualTo(1)
                .jsonPath("$.reusedExercises").isEqualTo(0)
        }

    @Test
    fun `import deduplicates exercises by name case insensitive`(): Unit =
        run {
            val exportToken = registerAndLogin("dedup-src-${UUID.randomUUID()}@test.com", "password123", "DedupSrc")
            val exerciseId = createExercise(exportToken, "Bench Press", "CHEST")
            val planId = createPlan(exportToken, "Push Plan")
            activatePlan(exportToken, planId)
            val groupId = createGroup(exportToken, planId, "Push Group", exerciseId)
            val sessionId = startSession(exportToken, groupId)
            addSetLog(exportToken, sessionId, exerciseId, BigDecimal("80.0"), 8)
            completeSession(exportToken, sessionId)

            val exportBody = fetchExport(exportToken)

            val importToken = registerAndLogin("dedup-dst-${UUID.randomUUID()}@test.com", "password123", "DedupDst")
            // Pre-create an exercise with the same name (different case)
            createExercise(importToken, "bench press", "CHEST")

            client
                .post()
                .uri("/api/import")
                .header("Authorization", "Bearer $importToken")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(exportBody)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.importedExercises").isEqualTo(0)
                .jsonPath("$.reusedExercises").isEqualTo(1)
        }

    @Test
    fun `import returns 409 when user has open workout session`(): Unit =
        run {
            val srcToken = registerAndLogin("open-src-${UUID.randomUUID()}@test.com", "password123", "OpenSrc")
            val exerciseId = createExercise(srcToken, "Row", "BACK")
            val planId = createPlan(srcToken, "Row Plan")
            activatePlan(srcToken, planId)
            val groupId = createGroup(srcToken, planId, "Row Group", exerciseId)
            val sessionId = startSession(srcToken, groupId)
            addSetLog(srcToken, sessionId, exerciseId, BigDecimal("60.0"), 10)
            completeSession(srcToken, sessionId)
            val exportBody = fetchExport(srcToken)

            val dstToken = registerAndLogin("open-dst-${UUID.randomUUID()}@test.com", "password123", "OpenDst")
            val dstExerciseId = createExercise(dstToken, "OHP", "SHOULDERS")
            val dstPlanId = createPlan(dstToken, "OHP Plan")
            activatePlan(dstToken, dstPlanId)
            val dstGroupId = createGroup(dstToken, dstPlanId, "OHP Group", dstExerciseId)
            // Leave session open
            startSession(dstToken, dstGroupId)

            client
                .post()
                .uri("/api/import")
                .header("Authorization", "Bearer $dstToken")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(exportBody)
                .exchange()
                .expectStatus().isEqualTo(409)
        }

    @Test
    fun `import returns 400 for invalid version`(): Unit =
        run {
            val token = registerAndLogin("badver-${UUID.randomUUID()}@test.com", "password123", "BadVer")

            client
                .post()
                .uri("/api/import")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapOf("version" to 99, "profile" to mapOf("email" to "x@x.com", "displayName" to "X")))
                .exchange()
                .expectStatus().isBadRequest
        }

    @Test
    fun `export requires authentication`(): Unit =
        run {
            client.get().uri("/api/export").exchange().expectStatus().isUnauthorized
        }

    @Test
    fun `import requires authentication`(): Unit =
        run {
            client.post().uri("/api/import").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapOf("version" to 1)).exchange().expectStatus().isUnauthorized
        }

    // --- Helpers ---

    private fun registerAndLogin(
        email: String,
        password: String,
        displayName: String,
    ): String {
        val response =
            client
                .post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapOf("email" to email, "password" to password, "displayName" to displayName))
                .exchange()
                .expectStatus().isCreated
                .expectBody(AuthResponse::class.java).returnResult().responseBody!!
        return response.accessToken
    }

    private fun createExercise(
        token: String,
        name: String,
        muscleGroup: String,
    ): UUID {
        val response =
            client
                .post().uri("/api/exercises")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapOf("name" to name, "muscleGroup" to muscleGroup))
                .exchange()
                .expectStatus().isCreated
                .expectBody(ExerciseResponse::class.java).returnResult().responseBody!!
        return response.id
    }

    private fun createPlan(
        token: String,
        name: String,
    ): UUID {
        val response =
            client
                .post().uri("/api/plans")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapOf("name" to name))
                .exchange()
                .expectStatus().isCreated
                .expectBody(WorkoutPlanResponse::class.java).returnResult().responseBody!!
        return response.id
    }

    private fun activatePlan(
        token: String,
        planId: UUID,
    ) {
        client.post().uri("/api/plans/$planId/activate")
            .header("Authorization", "Bearer $token")
            .exchange().expectStatus().isNoContent
    }

    private fun createGroup(
        token: String,
        planId: UUID,
        title: String,
        exerciseId: UUID,
    ): UUID {
        val groupResponse =
            client
                .post().uri("/api/plans/$planId/groups")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapOf("title" to title))
                .exchange()
                .expectStatus().isCreated
                .expectBody(WorkoutGroupResponse::class.java).returnResult().responseBody!!

        client.post().uri("/api/plans/$planId/groups/${groupResponse.id}/exercises")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("exerciseId" to exerciseId, "sets" to 3, "reps" to 8))
            .exchange().expectStatus().isCreated

        return groupResponse.id
    }

    private fun startSession(
        token: String,
        groupId: UUID,
    ): UUID {
        data class SessionResp(val id: UUID)
        val response =
            client
                .post().uri("/api/sessions")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapOf("workoutGroupId" to groupId))
                .exchange()
                .expectStatus().isCreated
                .expectBody(SessionResp::class.java).returnResult().responseBody!!
        return response.id
    }

    private fun addSetLog(
        token: String,
        sessionId: UUID,
        exerciseId: UUID,
        weight: BigDecimal,
        reps: Int,
    ) {
        client.post().uri("/api/sessions/$sessionId/set-logs")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf("exerciseId" to exerciseId, "setNumber" to 1, "weight" to weight, "reps" to reps),
            )
            .exchange().expectStatus().isCreated
    }

    private fun completeSession(
        token: String,
        sessionId: UUID,
    ) {
        client.post().uri("/api/sessions/$sessionId/complete")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("notes" to null))
            .exchange().expectStatus().isOk
    }

    private fun fetchExport(token: String): Map<*, *> =
        client.get().uri("/api/export")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java).returnResult().responseBody!!
}
