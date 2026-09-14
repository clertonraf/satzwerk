package com.satzwerk.cache

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.ReactiveValueOperations
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration

class RedisJsonCacheServiceTest {
    private val objectMapper = jacksonObjectMapper()
    private val meterRegistry = SimpleMeterRegistry()
    private val valueOperations = mock<ReactiveValueOperations<String, String>>()
    private val redisTemplate =
        mock<ReactiveStringRedisTemplate> {
            on { opsForValue() } doReturn valueOperations
        }

    @Test
    fun `get returns cached value and records hit`(): Unit =
        runBlocking {
            val key = "analytics:streak:user-1"
            val expected = StreakSnapshot(currentStreak = 3, longestStreak = 5)
            val json = objectMapper.writeValueAsString(expected)
            val service = RedisJsonCacheService(redisTemplate, objectMapper, meterRegistry, enabled = true)

            whenever(valueOperations.get(key)).thenReturn(Mono.just(json))

            val actual = service.get<StreakSnapshot>(cacheName = "analytics-streak", key = key)

            assertEquals(expected, actual)
            assertEquals(1.0, counter("analytics-streak", "hit"))
            assertEquals(0.0, counter("analytics-streak", "miss"))
        }

    @Test
    fun `get returns null on cache miss and records miss`(): Unit =
        runBlocking {
            val key = "analytics:streak:user-1"
            val service = RedisJsonCacheService(redisTemplate, objectMapper, meterRegistry, enabled = true)

            whenever(valueOperations.get(key)).thenReturn(Mono.empty())

            val actual = service.get<StreakSnapshot>(cacheName = "analytics-streak", key = key)

            assertNull(actual)
            assertEquals(0.0, counter("analytics-streak", "hit"))
            assertEquals(1.0, counter("analytics-streak", "miss"))
        }

    @Test
    fun `set serializes value with ttl`(): Unit =
        runBlocking {
            val key = "workouts:exercises:list:user-1:all"
            val value = listOf(ExerciseSnapshot(name = "Bench Press"))
            val ttl = Duration.ofMinutes(10)
            val service = RedisJsonCacheService(redisTemplate, objectMapper, meterRegistry, enabled = true)

            whenever(valueOperations.set(eq(key), any(), eq(ttl))).thenReturn(Mono.just(true))

            service.set(key = key, value = value, ttl = ttl)

            verify(valueOperations).set(eq(key), eq(objectMapper.writeValueAsString(value)), eq(ttl))
        }

    @Test
    fun `deleteByPattern removes matching keys`(): Unit =
        runBlocking {
            val pattern = "workouts:exercises:list:user-1:*"
            val keys = listOf("workouts:exercises:list:user-1:all", "workouts:exercises:list:user-1:CHEST")
            val service = RedisJsonCacheService(redisTemplate, objectMapper, meterRegistry, enabled = true)

            whenever(redisTemplate.scan(any())).thenReturn(Flux.fromIterable(keys))
            whenever(redisTemplate.delete(any<Flux<String>>())).thenReturn(Mono.just(2))

            val deleted = service.deleteByPattern(pattern)

            assertEquals(2L, deleted)
            verify(redisTemplate).delete(any<Flux<String>>())
        }

    @Test
    fun `deleteByPattern skips delete when no keys match`(): Unit =
        runBlocking {
            val pattern = "workouts:exercises:list:user-1:*"
            val service = RedisJsonCacheService(redisTemplate, objectMapper, meterRegistry, enabled = true)

            whenever(redisTemplate.scan(any())).thenReturn(Flux.empty())

            val deleted = service.deleteByPattern(pattern)

            assertEquals(0L, deleted)
            verify(redisTemplate, never()).delete(any<Flux<String>>())
        }

    @Test
    fun `get returns null and skips redis when cache is disabled`(): Unit =
        runBlocking {
            val service = RedisJsonCacheService(redisTemplate, objectMapper, meterRegistry, enabled = false)

            val actual =
                service.get<StreakSnapshot>(
                    cacheName = "analytics-streak",
                    key = "analytics:streak:user-1",
                )

            assertNull(actual)
            verify(valueOperations, never()).get(any())
        }

    @Test
    fun `get falls back to miss when redis read fails`(): Unit =
        runBlocking {
            whenever(valueOperations.get("analytics:streak:user-1"))
                .thenReturn(Mono.error(IllegalStateException("redis down")))
            val service = RedisJsonCacheService(redisTemplate, objectMapper, meterRegistry, enabled = true)

            val actual = service.get<StreakSnapshot>(cacheName = "analytics-streak", key = "analytics:streak:user-1")

            assertNull(actual)
            assertEquals(1.0, counter("analytics-streak", "miss"))
        }

    @Test
    fun `set swallows redis write failures`(): Unit =
        runBlocking {
            whenever(valueOperations.set(eq("workouts:exercises:list:user-1:all"), any(), eq(Duration.ofMinutes(10))))
                .thenReturn(Mono.error(IllegalStateException("redis down")))
            val service = RedisJsonCacheService(redisTemplate, objectMapper, meterRegistry, enabled = true)

            service.set(
                key = "workouts:exercises:list:user-1:all",
                value = listOf(ExerciseSnapshot(name = "Bench Press")),
                ttl = Duration.ofMinutes(10),
            )
        }

    @Test
    fun `delete swallows redis delete failures and returns false`(): Unit =
        runBlocking {
            whenever(redisTemplate.delete("analytics:streak:user-1"))
                .thenReturn(Mono.error(IllegalStateException("redis down")))
            val service = RedisJsonCacheService(redisTemplate, objectMapper, meterRegistry, enabled = true)

            val deleted = service.delete("analytics:streak:user-1")

            assertEquals(false, deleted)
        }

    private fun counter(
        cacheName: String,
        result: String,
    ): Double =
        meterRegistry
            .find("satzwerk.cache.requests")
            .tags("cache", cacheName, "result", result)
            .counter()
            ?.count() ?: 0.0
}

private data class StreakSnapshot(
    val currentStreak: Int,
    val longestStreak: Int,
)

private data class ExerciseSnapshot(
    val name: String,
)
