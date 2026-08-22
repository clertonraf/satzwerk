package com.satzwerk.measurements

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
import java.time.LocalDate
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PublicMeasurementIntegrationTest : PostgresTestContainer() {
    @Autowired
    lateinit var client: WebTestClient

    @Autowired
    lateinit var idempotencyRecordRepository: IdempotencyRecordRepository

    @Autowired
    lateinit var partnerWriteAuditRepository: PartnerWriteAuditRepository

    private val today: LocalDate = LocalDate.of(2026, 6, 1)

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun registerAndLogin(suffix: String = UUID.randomUUID().toString()): String {
        val auth =
            client
                .post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    mapOf(
                        "email" to "pub-meas-$suffix@example.com",
                        "password" to "password123",
                        "displayName" to "Pub Meas Tester",
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
        scopes: String = "measurements:write",
    ): PartnerAppRegistrationResponse =
        client
            .post()
            .uri("/api/partner-apps")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer $token")
            .bodyValue(
                mapOf(
                    "name" to "Measurement App",
                    "description" to "Integration test measurement partner app",
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
        scopes: String = "measurements:write",
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

    // ── Valid write ───────────────────────────────────────────────────────────

    @Test
    fun `partner app with measurements write scope can upsert a BodyMeasurement`() {
        val token = registerAndLogin()
        val app = registerApp(token)
        val grant = grantAccess(token, app.clientId)
        val idempotencyKey = UUID.randomUUID().toString()

        client
            .post()
            .uri("/api/public/measurements")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "measurementDate" to today.toString(),
                    "weightKg" to 80.5,
                    "shoulders" to 110.0,
                ),
            ).exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.measurementDate").isEqualTo(today.toString())
            .jsonPath("$.weightKg").isEqualTo(80.5)
            .jsonPath("$.shoulders").isEqualTo(110.0)
    }

    @Test
    fun `partner app upsert preserves existing fields on partial update`() {
        val token = registerAndLogin()
        val app = registerApp(token)
        val grant = grantAccess(token, app.clientId)

        // First write — set shoulders and chest
        client
            .post()
            .uri("/api/public/measurements")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "measurementDate" to today.toString(),
                    "shoulders" to 110.0,
                    "chest" to 95.0,
                ),
            ).exchange()
            .expectStatus().isOk

        // Second write — only send weightKg; shoulders and chest must be preserved
        client
            .post()
            .uri("/api/public/measurements")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "measurementDate" to today.toString(),
                    "weightKg" to 81.0,
                ),
            ).exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.shoulders").isEqualTo(110.0)
            .jsonPath("$.chest").isEqualTo(95.0)
            .jsonPath("$.weightKg").isEqualTo(81.0)
    }

    // ── Wrong scope ───────────────────────────────────────────────────────────

    @Test
    fun `partner app without measurements write scope is rejected with 403`() {
        val token = registerAndLogin()
        // Register app with read-only scope, grant only that scope
        val app = registerApp(token, scopes = "measurements:read")
        val grant = grantAccess(token, app.clientId, scopes = "measurements:read")

        client
            .post()
            .uri("/api/public/measurements")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("measurementDate" to today.toString(), "weightKg" to 80.0))
            .exchange()
            .expectStatus().isForbidden
    }

    // ── Invalid data ──────────────────────────────────────────────────────────

    @Test
    fun `measurement value below minimum is rejected with 400`() {
        val token = registerAndLogin()
        val app = registerApp(token)
        val grant = grantAccess(token, app.clientId)

        client
            .post()
            .uri("/api/public/measurements")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "measurementDate" to today.toString(),
                    "weightKg" to -5.0,
                ),
            ).exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `missing measurementDate is rejected with 400`() {
        val token = registerAndLogin()
        val app = registerApp(token)
        val grant = grantAccess(token, app.clientId)

        client
            .post()
            .uri("/api/public/measurements")
            .header("X-App-Token", grant.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("weightKg" to 80.0))
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `missing Idempotency-Key is rejected with 400`() {
        val token = registerAndLogin()
        val app = registerApp(token)
        val grant = grantAccess(token, app.clientId)

        client
            .post()
            .uri("/api/public/measurements")
            .header("X-App-Token", grant.accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("measurementDate" to today.toString(), "weightKg" to 80.0))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("Idempotency-Key header required")
    }

    @Test
    fun `same Idempotency-Key replays the original measurement response and records audit twice`() {
        val token = registerAndLogin()
        val app = registerApp(token)
        val grant = grantAccess(token, app.clientId)
        val idempotencyKey = UUID.randomUUID().toString()

        val first =
            client
                .post()
                .uri("/api/public/measurements")
                .header("X-App-Token", grant.accessToken)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapOf("measurementDate" to today.toString(), "weightKg" to 80.5))
                .exchange()
                .expectStatus().isOk
                .returnResult<MeasurementResponse>()
                .responseBody
                .blockFirst()!!

        val replayed =
            client
                .post()
                .uri("/api/public/measurements")
                .header("X-App-Token", grant.accessToken)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapOf("measurementDate" to today.toString(), "weightKg" to 91.0))
                .exchange()
                .expectStatus().isOk
                .returnResult<MeasurementResponse>()
                .responseBody
                .blockFirst()!!

        assertEquals(first, replayed)
        runBlocking {
            val records = idempotencyRecordRepository.findAllByGrantId(grant.grantId).toList()
            val audits = partnerWriteAuditRepository.findAllByGrantId(grant.grantId).toList()
            assertEquals(1, records.size)
            assertEquals(2, audits.size)
        }
    }

    // ── Ownership isolation ───────────────────────────────────────────────────

    @Test
    fun `partner write is isolated to the consenting user`() {
        val tokenA = registerAndLogin("iso-a-${UUID.randomUUID()}")
        val tokenB = registerAndLogin("iso-b-${UUID.randomUUID()}")

        val appA = registerApp(tokenA)
        val grantA = grantAccess(tokenA, appA.clientId)

        // User A writes a measurement via partner token
        client
            .post()
            .uri("/api/public/measurements")
            .header("X-App-Token", grantA.accessToken)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("measurementDate" to today.toString(), "weightKg" to 90.0))
            .exchange()
            .expectStatus().isOk

        // User B's JWT-authenticated GET must return zero measurements
        client
            .get()
            .uri("/api/measurements")
            .header("Authorization", "Bearer $tokenB")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(0)
    }
}
