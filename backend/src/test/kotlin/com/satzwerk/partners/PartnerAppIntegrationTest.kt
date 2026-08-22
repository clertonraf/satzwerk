package com.satzwerk.partners

import com.satzwerk.PostgresTestContainer
import com.satzwerk.auth.AuthResponse
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
class PartnerAppIntegrationTest : PostgresTestContainer() {
    @Autowired
    lateinit var client: WebTestClient

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun registerAndLogin(suffix: String = UUID.randomUUID().toString()): String {
        val auth =
            client
                .post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    mapOf(
                        "email" to "partner-test-$suffix@example.com",
                        "password" to "password123",
                        "displayName" to "Partner Tester",
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
        scopes: String = "exercises:read plans:read",
    ): PartnerAppRegistrationResponse =
        client
            .post()
            .uri("/api/partner-apps")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer $token")
            .bodyValue(
                mapOf(
                    "name" to "My Test App",
                    "description" to "Integration test partner app",
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
        scopes: String = "exercises:read",
    ): AppGrantResponse =
        client
            .post()
            .uri("/api/partner-grants")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer $token")
            .bodyValue(
                mapOf(
                    "clientId" to clientId,
                    "grantedScopes" to scopes,
                ),
            ).exchange()
            .expectStatus().isCreated
            .returnResult<AppGrantResponse>()
            .responseBody
            .blockFirst()!!

    // ── Registration ──────────────────────────────────────────────────────────

    @Test
    fun `register partner app returns client credentials`() {
        val token = registerAndLogin()
        client
            .post()
            .uri("/api/partner-apps")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer $token")
            .bodyValue(
                mapOf(
                    "name" to "Cool App",
                    "description" to "A cool third-party app",
                    "redirectUri" to "https://coolapp.example/callback",
                    "scopes" to "exercises:read sessions:read",
                ),
            ).exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.id").isNotEmpty
            .jsonPath("$.clientId").value<String> { cid -> assert(cid.startsWith("satzwerk_")) }
            .jsonPath("$.clientSecret").isNotEmpty
            .jsonPath("$.scopes").isEqualTo("exercises:read sessions:read")
    }

    @Test
    fun `register partner app rejects unknown scopes`() {
        val token = registerAndLogin()
        client
            .post()
            .uri("/api/partner-apps")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer $token")
            .bodyValue(
                mapOf(
                    "name" to "Bad App",
                    "description" to "App with invalid scope",
                    "redirectUri" to "https://bad.example/callback",
                    "scopes" to "admin:all",
                ),
            ).exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `list partner apps returns registered apps without client secret`() {
        val token = registerAndLogin()
        registerApp(token, scopes = "exercises:read")
        client
            .get()
            .uri("/api/partner-apps")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$").isArray
            .jsonPath("$[0].clientSecret").doesNotExist()
    }

    // ── Grant ─────────────────────────────────────────────────────────────────

    @Test
    fun `grant access issues opaque app access token`() {
        val token = registerAndLogin()
        val app = registerApp(token, scopes = "exercises:read plans:read")
        client
            .post()
            .uri("/api/partner-grants")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer $token")
            .bodyValue(
                mapOf(
                    "clientId" to app.clientId,
                    "grantedScopes" to "exercises:read",
                ),
            ).exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.grantId").isNotEmpty
            .jsonPath("$.accessToken").isNotEmpty
            .jsonPath("$.grantedScopes").isEqualTo("exercises:read")
    }

    @Test
    fun `grant rejects scopes not declared by app`() {
        val token = registerAndLogin()
        val app = registerApp(token, scopes = "exercises:read")
        client
            .post()
            .uri("/api/partner-grants")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer $token")
            .bodyValue(
                mapOf(
                    "clientId" to app.clientId,
                    "grantedScopes" to "sessions:write",
                ),
            ).exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `duplicate active grant returns 409`() {
        val token = registerAndLogin()
        val app = registerApp(token)
        grantAccess(token, app.clientId)
        client
            .post()
            .uri("/api/partner-grants")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer $token")
            .bodyValue(
                mapOf(
                    "clientId" to app.clientId,
                    "grantedScopes" to "exercises:read",
                ),
            ).exchange()
            .expectStatus().isEqualTo(409)
    }

    @Test
    fun `list active grants returns only non-revoked grants`() {
        val token = registerAndLogin()
        val app = registerApp(token)
        grantAccess(token, app.clientId)
        client
            .get()
            .uri("/api/partner-grants")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$").isArray
            .jsonPath("$[0].appName").isEqualTo("My Test App")
    }

    // ── Revocation ────────────────────────────────────────────────────────────

    @Test
    fun `revoke grant removes it from active grants list`() {
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
            .get()
            .uri("/api/partner-grants")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[?(@.grantId == '${grant.grantId}')]").doesNotExist()
    }

    @Test
    fun `revoke by wrong user returns 403`() {
        val tokenA = registerAndLogin("user-a-${UUID.randomUUID()}")
        val tokenB = registerAndLogin("user-b-${UUID.randomUUID()}")
        val app = registerApp(tokenA)
        val grant = grantAccess(tokenA, app.clientId)
        client
            .delete()
            .uri("/api/partner-grants/${grant.grantId}")
            .header("Authorization", "Bearer $tokenB")
            .exchange()
            .expectStatus().isForbidden
    }

    // ── Credential binding: probe route (GET /api/partner-grants/me) ──────────

    @Test
    fun `app access token authenticates probe route and returns bound identity`() {
        val token = registerAndLogin()
        val app = registerApp(token)
        val grant = grantAccess(token, app.clientId)
        client
            .get()
            .uri("/api/partner-grants/me")
            .header("X-App-Token", grant.accessToken)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.grantId").isEqualTo(grant.grantId.toString())
            .jsonPath("$.appId").isEqualTo(app.id.toString())
            .jsonPath("$.grantedScopes").isEqualTo("exercises:read")
    }

    @Test
    fun `app access token is rejected on probe route after grant is revoked`() {
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
            .get()
            .uri("/api/partner-grants/me")
            .header("X-App-Token", grant.accessToken)
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `partner token cannot access grant management routes`() {
        val token = registerAndLogin()
        val app = registerApp(token)
        val grant = grantAccess(token, app.clientId)
        // Partner token presented to a management route must be rejected (no JWT = 401).
        client
            .get()
            .uri("/api/partner-grants")
            .header("X-App-Token", grant.accessToken)
            .exchange()
            .expectStatus().isUnauthorized
    }

    // ── Credential binding: token is bound to both app AND consenting user ────

    @Test
    fun `app access token is bound to the consenting user and cannot surface another user's identity`() {
        // User A and user B independently grant the same app.
        // Each token resolves to its own user — A's token must show A's userId, not B's.
        val tokenA = registerAndLogin("binding-a-${UUID.randomUUID()}")
        val tokenB = registerAndLogin("binding-b-${UUID.randomUUID()}")
        val app = registerApp(tokenA)
        val grantA = grantAccess(tokenA, app.clientId)
        val grantB = grantAccess(tokenB, app.clientId)

        val bindingA =
            client
                .get()
                .uri("/api/partner-grants/me")
                .header("X-App-Token", grantA.accessToken)
                .exchange()
                .expectStatus().isOk
                .returnResult<PartnerGrantBinding>()
                .responseBody
                .blockFirst()!!

        val bindingB =
            client
                .get()
                .uri("/api/partner-grants/me")
                .header("X-App-Token", grantB.accessToken)
                .exchange()
                .expectStatus().isOk
                .returnResult<PartnerGrantBinding>()
                .responseBody
                .blockFirst()!!

        // Each token resolves to its own grant and its own user.
        assert(bindingA.grantId == grantA.grantId) { "Token A must resolve to grant A" }
        assert(bindingB.grantId == grantB.grantId) { "Token B must resolve to grant B" }
        assert(bindingA.userId != bindingB.userId) { "Tokens must resolve to different users" }
    }
}
