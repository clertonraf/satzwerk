package com.satzwerk.auth

import com.satzwerk.users.User
import com.satzwerk.users.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.Instant

private const val CLEANUP_DAYS_THRESHOLD = 30L
private const val SECONDS_PER_DAY = 86400L

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
) {
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

    suspend fun refresh(rawRefreshToken: String): TokenPair {
        val token =
            refreshTokenRepository.findByTokenHash(jwtService.sha256(rawRefreshToken))
                ?: throw InvalidRefreshTokenException()

        if (token.revokedAt != null || token.expiresAt.isBefore(Instant.now())) {
            throw InvalidRefreshTokenException()
        }

        refreshTokenRepository.save(token.copy(revokedAt = Instant.now()))
        val pair = issueTokenPair(token.userId)
        // Cleanup runs best-effort: a transient DB error must not roll back the completed rotation.
        runCatching { cleanupOldTokens() }
        return pair
    }

    private suspend fun cleanupOldTokens() {
        val cutoff = Instant.now().minusSeconds(CLEANUP_DAYS_THRESHOLD * SECONDS_PER_DAY)
        refreshTokenRepository.deleteByExpiresAtBefore(cutoff)
        refreshTokenRepository.deleteByRevokedAtIsNotNullAndRevokedAtBefore(cutoff)
    }

    private suspend fun issueTokenPair(userId: java.util.UUID): TokenPair {
        val rawRefreshToken = jwtService.generateRefreshToken()
        refreshTokenRepository.save(
            RefreshToken(
                userId = userId,
                tokenHash = jwtService.sha256(rawRefreshToken),
                expiresAt = jwtService.refreshTokenExpiresAt(),
            ),
        )
        return TokenPair(
            accessToken = jwtService.generateAccessToken(userId),
            refreshToken = rawRefreshToken,
        )
    }
}
