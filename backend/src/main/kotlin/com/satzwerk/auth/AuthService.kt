package com.satzwerk.auth

import com.satzwerk.common.ForbiddenException
import com.satzwerk.users.User
import com.satzwerk.users.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.Instant

private const val SECONDS_PER_DAY = 86400L

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
) {
    private val logger = LoggerFactory.getLogger(AuthService::class.java)

    suspend fun register(
        email: String,
        password: String,
        displayName: String,
    ): TokenPair {
        if (userRepository.findByEmail(email) != null) {
            throw DuplicateEmailException(email)
        }

        val user =
            userRepository.save(
                User(
                    email = email,
                    passwordHash = passwordEncoder.encode(password),
                    displayName = displayName,
                ),
            )
        return issueTokenPair(requireNotNull(user.id))
    }

    suspend fun login(
        email: String,
        password: String,
    ): TokenPair {
        val user = userRepository.findByEmail(email) ?: throw InvalidCredentialsException()
        if (!passwordEncoder.matches(password, user.passwordHash)) {
            throw InvalidCredentialsException()
        }
        return issueTokenPair(requireNotNull(user.id))
    }

    suspend fun refresh(
        rawRefreshToken: String,
        csrfHeaderToken: String?,
        csrfCookieToken: String?,
    ): TokenPair {
        val token = requireActiveRefreshToken(rawRefreshToken)
        validateCsrfToken(token, csrfHeaderToken, csrfCookieToken)

        refreshTokenRepository.save(token.copy(revokedAt = Instant.now()))
        val pair = issueTokenPair(token.userId)
        try {
            cleanupOldTokens()
        } catch (e: DataAccessException) {
            logger.warn("Post-refresh token cleanup failed", e)
        }
        return pair
    }

    suspend fun logout(rawRefreshToken: String?) {
        rawRefreshToken
            ?.takeUnless(String::isBlank)
            ?.let { revokeRefreshTokenIfActive(it) }
    }

    fun refreshTokenMaxAgeSeconds(): Long = jwtService.refreshTokenMaxAgeSeconds()

    private suspend fun cleanupOldTokens() {
        val cutoff = Instant.now().minusSeconds(REFRESH_TOKEN_RETENTION_DAYS * SECONDS_PER_DAY)
        refreshTokenRepository.deleteByExpiresAtBefore(cutoff)
        refreshTokenRepository.deleteByRevokedAtIsNotNullAndRevokedAtBefore(cutoff)
    }

    private suspend fun issueTokenPair(userId: java.util.UUID): TokenPair {
        val rawRefreshToken = jwtService.generateRefreshToken()
        val rawCsrfToken = jwtService.generateCsrfToken()
        refreshTokenRepository.save(
            RefreshToken(
                userId = userId,
                tokenHash = jwtService.sha256(rawRefreshToken),
                csrfTokenHash = jwtService.sha256(rawCsrfToken),
                expiresAt = jwtService.refreshTokenExpiresAt(),
            ),
        )
        return TokenPair(
            accessToken = jwtService.generateAccessToken(userId),
            refreshToken = rawRefreshToken,
            csrfToken = rawCsrfToken,
        )
    }

    private suspend fun requireActiveRefreshToken(rawRefreshToken: String): RefreshToken {
        val token =
            refreshTokenRepository.findByTokenHash(jwtService.sha256(rawRefreshToken))
                ?: throw InvalidRefreshTokenException()

        if (token.revokedAt != null || token.expiresAt.isBefore(Instant.now())) {
            throw InvalidRefreshTokenException()
        }

        return token
    }

    private fun validateCsrfToken(
        token: RefreshToken,
        csrfHeaderToken: String?,
        csrfCookieToken: String?,
    ) {
        val csrfHeader = requireCsrfHeaderToken(csrfHeaderToken)
        val csrfCookie = requireCsrfCookieToken(csrfCookieToken)
        val matchesStoredHash = token.csrfTokenHash == jwtService.sha256(csrfHeader)

        if (csrfHeader != csrfCookie || !matchesStoredHash) {
            throw ForbiddenException("CSRF token mismatch")
        }
    }

    private suspend fun revokeRefreshTokenIfActive(rawRefreshToken: String) {
        val token = refreshTokenRepository.findByTokenHash(jwtService.sha256(rawRefreshToken)) ?: return
        if (token.revokedAt == null) {
            refreshTokenRepository.save(token.copy(revokedAt = Instant.now()))
        }
    }
}

private fun requireCsrfHeaderToken(csrfHeaderToken: String?): String =
    csrfHeaderToken?.takeUnless(String::isBlank)
        ?: throw ForbiddenException("Missing CSRF token header")

private fun requireCsrfCookieToken(csrfCookieToken: String?): String =
    csrfCookieToken?.takeUnless(String::isBlank)
        ?: throw ForbiddenException("Missing CSRF token cookie")
