package com.satzwerk.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TokenSecretServiceTest {
    private val tokenSecretService = TokenSecretService()

    @Test
    fun `hash returns stable sha256 digest`() {
        val digest = tokenSecretService.hash("satzwerk-token")

        assertEquals(
            "dbb673c5f592174eceef1b70a4dc78f2a59fa64f8e7e87689cd0c3c9f1b4abe7",
            digest,
        )
    }

    @Test
    fun `generateHexToken supports personal token prefix format`() {
        val token = tokenSecretService.generateHexToken(byteCount = 16, prefix = "satzwerk_")

        assertTrue(token.startsWith("satzwerk_"))
        assertEquals("satzwerk_".length + 32, token.length)
    }

    @Test
    fun `generateHexToken supports opaque partner token format`() {
        val token = tokenSecretService.generateHexToken(byteCount = 32)

        assertEquals(64, token.length)
    }
}
