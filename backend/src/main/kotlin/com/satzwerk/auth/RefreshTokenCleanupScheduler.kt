package com.satzwerk.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

private const val SCHEDULER_SECONDS_PER_DAY = 86400L

@Component
class RefreshTokenCleanupScheduler(
    private val refreshTokenRepository: RefreshTokenRepository,
) {
    private val logger = LoggerFactory.getLogger(RefreshTokenCleanupScheduler::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Runs daily at 03:00. Best-effort: failures are logged but never propagated.
    @Scheduled(cron = "0 0 3 * * *")
    fun runCleanup() {
        scope.launch {
            val cutoff = Instant.now().minusSeconds(REFRESH_TOKEN_RETENTION_DAYS * SCHEDULER_SECONDS_PER_DAY)
            try {
                refreshTokenRepository.deleteByExpiresAtBefore(cutoff)
                refreshTokenRepository.deleteByRevokedAtIsNotNullAndRevokedAtBefore(cutoff)
            } catch (e: DataAccessException) {
                logger.warn("Scheduled refresh-token cleanup failed", e)
            }
        }
    }
}
