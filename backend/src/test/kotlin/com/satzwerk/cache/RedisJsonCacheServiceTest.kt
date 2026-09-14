package com.satzwerk.cache

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.ReactiveValueOperations
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
    fun `get evicts malformed cached payload and records miss`(): Unit =
        runBlocking {
            val key = "analytics:streak:user-1"
            val service = RedisJsonCacheService(redisTemplate, objectMapper, meterRegistry, enabled = true)

            whenever(valueOperations.get(key)).thenReturn(Mono.just("{not-json"))
            whenever(redisTemplate.delete(key)).thenReturn(Mono.just(1))

            val actual = service.get<StreakSnapshot>(cacheName = "analytics-streak", key = key)

            assertNull(actual)
            assertEquals(1.0, counter("analytics-streak", "miss"))
            verify(redisTemplate).delete(key)
        }

    @Test
    fun `get evicts literal null payload and records miss`(): Unit =
        runBlocking {
            val key = "analytics:streak:user-1"
            val service = RedisJsonCacheService(redisTemplate, objectMapper, meterRegistry, enabled = true)

            whenever(valueOperations.get(key)).thenReturn(Mono.just("null"))
            whenever(redisTemplate.delete(key)).thenReturn(Mono.just(1))

            val actual = service.get<StreakSnapshot>(cacheName = "analytics-streak", key = key)

            assertNull(actual)
            assertEquals(1.0, counter("analytics-streak", "miss"))
            verify(redisTemplate).delete(key)
        }

    @Test
    fun `version reads default to zero and increment returns new value`(): Unit =
        runBlocking {
            val service = RedisJsonCacheService(redisTemplate, objectMapper, meterRegistry, enabled = true)
            whenever(valueOperations.get("analytics:version:user-1")).thenReturn(Mono.empty())
            whenever(valueOperations.increment("analytics:version:user-1")).thenReturn(Mono.just(1))

            val before = service.getLong("analytics:version:user-1")
            val after = service.increment("analytics:version:user-1")

            assertEquals(0L, before)
            assertEquals(1L, after)
        }

    @Test
    fun `malformed version payload repairs to a fresh generation instead of reusing zero`(): Unit =
        runBlocking {
            val key = "analytics:version:user-1"
            val persistedVersion = argumentCaptor<String>()
            val service = RedisJsonCacheService(redisTemplate, objectMapper, meterRegistry, enabled = true)
            whenever(valueOperations.get(key)).thenReturn(Mono.just("not-a-long"))
            whenever(redisTemplate.delete(key)).thenReturn(Mono.just(1))
            whenever(valueOperations.set(eq(key), persistedVersion.capture())).thenReturn(Mono.just(true))

            val repairedVersion = service.getLong(key)

            assertTrue(repairedVersion > 0L)
            assertEquals(repairedVersion.toString(), persistedVersion.firstValue)
            verify(redisTemplate).delete(key)
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
    fun `get propagates cancellation`() {
        whenever(valueOperations.get("analytics:streak:user-1"))
            .thenReturn(Mono.error(CancellationException("request cancelled")))
        val service = RedisJsonCacheService(redisTemplate, objectMapper, meterRegistry, enabled = true)

        assertThrows<CancellationException> {
            runBlocking {
                service.get<StreakSnapshot>(cacheName = "analytics-streak", key = "analytics:streak:user-1")
            }
        }
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
    fun `set propagates cancellation`() {
        whenever(valueOperations.set(eq("workouts:exercises:list:user-1:all"), any(), eq(Duration.ofMinutes(10))))
            .thenReturn(Mono.error(CancellationException("request cancelled")))
        val service = RedisJsonCacheService(redisTemplate, objectMapper, meterRegistry, enabled = true)

        assertThrows<CancellationException> {
            runBlocking {
                service.set(
                    key = "workouts:exercises:list:user-1:all",
                    value = listOf(ExerciseSnapshot(name = "Bench Press")),
                    ttl = Duration.ofMinutes(10),
                )
            }
        }
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

    @Test
    fun `delete propagates cancellation`() {
        whenever(redisTemplate.delete("analytics:streak:user-1"))
            .thenReturn(Mono.error(CancellationException("request cancelled")))
        val service = RedisJsonCacheService(redisTemplate, objectMapper, meterRegistry, enabled = true)

        assertThrows<CancellationException> {
            runBlocking {
                service.delete("analytics:streak:user-1")
            }
        }
    }

    @Test
    fun `increment returns null on redis failure`(): Unit =
        runBlocking {
            whenever(valueOperations.increment("analytics:version:user-1"))
                .thenReturn(Mono.error(IllegalStateException("redis down")))
            val service = RedisJsonCacheService(redisTemplate, objectMapper, meterRegistry, enabled = true)

            val updated = service.increment("analytics:version:user-1")

            assertNull(updated)
        }

    @Test
    fun `version reads and increments propagate cancellation`() {
        whenever(valueOperations.get("analytics:version:user-1"))
            .thenReturn(Mono.error(CancellationException("request cancelled")))
        whenever(valueOperations.increment("analytics:version:user-2"))
            .thenReturn(Mono.error(CancellationException("request cancelled")))
        val service = RedisJsonCacheService(redisTemplate, objectMapper, meterRegistry, enabled = true)

        assertThrows<CancellationException> {
            runBlocking {
                service.getLong("analytics:version:user-1")
            }
        }
        assertThrows<CancellationException> {
            runBlocking {
                service.increment("analytics:version:user-2")
            }
        }
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
