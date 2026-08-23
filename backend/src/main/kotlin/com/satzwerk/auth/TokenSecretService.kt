package com.satzwerk.auth

import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

@Service
class TokenSecretService {
    fun hash(raw: String): String = sha256Bytes(raw).joinToString("") { "%02x".format(it) }

    fun generateHexToken(
        byteCount: Int,
        prefix: String = "",
    ): String {
        val bytes = ByteArray(byteCount)
        SecureRandom().nextBytes(bytes)
        return prefix + bytes.joinToString("") { "%02x".format(it) }
    }

    private fun sha256Bytes(raw: String): ByteArray =
        MessageDigest
            .getInstance("SHA-256")
            .digest(raw.toByteArray(StandardCharsets.UTF_8))
}
