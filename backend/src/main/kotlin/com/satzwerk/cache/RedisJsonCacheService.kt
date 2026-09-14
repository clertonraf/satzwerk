package com.satzwerk.cache

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

private const val CACHE_REQUESTS_METER = "satzwerk.cache.requests"

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
            offloadCacheWork {
                val payload =
                    runCatching { redisTemplate.opsForValue().get(key).awaitFirstOrNull() }
                        .onFailure { error ->
                            logger.warn("Redis cache read failed for key={}", key, error)
                        }.getOrNull()

                if (payload == null) {
                    cacheCounter(cacheName, "miss").increment()
                    return@offloadCacheWork null
                }

                val decoded =
                    runCatching { objectMapper.readValue(payload, typeReference) as T }
                        .onFailure { error ->
                            logger.warn("Redis cache decode failed for key={}", key, error)
                            delete(key)
                        }.getOrNull()

                if (decoded == null) {
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
            offloadCacheWork {
                runCatching { redisTemplate.opsForValue().get(key).awaitFirstOrNull() }
                    .onFailure { error ->
                        logger.warn("Redis cache read failed for key={}", key, error)
                    }.getOrNull()
                    ?.let { payload ->
                        runCatching { payload.toLong() }
                            .onFailure { error ->
                                logger.warn("Redis cache version decode failed for key={}", key, error)
                                delete(key)
                            }.getOrDefault(0L)
                    } ?: 0L
            }
        }

    suspend fun increment(key: String): Long? {
        if (!enabled) {
            return 0L
        }
        return offloadCacheWork {
            runCatching { redisTemplate.opsForValue().increment(key).awaitSingle() }
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
        offloadCacheWork {
            runCatching {
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
        return offloadCacheWork {
            runCatching { (redisTemplate.delete(key).awaitFirstOrNull() ?: 0L) > 0 }
                .onFailure { error ->
                    logger.warn("Redis cache delete failed for key={}", key, error)
                }.getOrDefault(false)
        }
    }

    private fun cacheCounter(
        cacheName: String,
        result: String,
    ) = meterRegistry.counter(CACHE_REQUESTS_METER, "cache", cacheName, "result", result)

    // kotlinx-coroutines-reactor resumes suspended Redis calls on Lettuce I/O threads by default.
    // Move cache networking + JSON work off that event loop so concurrent cache hits do not queue there.
    private suspend fun <T> offloadCacheWork(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }
}

suspend inline fun <reified T> RedisJsonCacheService.get(
    cacheName: String,
    key: String,
): T? = get(cacheName, key, object : TypeReference<T>() {})
