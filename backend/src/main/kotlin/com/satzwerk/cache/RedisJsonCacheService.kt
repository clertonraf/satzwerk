package com.satzwerk.cache

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.ScanOptions
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import java.time.Duration

private const val CACHE_REQUESTS_METER = "satzwerk.cache.requests"
private const val CACHE_SCAN_BATCH_SIZE: Long = 100

@Service
class RedisJsonCacheService(
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val meterRegistry: MeterRegistry,
    @Value("\${satzwerk.cache.enabled:true}") private val enabled: Boolean,
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
                runCatching { redisTemplate.opsForValue().get(key).awaitFirstOrNull() }
                    .onFailure { error ->
                        logger.warn("Redis cache read failed for key={}", key, error)
                    }.getOrNull()
                    ?.let { payload ->
                        cacheCounter(cacheName, "hit").increment()
                        objectMapper.readValue(payload, typeReference) as T
                    } ?: run {
                    if (enabled) {
                        cacheCounter(cacheName, "miss").increment()
                    }
                    null
                }
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

    suspend fun deleteByPattern(pattern: String): Long {
        if (!enabled) {
            return 0L
        }
        return offloadCacheWork {
            val keys =
                runCatching {
                    redisTemplate.scan(
                        ScanOptions.scanOptions()
                            .match(pattern)
                            .count(CACHE_SCAN_BATCH_SIZE)
                            .build(),
                    ).asFlow()
                        .toList()
                }.onFailure { error ->
                    logger.warn("Redis cache scan failed for pattern={}", pattern, error)
                }.getOrDefault(emptyList())

            if (keys.isEmpty()) {
                0L
            } else {
                runCatching { redisTemplate.delete(Flux.fromIterable(keys)).awaitSingle() }
                    .onFailure { error ->
                        logger.warn("Redis cache bulk delete failed for pattern={}", pattern, error)
                    }.getOrDefault(0L)
            }
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
