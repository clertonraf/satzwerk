package com.satzwerk.cache

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.ScanOptions
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

private const val CACHE_REQUESTS_METER = "satzwerk.cache.requests"
private const val SCAN_BATCH_SIZE = 1_000L
private val REPAIRED_VERSION_SEQUENCE = AtomicLong(System.currentTimeMillis())

@Service
class RedisJsonCacheService(
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val meterRegistry: MeterRegistry,
    @Value("\${satzwerk.cache.enabled:false}") private val enabled: Boolean,
) {
    private val logger = LoggerFactory.getLogger(RedisJsonCacheService::class.java)

    suspend fun <T> get(
        cacheName: String,
        key: String,
        typeReference: TypeReference<T>,
    ): T? =
        if (!enabled) {
            null
        } else {
            offloadRedisCacheWork {
                val payload =
                    redisResult { redisTemplate.opsForValue().get(key).awaitFirstOrNull() }
                        .onFailure { error ->
                            logger.warn("Redis cache read failed for key={}", key, error)
                        }.getOrNull()

                if (payload == null) {
                    cacheCounter(cacheName, "miss").increment()
                    return@offloadRedisCacheWork null
                }

                val decodedResult = runCatching { objectMapper.readValue(payload, typeReference) as T }
                decodedResult.exceptionOrNull()?.let { error ->
                    logger.warn("Redis cache decode failed for key={}", key, error)
                    delete(key)
                }
                val decoded = decodedResult.getOrNull()

                if (decoded == null) {
                    if (decodedResult.isSuccess) {
                        delete(key)
                    }
                    cacheCounter(cacheName, "miss").increment()
                    null
                } else {
                    cacheCounter(cacheName, "hit").increment()
                    decoded
                }
            }
        }

    suspend fun getLong(key: String): Long =
        if (!enabled) {
            0L
        } else {
            offloadRedisCacheWork {
                redisResult { redisTemplate.opsForValue().get(key).awaitFirstOrNull() }
                    .onFailure { error ->
                        logger.warn("Redis cache read failed for key={}", key, error)
                    }.getOrNull()
                    ?.let { payload ->
                        runCatching { payload.toLong() }
                            .onFailure { error ->
                                logger.warn("Redis cache version decode failed for key={}", key, error)
                                delete(key)
                            }.getOrElse {
                                repairVersionKey(key)
                            }
                    } ?: 0L
            }
        }

    suspend fun increment(key: String): Long? {
        if (!enabled) {
            return 0L
        }
        return offloadRedisCacheWork {
            redisResult { redisTemplate.opsForValue().increment(key).awaitSingle() }
                .onFailure { error ->
                    logger.warn("Redis cache increment failed for key={}", key, error)
                }.getOrNull()
        }
    }

    suspend fun set(
        key: String,
        value: Any,
        ttl: Duration,
    ) {
        if (!enabled) {
            return
        }
        offloadRedisCacheWork {
            redisResult {
                val payload = objectMapper.writeValueAsString(value)
                redisTemplate.opsForValue().set(key, payload, ttl).awaitSingle()
            }.onFailure { error ->
                logger.warn("Redis cache write failed for key={}", key, error)
            }
        }
    }

    suspend fun delete(key: String): Boolean {
        if (!enabled) {
            return false
        }
        return offloadRedisCacheWork {
            redisResult { (redisTemplate.delete(key).awaitFirstOrNull() ?: 0L) > 0 }
                .onFailure { error ->
                    logger.warn("Redis cache delete failed for key={}", key, error)
                }.getOrDefault(false)
        }
    }

    suspend fun deleteByPattern(pattern: String): Boolean {
        if (!enabled) {
            return false
        }
        return offloadRedisCacheWork {
            val keys =
                redisResult {
                    redisTemplate
                        .scan(
                            ScanOptions.scanOptions()
                                .match(pattern)
                                .count(SCAN_BATCH_SIZE)
                                .build(),
                        ).collectList()
                        .awaitSingle()
                }.onFailure { error ->
                    logger.warn("Redis cache scan failed for pattern={}", pattern, error)
                }.getOrNull() ?: return@offloadRedisCacheWork false

            if (keys.isEmpty()) {
                return@offloadRedisCacheWork true
            }

            redisResult { (redisTemplate.delete(Flux.fromIterable(keys)).awaitFirstOrNull() ?: 0L) >= 0 }
                .onFailure { error ->
                    logger.warn("Redis cache delete failed for pattern={}", pattern, error)
                }.getOrDefault(false)
        }
    }

    suspend fun writeFreshVersion(key: String): Long? {
        if (!enabled) {
            return 0L
        }

        return offloadRedisCacheWork {
            val freshVersion = nextFreshVersion()
            if (persistCacheVersion(redisTemplate, logger, key, freshVersion)) {
                freshVersion
            } else {
                logger.warn("Redis cache version repair could not persist fresh version for key={}", key)
                null
            }
        }
    }

    private fun cacheCounter(
        cacheName: String,
        result: String,
    ) = meterRegistry.counter(CACHE_REQUESTS_METER, "cache", cacheName, "result", result)

    private suspend fun repairVersionKey(key: String): Long {
        val freshVersion = nextFreshVersion()
        if (!persistCacheVersion(redisTemplate, logger, key, freshVersion)) {
            logger.warn("Redis cache version repair could not persist fresh version for key={}", key)
        }
        return freshVersion
    }
}

suspend inline fun <reified T> RedisJsonCacheService.get(
    cacheName: String,
    key: String,
): T? = get(cacheName, key, object : TypeReference<T>() {})

// kotlinx-coroutines-reactor resumes suspended Redis calls on Lettuce I/O threads by default.
// Move cache networking + JSON work off that event loop so concurrent cache hits do not queue there.
private suspend fun <T> offloadRedisCacheWork(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

private suspend fun <T> redisResult(block: suspend () -> T): Result<T> =
    runCatching { block() }
        .onFailure { error ->
            if (error is CancellationException) {
                throw error
            }
        }

private fun nextFreshVersion(): Long = REPAIRED_VERSION_SEQUENCE.incrementAndGet()

private suspend fun persistCacheVersion(
    redisTemplate: ReactiveStringRedisTemplate,
    logger: Logger,
    key: String,
    version: Long,
): Boolean =
    redisResult {
        redisTemplate.opsForValue().set(key, version.toString()).awaitSingle()
    }.onFailure { error ->
        logger.warn("Redis cache version repair failed for key={}", key, error)
    }.getOrDefault(false)
