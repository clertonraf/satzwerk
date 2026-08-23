package com.satzwerk.auth

import com.satzwerk.common.BadRequestException
import com.satzwerk.common.ForbiddenException
import com.satzwerk.common.NotFoundException
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

private const val MAX_ACTIVE_TOKENS_PER_USER = 25
private const val RAW_TOKEN_RANDOM_BYTES = 16

@Service
class PersonalApiTokenService(
    private val repository: PersonalApiTokenRepository,
    private val tokenSecretService: TokenSecretService,
) {
    suspend fun create(
        userId: UUID,
        name: String,
        scopes: List<String>,
    ): Pair<PersonalApiToken, String> {
        validateScopes(scopes)

        val active = repository.findByUserIdAndRevokedAtIsNull(userId)
        if (active.size >= MAX_ACTIVE_TOKENS_PER_USER) {
            throw BadRequestException("Maximum of $MAX_ACTIVE_TOKENS_PER_USER active tokens allowed")
        }

        val rawToken = buildRawToken()
        val token =
            repository.save(
                PersonalApiToken(
                    userId = userId,
                    name = name,
                    tokenHash = tokenSecretService.hash(rawToken),
                    scopesRaw = scopes.distinct().sorted().joinToString(","),
                ),
            )
        return token to rawToken
    }

    suspend fun list(userId: UUID): List<PersonalApiToken> = repository.findByUserIdAndRevokedAtIsNull(userId)

    suspend fun revoke(
        userId: UUID,
        tokenId: UUID,
    ) {
        val token =
            repository.findByIdAndUserId(tokenId, userId)
                ?: throw NotFoundException("Token not found")
        if (token.revokedAt != null) throw ForbiddenException("Token already revoked")
        repository.save(token.copy(revokedAt = Instant.now()))
    }

    /**
     * Resolves a raw bearer token to the matching [PersonalApiToken] if it is active.
     * Updates [PersonalApiToken.lastUsedAt] on each successful resolution.
     * Returns null for unknown or revoked tokens (caller treats both as unauthenticated).
     */
    suspend fun resolve(rawToken: String): PersonalApiToken? {
        val hash = tokenSecretService.hash(rawToken)
        val token = repository.findByTokenHash(hash)
        return when {
            token == null || token.revokedAt != null -> null
            else -> repository.save(token.copy(lastUsedAt = Instant.now()))
        }
    }

    private fun buildRawToken(): String =
        tokenSecretService.generateHexToken(RAW_TOKEN_RANDOM_BYTES, prefix = "satzwerk_")
}

private fun validateScopes(scopes: List<String>) {
    if (scopes.isEmpty()) throw BadRequestException("At least one scope is required")
    val invalid = scopes.filterNot { it in TokenScope.all }
    if (invalid.isNotEmpty()) throw BadRequestException("Unknown scopes: ${invalid.joinToString()}")
}
