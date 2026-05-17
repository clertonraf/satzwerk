package com.satzwerk.auth

import com.satzwerk.config.JwtProperties
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService(
    jwtProperties: JwtProperties,
) {
    private val secretKey: SecretKey =
        Keys.hmacShaKeyFor(sha256Bytes(jwtProperties.secret))
    private val expiryMs = jwtProperties.expiryMs
    private val refreshExpiryMs = jwtProperties.refreshExpiryMs

    fun generateAccessToken(userId: UUID): String {
        val now = Instant.now()
        return Jwts
            .builder()
            .subject(userId.toString())
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(expiryMs)))
            .signWith(secretKey)
            .compact()
    }

    fun generateRefreshToken(): String = UUID.randomUUID().toString()

    fun refreshTokenExpiresAt(): Instant = Instant.now().plusMillis(refreshExpiryMs)

    fun validateAccessToken(token: String): UUID =
        UUID.fromString(
            Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .payload
                .subject,
        )

    fun sha256(raw: String): String = sha256Bytes(raw).joinToString("") { "%02x".format(it) }

    private fun sha256Bytes(raw: String): ByteArray =
        MessageDigest
            .getInstance("SHA-256")
            .digest(raw.toByteArray(StandardCharsets.UTF_8))
}
