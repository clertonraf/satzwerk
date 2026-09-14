package com.satzwerk.analytics

import com.satzwerk.PostgresTestContainer
import com.satzwerk.auth.AuthResponse
import com.satzwerk.sessions.SetLogResponse
import com.satzwerk.sessions.WorkoutSessionResponse
import com.satzwerk.users.UserRepository
import com.satzwerk.workouts.ExerciseResponse
import com.satzwerk.workouts.WorkoutGroupResponse
import com.satzwerk.workouts.WorkoutPlanResponse
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@AutoConfigureObservability
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AnalyticsCacheIntegrationTest : PostgresTestContainer() {
    @Autowired
    lateinit var client: WebTestClient

    @Autowired
    lateinit var meterRegistry: MeterRegistry

    @Autowired
    lateinit var analyticsReadCache: AnalyticsReadCache

    @Autowired
    lateinit var userRepository: UserRepository

    private lateinit var authToken: String
    private lateinit var userEmail: String
    private lateinit var workoutPlanId: UUID
    private lateinit var workoutGroupId: UUID
    private lateinit var exerciseId: UUID

    @BeforeEach
    fun setup() {
        userEmail = "analytics-cache-${UUID.randomUUID()}@test.com"
        authToken = registerAndLogin(userEmail, "password123", "Analytics User")
        exerciseId = createExercise("Bench Press", "CHEST")
        workoutPlanId = createPlan("Push Pull Legs")
        workoutGroupId = createGroup(workoutPlanId, "Push Day", exerciseId)
    }

    @Test
    fun `heatmap and streak cache invalidate after new set log`() {
        val today = LocalDate.now(ZoneOffset.UTC)
        val heatmapMissBefore = cacheCounter("analytics-heatmap", "miss")
        val heatmapHitBefore = cacheCounter("analytics-heatmap", "hit")
        val streakMissBefore = cacheCounter("analytics-streak", "miss")
        val streakHitBefore = cacheCounter("analytics-streak", "hit")

        getHeatmap(today, expectedCount = 0)
        getHeatmap(today, expectedCount = 0)
        getStreak(expectedCurrent = 0, expectedLongest = 0)
        getStreak(expectedCurrent = 0, expectedLongest = 0)

        assertEquals(1.0, cacheCounter("analytics-heatmap", "miss") - heatmapMissBefore)
        assertEquals(1.0, cacheCounter("analytics-heatmap", "hit") - heatmapHitBefore)
        assertEquals(1.0, cacheCounter("analytics-streak", "miss") - streakMissBefore)
        assertEquals(1.0, cacheCounter("analytics-streak", "hit") - streakHitBefore)

        val session = startSession(workoutGroupId)
        addSetLog(session.id, exerciseId, 1)

        getHeatmap(today, expectedCount = 1)
        getStreak(expectedCurrent = 1, expectedLongest = 1)

        assertEquals(2.0, cacheCounter("analytics-heatmap", "miss") - heatmapMissBefore)
        assertEquals(1.0, cacheCounter("analytics-heatmap", "hit") - heatmapHitBefore)
        assertEquals(2.0, cacheCounter("analytics-streak", "miss") - streakMissBefore)
        assertEquals(1.0, cacheCounter("analytics-streak", "hit") - streakHitBefore)
    }

    @Test
    fun `analytics cache invalidates after set log update`() {
        val today = LocalDate.now(ZoneOffset.UTC)
        val session = startSession(workoutGroupId)
        val setLog = addSetLog(session.id, exerciseId, 1)
        val heatmapMissBefore = cacheCounter("analytics-heatmap", "miss")
        val heatmapHitBefore = cacheCounter("analytics-heatmap", "hit")
        val streakMissBefore = cacheCounter("analytics-streak", "miss")
        val streakHitBefore = cacheCounter("analytics-streak", "hit")

        getHeatmap(today, expectedCount = 1)
        getHeatmap(today, expectedCount = 1)
        getStreak(expectedCurrent = 1, expectedLongest = 1)
        getStreak(expectedCurrent = 1, expectedLongest = 1)

        updateSetLog(
            session.id,
            setLog.id,
            AnalyticsCacheSetLogFixture(weight = BigDecimal("82.5"), reps = 6),
        )

        getHeatmap(today, expectedCount = 1)
        getStreak(expectedCurrent = 1, expectedLongest = 1)

        assertEquals(2.0, cacheCounter("analytics-heatmap", "miss") - heatmapMissBefore)
        assertEquals(1.0, cacheCounter("analytics-heatmap", "hit") - heatmapHitBefore)
        assertEquals(2.0, cacheCounter("analytics-streak", "miss") - streakMissBefore)
        assertEquals(1.0, cacheCounter("analytics-streak", "hit") - streakHitBefore)
    }

    @Test
    fun `analytics cache invalidates after set log delete`() {
        val today = LocalDate.now(ZoneOffset.UTC)
        val session = startSession(workoutGroupId)
        val setLog = addSetLog(session.id, exerciseId, 1)
        val heatmapMissBefore = cacheCounter("analytics-heatmap", "miss")
        val heatmapHitBefore = cacheCounter("analytics-heatmap", "hit")
        val streakMissBefore = cacheCounter("analytics-streak", "miss")
        val streakHitBefore = cacheCounter("analytics-streak", "hit")

        getHeatmap(today, expectedCount = 1)
        getHeatmap(today, expectedCount = 1)
        getStreak(expectedCurrent = 1, expectedLongest = 1)
        getStreak(expectedCurrent = 1, expectedLongest = 1)

        deleteSetLog(session.id, setLog.id)

        getHeatmap(today, expectedCount = 0)
        getStreak(expectedCurrent = 0, expectedLongest = 0)

        assertEquals(2.0, cacheCounter("analytics-heatmap", "miss") - heatmapMissBefore)
        assertEquals(1.0, cacheCounter("analytics-heatmap", "hit") - heatmapHitBefore)
        assertEquals(2.0, cacheCounter("analytics-streak", "miss") - streakMissBefore)
        assertEquals(1.0, cacheCounter("analytics-streak", "hit") - streakHitBefore)
    }

    @Test
    fun `analytics cache invalidates after WorkoutGroup delete cascade`() {
        val today = LocalDate.now(ZoneOffset.UTC)
        val session = startSession(workoutGroupId)
        addSetLog(session.id, exerciseId, 1)
        val heatmapMissBefore = cacheCounter("analytics-heatmap", "miss")
        val heatmapHitBefore = cacheCounter("analytics-heatmap", "hit")
        val streakMissBefore = cacheCounter("analytics-streak", "miss")
        val streakHitBefore = cacheCounter("analytics-streak", "hit")

        getHeatmap(today, expectedCount = 1)
        getHeatmap(today, expectedCount = 1)
        getStreak(expectedCurrent = 1, expectedLongest = 1)
        getStreak(expectedCurrent = 1, expectedLongest = 1)

        deleteGroup(workoutPlanId, workoutGroupId)

        getHeatmap(today, expectedCount = 0)
        getStreak(expectedCurrent = 0, expectedLongest = 0)

        assertEquals(2.0, cacheCounter("analytics-heatmap", "miss") - heatmapMissBefore)
        assertEquals(1.0, cacheCounter("analytics-heatmap", "hit") - heatmapHitBefore)
        assertEquals(2.0, cacheCounter("analytics-streak", "miss") - streakMissBefore)
        assertEquals(1.0, cacheCounter("analytics-streak", "hit") - streakHitBefore)
    }

    @Test
    fun `analytics cache invalidates after WorkoutPlan delete cascade`() {
        val today = LocalDate.now(ZoneOffset.UTC)
        val session = startSession(workoutGroupId)
        addSetLog(session.id, exerciseId, 1)
        val heatmapMissBefore = cacheCounter("analytics-heatmap", "miss")
        val heatmapHitBefore = cacheCounter("analytics-heatmap", "hit")
        val streakMissBefore = cacheCounter("analytics-streak", "miss")
        val streakHitBefore = cacheCounter("analytics-streak", "hit")

        getHeatmap(today, expectedCount = 1)
        getHeatmap(today, expectedCount = 1)
        getStreak(expectedCurrent = 1, expectedLongest = 1)
        getStreak(expectedCurrent = 1, expectedLongest = 1)

        deletePlan(workoutPlanId)

        getHeatmap(today, expectedCount = 0)
        getStreak(expectedCurrent = 0, expectedLongest = 0)

        assertEquals(2.0, cacheCounter("analytics-heatmap", "miss") - heatmapMissBefore)
        assertEquals(1.0, cacheCounter("analytics-heatmap", "hit") - heatmapHitBefore)
        assertEquals(2.0, cacheCounter("analytics-streak", "miss") - streakMissBefore)
        assertEquals(1.0, cacheCounter("analytics-streak", "hit") - streakHitBefore)
    }

    @Test
    fun `analytics cache ignores stale write from older version after invalidation`(): Unit =
        runBlocking {
            val userId = requireNotNull(userRepository.findByEmail(userEmail)?.id)
            val today = LocalDate.now(ZoneOffset.UTC)
            val staleHeatmapLookup = analyticsReadCache.lookupHeatmap(userId, today, today)
            val staleStreakLookup = analyticsReadCache.lookupStreak(userId)

            analyticsReadCache.invalidateUser(userId)
            analyticsReadCache.putHeatmap(
                userId = userId,
                from = today,
                to = today,
                version = staleHeatmapLookup.version,
                entries = listOf(HeatmapEntry(today, 99, 4)),
            )
            analyticsReadCache.putStreak(
                userId = userId,
                version = staleStreakLookup.version,
                streak = StreakResponse(currentStreak = 9, longestStreak = 9),
            )

            val freshHeatmapLookup = analyticsReadCache.lookupHeatmap(userId, today, today)
            val freshStreakLookup = analyticsReadCache.lookupStreak(userId)

            assertEquals(staleHeatmapLookup.version + 1, freshHeatmapLookup.version)
            assertEquals(staleStreakLookup.version + 1, freshStreakLookup.version)
            assertNull(freshHeatmapLookup.value)
            assertNull(freshStreakLookup.value)
        }

    private fun getHeatmap(
        day: LocalDate,
        expectedCount: Int,
    ) {
        client
            .get()
            .uri("/api/analytics/heatmap?from=$day&to=$day")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].count").isEqualTo(expectedCount)
    }

    private fun getStreak(
        expectedCurrent: Int,
        expectedLongest: Int,
    ) {
        client
            .get()
            .uri("/api/analytics/streak")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.currentStreak").isEqualTo(expectedCurrent)
            .jsonPath("$.longestStreak").isEqualTo(expectedLongest)
    }

    private fun startSession(groupId: UUID): WorkoutSessionResponse =
        client
            .post()
            .uri("/api/sessions")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("workoutGroupId" to groupId))
            .exchange()
            .expectStatus().isCreated
            .expectBody(WorkoutSessionResponse::class.java)
            .returnResult()
            .responseBody!!

    private fun addSetLog(
        sessionId: UUID,
        targetExerciseId: UUID,
        setNumber: Int,
        setLog: AnalyticsCacheSetLogFixture = AnalyticsCacheSetLogFixture(),
    ): SetLogResponse =
        client
            .post()
            .uri("/api/sessions/$sessionId/set-logs")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "exerciseId" to targetExerciseId,
                    "setNumber" to setNumber,
                    "weight" to setLog.weight,
                    "reps" to setLog.reps,
                ),
            ).exchange()
            .expectStatus().isCreated
            .expectBody(SetLogResponse::class.java)
            .returnResult()
            .responseBody!!

    private fun updateSetLog(
        sessionId: UUID,
        setLogId: UUID,
        setLog: AnalyticsCacheSetLogFixture,
    ) {
        client
            .patch()
            .uri("/api/sessions/$sessionId/set-logs/$setLogId")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "weight" to setLog.weight,
                    "reps" to setLog.reps,
                ),
            ).exchange()
            .expectStatus().isOk
    }

    private fun deleteSetLog(
        sessionId: UUID,
        setLogId: UUID,
    ) {
        client
            .delete()
            .uri("/api/sessions/$sessionId/set-logs/$setLogId")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isNoContent
    }

    private fun deleteGroup(
        planId: UUID,
        groupId: UUID,
    ) {
        client
            .delete()
            .uri("/api/plans/$planId/groups/$groupId")
            .headers { it.setBearerAuth(authToken) }
            .exchange()
            .expectStatus().isNoContent
    }

    private fun deletePlan(planId: UUID) {
        client
            .delete()
            .uri("/api/plans/$planId")
            .headers { it.setBearerAuth(authToken) }
            .exchange()
            .expectStatus().isNoContent
    }

    private fun createExercise(
        name: String,
        muscleGroup: String,
    ): UUID =
        client
            .post()
            .uri("/api/exercises")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "name" to name,
                    "muscleGroup" to muscleGroup,
                ),
            ).exchange()
            .expectStatus().isCreated
            .expectBody(ExerciseResponse::class.java)
            .returnResult()
            .responseBody!!
            .id

    private fun createPlan(name: String): UUID {
        val response =
            client
                .post()
                .uri("/api/plans")
                .header("Authorization", "Bearer $authToken")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapOf("name" to name))
                .exchange()
                .expectStatus().isCreated
                .expectBody(WorkoutPlanResponse::class.java)
                .returnResult()
                .responseBody!!

        client
            .post()
            .uri("/api/plans/${response.id}/activate")
            .header("Authorization", "Bearer $authToken")
            .exchange()
            .expectStatus().isNoContent
        return response.id
    }

    private fun createGroup(
        planId: UUID,
        title: String,
        targetExerciseId: UUID,
    ): UUID {
        val groupId =
            client
                .post()
                .uri("/api/plans/$planId/groups")
                .header("Authorization", "Bearer $authToken")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapOf("title" to title))
                .exchange()
                .expectStatus().isCreated
                .expectBody(WorkoutGroupResponse::class.java)
                .returnResult()
                .responseBody!!
                .id

        client
            .post()
            .uri("/api/plans/$planId/groups/$groupId/exercises")
            .header("Authorization", "Bearer $authToken")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "exerciseId" to targetExerciseId,
                    "sets" to 4,
                    "reps" to 8,
                ),
            ).exchange()
            .expectStatus().isCreated

        return groupId
    }

    private fun registerAndLogin(
        email: String,
        password: String,
        displayName: String,
    ): String =
        client
            .post()
            .uri("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "email" to email,
                    "password" to password,
                    "displayName" to displayName,
                ),
            ).exchange()
            .expectStatus().isCreated
            .expectBody(AuthResponse::class.java)
            .returnResult()
            .responseBody!!
            .accessToken

    private fun cacheCounter(
        cacheName: String,
        result: String,
    ): Double =
        meterRegistry
            .find("satzwerk.cache.requests")
            .tags("cache", cacheName, "result", result)
            .counter()
            ?.count() ?: 0.0
}

private data class AnalyticsCacheSetLogFixture(
    val weight: BigDecimal = BigDecimal("80.0"),
    val reps: Int = 5,
)
