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
class PublicExerciseIntegrationTest : PostgresTestContainer() {
    @Autowired
    lateinit var client: WebTestClient

    @Autowired
    lateinit var publicWriteIdempotencyRecordRepository: PublicWriteIdempotencyRecordRepository

    @Autowired
    lateinit var publicWriteAuditRepository: PublicWriteAuditRepository

    @Test
    fun `partner app with exercises write scope can create an Exercise`() {
        val token = registerAndLogin()
        val app = registerApp(token)
        val grant = grantAccess(token, app.clientId)

        client
            .post()
            .uri("/api/public/exercises")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "name" to "Partner Bench Press",
                    "muscleGroup" to "CHEST",
                    "description" to "Imported from partner",
                ),
            ).exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.id").isNotEmpty
            .jsonPath("$.name").isEqualTo("Partner Bench Press")
            .jsonPath("$.muscleGroup").isEqualTo("CHEST")
            .jsonPath("$.description").isEqualTo("Imported from partner")
    }

    @Test
    fun `partner app with exercises write scope can update an owned Exercise`() {
        val token = registerAndLogin()
        val app = registerApp(token)
        val grant = grantAccess(token, app.clientId)
        val created = createExercise(token, "Bench Press", "CHEST")

        client
            .put()
            .uri("/api/public/exercises/${created.id}")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "name" to "Paused Bench Press",
                    "videoUrl" to "https://example.com/bench",
                ),
            ).exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(created.id.toString())
            .jsonPath("$.name").isEqualTo("Paused Bench Press")
            .jsonPath("$.muscleGroup").isEqualTo("CHEST")
            .jsonPath("$.videoUrl").isEqualTo("https://example.com/bench")
    }

    @Test
    fun `partner app without exercises write scope is rejected with 403`() {
        val token = registerAndLogin()
        val app = registerApp(token, scopes = "plans:write")
        val grant = grantAccess(token, app.clientId, scopes = "plans:write")

        client
            .post()
            .uri("/api/public/exercises")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to "Blocked Exercise", "muscleGroup" to "CHEST"))
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.error").isEqualTo("Required scope: exercises:write")
    }

    @Test
    fun `partner app cannot update an Exercise owned by a different user`() {
        val tokenA = registerAndLogin(uniqueSuffix("exercise-own-a"))
        val tokenB = registerAndLogin(uniqueSuffix("exercise-own-b"))
        val created = createExercise(tokenA, "Owner Bench", "CHEST")
        val appB = registerApp(tokenB)
        val grantB = grantAccess(tokenB, appB.clientId)

        client
            .put()
            .uri("/api/public/exercises/${created.id}")
            .header("X-App-Token", grantB.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to "Stolen Bench"))
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `exercise create with missing required fields returns 400`() {
        val token = registerAndLogin()
        val app = registerApp(token)
        val grant = grantAccess(token, app.clientId)

        client
            .post()
            .uri("/api/public/exercises")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "name" to "",
                    "muscleGroup" to "",
                ),
            )
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.errors.name").exists()
            .jsonPath("$.errors.muscleGroup").exists()
    }

    @Test
    fun `same Idempotency-Key replays the original Exercise create response and records audit twice`() {
        val token = registerAndLogin()
        val app = registerApp(token)
        val grant = grantAccess(token, app.clientId)
        val idempotencyKey = UUID.randomUUID().toString()
        val requestBody = mapOf("name" to "Replay Bench", "muscleGroup" to "CHEST")

        val first =
            client
                .post()
                .uri("/api/public/exercises")
                .header("X-App-Token", grant.accessToken)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isCreated
                .returnResult<ExerciseResponse>()
                .responseBody
                .blockFirst()!!

        val replayed =
            client
                .post()
                .uri("/api/public/exercises")
                .header("X-App-Token", grant.accessToken)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isCreated
                .returnResult<ExerciseResponse>()
                .responseBody
                .blockFirst()!!

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
            assertEquals(PublicScope.EXERCISES_WRITE, audits.first().grantedScopes)
        }
    }

    @Test
    fun `PAT with exercises write scope replays the original Exercise create response`() {
        val token = registerAndLogin()
        val personalToken = createToken(token, "Exercise Writer", listOf(PublicScope.EXERCISES_WRITE))
        val idempotencyKey = UUID.randomUUID().toString()
        val requestBody = mapOf("name" to "PAT Replay Bench", "muscleGroup" to "CHEST")

        val first =
            client
                .post()
                .uri("/api/public/exercises")
                .header("Authorization", "Bearer ${personalToken.token}")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isCreated
                .returnResult<ExerciseResponse>()
                .responseBody
                .blockFirst()!!

        val replayed =
            client
                .post()
                .uri("/api/public/exercises")
                .header("Authorization", "Bearer ${personalToken.token}")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isCreated
                .returnResult<ExerciseResponse>()
                .responseBody
                .blockFirst()!!

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
            assertEquals(PublicScope.EXERCISES_WRITE, audits.first().grantedScopes)
            assertEquals(PublicWritePrincipalType.PERSONAL_API_TOKEN, records.first().principalType)
            assertEquals(personalToken.id, records.first().credentialId)
            assertEquals(PublicWritePrincipalType.PERSONAL_API_TOKEN, audits.first().principalType)
            assertEquals(personalToken.id, audits.first().credentialId)
            assertEquals(null, audits.first().appId)
            assertEquals(null, audits.first().grantId)
        }
    }

    @Test
    fun `PAT without exercises write scope is rejected with 403 on Exercise create`() {
        val token = registerAndLogin()
        val personalToken = createToken(token, "Exercise Reader", listOf(PublicScope.PLANS_WRITE))

        client
            .post()
            .uri("/api/public/exercises")
            .header("Authorization", "Bearer ${personalToken.token}")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to "Blocked PAT Exercise", "muscleGroup" to "CHEST"))
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.error").isEqualTo("Required scope: ${PublicScope.EXERCISES_WRITE}")
    }

    @Test
    fun `reusing an Idempotency-Key with a different Exercise payload returns 409`() {
        val token = registerAndLogin()
        val app = registerApp(token)
        val grant = grantAccess(token, app.clientId)
        val idempotencyKey = UUID.randomUUID().toString()

        client
            .post()
            .uri("/api/public/exercises")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to "Replay Bench", "muscleGroup" to "CHEST"))
            .exchange()
            .expectStatus().isCreated

        client
            .post()
            .uri("/api/public/exercises")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to "Changed On Replay", "muscleGroup" to "BACK"))
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.error").isEqualTo("Idempotency-Key already used with a different payload")
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

    private fun registerAndLogin(suffix: String = UUID.randomUUID().toString()): String {
        val auth =
            client
                .post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    mapOf(
                        "email" to "pub-exercise-$suffix@example.com",
                        "password" to "password123",
                        "displayName" to "Public Exercise Tester",
                    ),
                ).exchange()
                .expectStatus().isCreated
                .returnResult<AuthResponse>()
                .responseBody
                .blockFirst()!!
        return auth.accessToken
    }

    private fun uniqueSuffix(prefix: String): String = "$prefix-${UUID.randomUUID().toString().take(8)}"

    private fun registerApp(
        token: String,
        scopes: String = "exercises:write",
    ): PartnerAppRegistrationResponse =
        client
            .post()
            .uri("/api/partner-apps")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer $token")
            .bodyValue(
                mapOf(
                    "name" to "Exercise App ${UUID.randomUUID()}",
                    "description" to "Integration test exercise partner app",
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
        scopes: String = "exercises:write",
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
