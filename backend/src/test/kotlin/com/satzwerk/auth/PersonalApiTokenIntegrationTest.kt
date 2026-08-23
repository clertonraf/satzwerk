package com.satzwerk.auth

import com.satzwerk.PostgresTestContainer
import com.satzwerk.analytics.HeatmapEntry
import com.satzwerk.publicapi.PublicScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PersonalApiTokenIntegrationTest : PostgresTestContainer() {
    @Autowired
    lateinit var client: WebTestClient

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun registerAndGetJwt(email: String): String =
        client.post().uri("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("email" to email, "password" to "password123", "displayName" to "Test"))
            .exchange()
            .expectStatus().isCreated
            .expectBody(AuthResponse::class.java)
            .returnResult().responseBody!!.accessToken

    private fun createToken(
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
            .returnResult().responseBody!!

    // ── token lifecycle ───────────────────────────────────────────────────────

    @Test
    fun `create returns 201 with raw token exposed once`() {
        val jwt = registerAndGetJwt("pat-create@test.com")
        val resp = createToken(jwt, "My Script", listOf(PublicScope.ANALYTICS_READ))
        assertTrue(resp.token.startsWith("satzwerk_"), "raw token must start with satzwerk_ prefix")
        assertEquals(listOf(PublicScope.ANALYTICS_READ), resp.scopes)
        assertEquals("My Script", resp.name)
    }

    @Test
    fun `create rejects unknown scope`() {
        val jwt = registerAndGetJwt("pat-bad-scope@test.com")
        client.post().uri("/api/tokens")
            .header("Authorization", "Bearer $jwt")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to "bad", "scopes" to listOf(PublicScope.ANALYTICS_READ, "invalid:scope")))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("Unknown scopes: invalid:scope")
    }

    @Test
    fun `create rejects empty scopes`() {
        val jwt = registerAndGetJwt("pat-no-scope@test.com")
        client.post().uri("/api/tokens")
            .header("Authorization", "Bearer $jwt")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to "empty", "scopes" to emptyList<String>()))
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `list returns only active tokens for owner`() {
        val jwt = registerAndGetJwt("pat-list@test.com")
        createToken(jwt, "Token A", listOf(PublicScope.ANALYTICS_READ))
        createToken(jwt, "Token B", listOf(PublicScope.EXERCISES_READ))

        client.get().uri("/api/tokens")
            .header("Authorization", "Bearer $jwt")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)
            .jsonPath("$[0].name").isNotEmpty
            .jsonPath("$[0].token").doesNotExist()
    }

    @Test
    fun `revoke removes token from active list`() {
        val jwt = registerAndGetJwt("pat-revoke@test.com")
        val token = createToken(jwt, "To Revoke", listOf(PublicScope.ANALYTICS_READ))

        client.delete().uri("/api/tokens/${token.id}")
            .header("Authorization", "Bearer $jwt")
            .exchange()
            .expectStatus().isNoContent

        client.get().uri("/api/tokens")
            .header("Authorization", "Bearer $jwt")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(0)
    }

    @Test
    fun `revoked token is rejected immediately on public route`() {
        val jwt = registerAndGetJwt("pat-revoke-immediate@test.com")
        val created = createToken(jwt, "Revoke Me", listOf(PublicScope.ANALYTICS_READ))
        val rawToken = created.token

        // Confirm it works before revocation
        client.get().uri("/api/public/analytics/heatmap?from=2026-01-01&to=2026-01-07")
            .header("Authorization", "Bearer $rawToken")
            .exchange()
            .expectStatus().isOk

        // Revoke
        client.delete().uri("/api/tokens/${created.id}")
            .header("Authorization", "Bearer $jwt")
            .exchange()
            .expectStatus().isNoContent

        // Must be rejected immediately — 401 since auth context is never set for revoked tokens
        client.get().uri("/api/public/analytics/heatmap?from=2026-01-01&to=2026-01-07")
            .header("Authorization", "Bearer $rawToken")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `token with wrong scope returns 403 on public heatmap`() {
        val jwt = registerAndGetJwt("pat-wrong-scope@test.com")
        val created = createToken(jwt, "Exercises Only", listOf(PublicScope.EXERCISES_READ))

        client.get().uri("/api/public/analytics/heatmap?from=2026-01-01&to=2026-01-07")
            .header("Authorization", "Bearer ${created.token}")
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.error").isEqualTo("Required scope: ${PublicScope.ANALYTICS_READ}")
    }

    @Test
    fun `missing bearer token returns 401 on public heatmap`() {
        client.get().uri("/api/public/analytics/heatmap?from=2026-01-01&to=2026-01-07")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `valid token with analytics read returns heatmap data`() {
        val jwt = registerAndGetJwt("pat-heatmap@test.com")
        val created = createToken(jwt, "Heatmap Reader", listOf(PublicScope.ANALYTICS_READ))

        client.get().uri("/api/public/analytics/heatmap?from=2026-01-01&to=2026-01-07")
            .header("Authorization", "Bearer ${created.token}")
            .exchange()
            .expectStatus().isOk
            .expectBodyList(HeatmapEntry::class.java)
            .hasSize(7)
    }

    @Test
    fun `PAT cannot be used to manage tokens - JWT required`() {
        val jwt = registerAndGetJwt("pat-meta@test.com")
        val created = createToken(jwt, "Meta Token", listOf(PublicScope.ANALYTICS_READ))

        // A PAT cannot be used to create other tokens — JWT is required for management routes
        client.post().uri("/api/tokens")
            .header("Authorization", "Bearer ${created.token}")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to "nested", "scopes" to listOf(PublicScope.ANALYTICS_READ)))
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `user cannot revoke another users token`() {
        val jwtA = registerAndGetJwt("pat-owner-a@test.com")
        val jwtB = registerAndGetJwt("pat-owner-b@test.com")
        val tokenA = createToken(jwtA, "A token", listOf(PublicScope.ANALYTICS_READ))

        client.delete().uri("/api/tokens/${tokenA.id}")
            .header("Authorization", "Bearer $jwtB")
            .exchange()
            .expectStatus().isNotFound
    }
}
