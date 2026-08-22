package com.satzwerk.medications

import com.satzwerk.PostgresTestContainer
import com.satzwerk.auth.AuthResponse
import com.satzwerk.partners.AppGrantResponse
import com.satzwerk.partners.PartnerAppRegistrationResponse
import com.satzwerk.publicapi.IdempotencyRecordRepository
import com.satzwerk.publicapi.PartnerWriteAuditRepository
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
import java.time.Instant
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PublicMedicationIntegrationTest : PostgresTestContainer() {
    @Autowired
    lateinit var client: WebTestClient

    @Autowired
    lateinit var idempotencyRecordRepository: IdempotencyRecordRepository

    @Autowired
    lateinit var partnerWriteAuditRepository: PartnerWriteAuditRepository

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun registerAndLogin(suffix: String = UUID.randomUUID().toString()): String {
        val auth =
            client
                .post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    mapOf(
                        "email" to "pub-med-$suffix@example.com",
                        "password" to "password123",
                        "displayName" to "Pub Med Tester",
                    ),
                ).exchange()
                .expectStatus().isCreated
                .returnResult<AuthResponse>()
                .responseBody
                .blockFirst()!!
        return auth.accessToken
    }

    private fun registerApp(
        token: String,
        scopes: String = "medications:write",
    ): PartnerAppRegistrationResponse =
        client
            .post()
            .uri("/api/partner-apps")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer $token")
            .bodyValue(
                mapOf(
                    "name" to "Medication App ${UUID.randomUUID()}",
                    "description" to "Integration test medication partner app",
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
        scopes: String = "medications:write",
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

    private fun defaultMedicationBody(name: String = "Vitamin D ${UUID.randomUUID()}") =
        mapOf(
            "name" to name,
            "dosageAmount" to 1000.0,
            "dosageUnit" to "IU",
            "frequency" to mapOf("type" to "DAILY", "timesPerDay" to 1),
        )

    // ── Medication create ─────────────────────────────────────────────────────

    @Test
    fun `partner app with medications write scope can create a Medication`() {
        val token = registerAndLogin()
        val app = registerApp(token)
        val grant = grantAccess(token, app.clientId)

        client
            .post()
            .uri("/api/public/medications")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(defaultMedicationBody("Omega-3"))
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.id").isNotEmpty
            .jsonPath("$.name").isEqualTo("Omega-3")
            .jsonPath("$.dosageUnit").isEqualTo("IU")
    }

    @Test
    fun `partner medication create enforces per-user name uniqueness`() {
        val token = registerAndLogin()
        val app = registerApp(token)
        val grant = grantAccess(token, app.clientId)

        client
            .post()
            .uri("/api/public/medications")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(defaultMedicationBody("Aspirin"))
            .exchange()
            .expectStatus().isCreated

        // Duplicate name must fail with 409
        client
            .post()
            .uri("/api/public/medications")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(defaultMedicationBody("Aspirin"))
            .exchange()
            .expectStatus().isEqualTo(409)
    }

    // ── Medication update ─────────────────────────────────────────────────────

    @Test
    fun `partner app with medications write scope can update an owned Medication`() {
        val token = registerAndLogin()
        val app = registerApp(token)
        val grant = grantAccess(token, app.clientId)

        val created =
            client
                .post()
                .uri("/api/public/medications")
                .header("X-App-Token", grant.accessToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(defaultMedicationBody("Zinc"))
                .exchange()
                .expectStatus().isCreated
                .returnResult<MedicationResponse>()
                .responseBody
                .blockFirst()!!

        client
            .put()
            .uri("/api/public/medications/${created.id}")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "name" to "Zinc Updated",
                    "dosageAmount" to 500.0,
                    "dosageUnit" to "MG",
                    "frequency" to mapOf("type" to "DAILY", "timesPerDay" to 2),
                ),
            ).exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.name").isEqualTo("Zinc Updated")
            .jsonPath("$.dosageAmount").isEqualTo(500.0)
    }

    @Test
    fun `partner app cannot update a Medication owned by a different user`() {
        val tokenA = registerAndLogin("med-own-a-${UUID.randomUUID()}")
        val tokenB = registerAndLogin("med-own-b-${UUID.randomUUID()}")

        // User A creates a medication via JWT
        val created =
            client
                .post()
                .uri("/api/medications")
                .header("Authorization", "Bearer $tokenA")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(defaultMedicationBody("Melatonin"))
                .exchange()
                .expectStatus().isOk
                .returnResult<MedicationResponse>()
                .responseBody
                .blockFirst()!!

        // User B registers an app and grants themselves medications:write
        val appB = registerApp(tokenB)
        val grantB = grantAccess(tokenB, appB.clientId)

        // User B's partner token must not be able to update User A's medication
        client
            .put()
            .uri("/api/public/medications/${created.id}")
            .header("X-App-Token", grantB.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "name" to "Owned By A",
                    "dosageAmount" to 1.0,
                    "dosageUnit" to "MG",
                    "frequency" to mapOf("type" to "DAILY", "timesPerDay" to 1),
                ),
            ).exchange()
            .expectStatus().isNotFound
    }

    // ── MedicationLog (logDose) ───────────────────────────────────────────────

    @Test
    fun `partner app with medications write scope can log a dose for an owned Medication`() {
        val token = registerAndLogin()
        val app = registerApp(token)
        val grant = grantAccess(token, app.clientId)

        val med =
            client
                .post()
                .uri("/api/public/medications")
                .header("X-App-Token", grant.accessToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(defaultMedicationBody("Magnesium"))
                .exchange()
                .expectStatus().isCreated
                .returnResult<MedicationResponse>()
                .responseBody
                .blockFirst()!!

        client
            .post()
            .uri("/api/public/medications/${med.id}/logs")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "takenAt" to Instant.now().toString(),
                    "taken" to true,
                    "notes" to "with breakfast",
                ),
            ).exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.id").isNotEmpty
            .jsonPath("$.medicationId").isEqualTo(med.id.toString())
            .jsonPath("$.taken").isEqualTo(true)
    }

    @Test
    fun `partner app cannot log a dose for a Medication owned by a different user`() {
        val tokenA = registerAndLogin("log-own-a-${UUID.randomUUID()}")
        val tokenB = registerAndLogin("log-own-b-${UUID.randomUUID()}")

        // User A creates a medication
        val med =
            client
                .post()
                .uri("/api/medications")
                .header("Authorization", "Bearer $tokenA")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(defaultMedicationBody("Ibuprofen"))
                .exchange()
                .expectStatus().isOk
                .returnResult<MedicationResponse>()
                .responseBody
                .blockFirst()!!

        // User B's partner token
        val appB = registerApp(tokenB)
        val grantB = grantAccess(tokenB, appB.clientId)

        client
            .post()
            .uri("/api/public/medications/${med.id}/logs")
            .header("X-App-Token", grantB.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("takenAt" to Instant.now().toString(), "taken" to true))
            .exchange()
            .expectStatus().isNotFound
    }

    // ── Wrong scope ───────────────────────────────────────────────────────────

    @Test
    fun `partner app without medications write scope is rejected with 403 on create`() {
        val token = registerAndLogin()
        val app = registerApp(token, scopes = "measurements:write")
        val grant = grantAccess(token, app.clientId, scopes = "measurements:write")

        client
            .post()
            .uri("/api/public/medications")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(defaultMedicationBody("Blocked Vitamin"))
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `partner app without medications write scope is rejected with 403 on log dose`() {
        val tokenOwner = registerAndLogin("log-scope-owner-${UUID.randomUUID()}")
        val tokenOther = registerAndLogin("log-scope-other-${UUID.randomUUID()}")

        // Owner creates a medication via first-party API
        val med =
            client
                .post()
                .uri("/api/medications")
                .header("Authorization", "Bearer $tokenOwner")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(defaultMedicationBody("Scoped Med"))
                .exchange()
                .expectStatus().isOk
                .returnResult<MedicationResponse>()
                .responseBody
                .blockFirst()!!

        // Other user has no medications:write scope
        val app = registerApp(tokenOther, scopes = "measurements:write")
        val grant = grantAccess(tokenOther, app.clientId, scopes = "measurements:write")

        client
            .post()
            .uri("/api/public/medications/${med.id}/logs")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("takenAt" to Instant.now().toString(), "taken" to true))
            .exchange()
            .expectStatus().isForbidden
    }

    // ── Revoked access ────────────────────────────────────────────────────────

    @Test
    fun `revoked partner grant cannot create Medication`() {
        val token = registerAndLogin()
        val app = registerApp(token)
        val grant = grantAccess(token, app.clientId)

        client
            .delete()
            .uri("/api/partner-grants/${grant.grantId}")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isNoContent

        client
            .post()
            .uri("/api/public/medications")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(defaultMedicationBody("Blocked Med"))
            .exchange()
            .expectStatus().isUnauthorized
    }

    // ── Invalid data ──────────────────────────────────────────────────────────

    @Test
    fun `medication create with invalid dosage amount returns 400`() {
        val token = registerAndLogin()
        val app = registerApp(token)
        val grant = grantAccess(token, app.clientId)

        client
            .post()
            .uri("/api/public/medications")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "name" to "Bad Med",
                    "dosageAmount" to 0.0,
                    "dosageUnit" to "MG",
                    "frequency" to mapOf("type" to "DAILY", "timesPerDay" to 1),
                ),
            ).exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `log dose with missing takenAt returns 400`() {
        val token = registerAndLogin()
        val app = registerApp(token)
        val grant = grantAccess(token, app.clientId)

        val med =
            client
                .post()
                .uri("/api/public/medications")
                .header("X-App-Token", grant.accessToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(defaultMedicationBody("Test Med"))
                .exchange()
                .expectStatus().isCreated
                .returnResult<MedicationResponse>()
                .responseBody
                .blockFirst()!!

        client
            .post()
            .uri("/api/public/medications/${med.id}/logs")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("taken" to true))
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `missing Idempotency-Key is rejected with 400 on medication create`() {
        val token = registerAndLogin()
        val app = registerApp(token)
        val grant = grantAccess(token, app.clientId)

        client
            .post()
            .uri("/api/public/medications")
            .header("X-App-Token", grant.accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(defaultMedicationBody("No Key"))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("Idempotency-Key header required")
    }

    @Test
    fun `same Idempotency-Key replays the original medication create response and records audit twice`() {
        val token = registerAndLogin()
        val app = registerApp(token)
        val grant = grantAccess(token, app.clientId)
        val idempotencyKey = UUID.randomUUID().toString()
        val requestBody = defaultMedicationBody("Replay Med")

        val first =
            client
                .post()
                .uri("/api/public/medications")
                .header("X-App-Token", grant.accessToken)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isCreated
                .returnResult<MedicationResponse>()
                .responseBody
                .blockFirst()!!

        val replayed =
            client
                .post()
                .uri("/api/public/medications")
                .header("X-App-Token", grant.accessToken)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isCreated
                .returnResult<MedicationResponse>()
                .responseBody
                .blockFirst()!!

        assertEquals(first, replayed)
        runBlocking {
            val records = idempotencyRecordRepository.findAllByGrantId(grant.grantId).toList()
            val audits = partnerWriteAuditRepository.findAllByGrantId(grant.grantId).toList()
            assertEquals(1, records.size)
            assertEquals(2, audits.size)
            assertEquals("medications:write", audits.first().grantedScopes)
        }
    }
}
