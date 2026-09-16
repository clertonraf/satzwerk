package com.satzwerk.workouts

import com.satzwerk.PostgresTestContainer
import com.satzwerk.auth.AuthResponse
import com.satzwerk.users.UserRepository
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
import java.time.Instant
import java.util.UUID

@AutoConfigureObservability
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WorkoutPlanCacheIntegrationTest : PostgresTestContainer() {
    @Autowired
    lateinit var client: WebTestClient

    @Autowired
    lateinit var meterRegistry: MeterRegistry

    @Autowired
    lateinit var workoutPlanReadCache: WorkoutPlanReadCache

    @Autowired
    lateinit var workoutGroupReadCache: WorkoutGroupReadCache

    @Autowired
    lateinit var workoutPlanService: WorkoutPlanService

    @Autowired
    lateinit var userRepository: UserRepository

    private lateinit var authToken: String
    private lateinit var userEmail: String
    private lateinit var exerciseId: UUID

    @BeforeEach
    fun setup() {
        userEmail = "plan-cache-${UUID.randomUUID()}@test.com"
        authToken = registerAndLogin(userEmail, "password123", "Plan Cache User")
        exerciseId = createExercise("Bench Press", "CHEST")
    }

    @Test
    fun `plan list and detail use cache and invalidate after plan writes`() {
        val planId = createPlan("PPL")

        val listMissBefore = cacheCounter("workout-plan-list", "miss")
        val listHitBefore = cacheCounter("workout-plan-list", "hit")
        val detailMissBefore = cacheCounter("workout-plan-detail", "miss")
        val detailHitBefore = cacheCounter("workout-plan-detail", "hit")

        listPlans(expectedCount = 1)
        listPlans(expectedCount = 1)
        getPlanDetail(planId, DetailExpectation(expectedGroups = 0))
        getPlanDetail(planId, DetailExpectation(expectedGroups = 0))

        assertEquals(1.0, cacheCounter("workout-plan-list", "miss") - listMissBefore)
        assertEquals(1.0, cacheCounter("workout-plan-list", "hit") - listHitBefore)
        assertEquals(1.0, cacheCounter("workout-plan-detail", "miss") - detailMissBefore)
        assertEquals(1.0, cacheCounter("workout-plan-detail", "hit") - detailHitBefore)

        updatePlan(planId, "Push Pull Legs")
        listPlans(expectedCount = 1, expectedName = "Push Pull Legs")
        getPlanDetail(planId, DetailExpectation(expectedGroups = 0, expectedName = "Push Pull Legs"))

        assertEquals(2.0, cacheCounter("workout-plan-list", "miss") - listMissBefore)
        assertEquals(1.0, cacheCounter("workout-plan-list", "hit") - listHitBefore)
        assertEquals(2.0, cacheCounter("workout-plan-detail", "miss") - detailMissBefore)
        assertEquals(1.0, cacheCounter("workout-plan-detail", "hit") - detailHitBefore)

        deletePlan(planId)
        listPlans(expectedCount = 0)
    }

    @Test
    fun `plan detail and group lookups invalidate after group writes`(): Unit =
        runBlocking {
            val planId = createPlan("PPL")
            val groupId = createGroup(planId, "Treino A")

            val detailMissBefore = cacheCounter("workout-plan-detail", "miss")
            val detailHitBefore = cacheCounter("workout-plan-detail", "hit")
            val groupMissBefore = cacheCounter("workout-groups", "miss")
            val groupHitBefore = cacheCounter("workout-groups", "hit")

            getPlanDetail(planId, DetailExpectation(expectedGroups = 1))
            getPlanDetail(planId, DetailExpectation(expectedGroups = 1))
            requireGroup(planId, groupId)
            requireGroup(planId, groupId)

            assertEquals(1.0, cacheCounter("workout-plan-detail", "miss") - detailMissBefore)
            assertEquals(1.0, cacheCounter("workout-plan-detail", "hit") - detailHitBefore)
            assertEquals(1.0, cacheCounter("workout-groups", "miss") - groupMissBefore)
            assertEquals(0.0, cacheCounter("workout-groups", "hit") - groupHitBefore)

            updateGroup(planId, groupId, "Treino B")
            getPlanDetail(
                planId,
                DetailExpectation(expectedGroups = 1, expectedGroupTitle = "Treino B"),
            )
            requireGroup(planId, groupId, expectedTitle = "Treino B")

            assertEquals(2.0, cacheCounter("workout-plan-detail", "miss") - detailMissBefore)
            assertEquals(1.0, cacheCounter("workout-plan-detail", "hit") - detailHitBefore)
            assertEquals(2.0, cacheCounter("workout-groups", "miss") - groupMissBefore)
            assertEquals(0.0, cacheCounter("workout-groups", "hit") - groupHitBefore)

            deleteGroup(planId, groupId)
            getPlanDetail(planId, DetailExpectation(expectedGroups = 0))
        }

    @Test
    fun `plan detail invalidates after nested WorkoutExercise writes`() {
        val planId = createPlan("PPL")
        val groupId = createGroup(planId, "Treino A")
        val detailMissBefore = cacheCounter("workout-plan-detail", "miss")
        val detailHitBefore = cacheCounter("workout-plan-detail", "hit")
        val groupMissBefore = cacheCounter("workout-groups", "miss")
        val groupHitBefore = cacheCounter("workout-groups", "hit")

        getPlanDetail(planId, DetailExpectation(expectedGroups = 1))
        getPlanDetail(planId, DetailExpectation(expectedGroups = 1))

        val workoutExerciseId = createWorkoutExercise(planId, groupId, exerciseId)
        getPlanDetail(planId, DetailExpectation(expectedGroups = 1, expectedExerciseCount = 1))
        updateWorkoutExercise(planId, groupId, workoutExerciseId, sets = 5)
        getPlanDetail(
            planId,
            DetailExpectation(expectedGroups = 1, expectedExerciseCount = 1, expectedSets = 5),
        )
        deleteWorkoutExercise(planId, groupId, workoutExerciseId)
        getPlanDetail(planId, DetailExpectation(expectedGroups = 1, expectedExerciseCount = 0))

        assertEquals(4.0, cacheCounter("workout-plan-detail", "miss") - detailMissBefore)
        assertEquals(1.0, cacheCounter("workout-plan-detail", "hit") - detailHitBefore)
        assertEquals(1.0, cacheCounter("workout-groups", "miss") - groupMissBefore)
        assertEquals(3.0, cacheCounter("workout-groups", "hit") - groupHitBefore)
    }

    @Test
    fun `plan detail invalidates after Exercise rename`() {
        val planId = createPlan("PPL")
        val groupId = createGroup(planId, "Treino A")
        createWorkoutExercise(planId, groupId, exerciseId)
        val detailMissBefore = cacheCounter("workout-plan-detail", "miss")
        val detailHitBefore = cacheCounter("workout-plan-detail", "hit")
        val groupMissBefore = cacheCounter("workout-groups", "miss")
        val groupHitBefore = cacheCounter("workout-groups", "hit")

        getPlanDetail(planId, DetailExpectation(expectedGroups = 1))
        getPlanDetail(planId, DetailExpectation(expectedGroups = 1))

        renameExercise(exerciseId, "Paused Bench Press")
        getPlanDetail(
            planId,
            DetailExpectation(
                expectedGroups = 1,
                expectedExerciseCount = 1,
                expectedExerciseName = "Paused Bench Press",
            ),
        )

        assertEquals(2.0, cacheCounter("workout-plan-detail", "miss") - detailMissBefore)
        assertEquals(1.0, cacheCounter("workout-plan-detail", "hit") - detailHitBefore)
        assertEquals(1.0, cacheCounter("workout-groups", "miss") - groupMissBefore)
        assertEquals(1.0, cacheCounter("workout-groups", "hit") - groupHitBefore)
    }

    @Test
    fun `plan list invalidates after activate`() {
        val planA = createPlan("Plan A")
        val planB = createPlan("Plan B")
        val listMissBefore = cacheCounter("workout-plan-list", "miss")
        val listHitBefore = cacheCounter("workout-plan-list", "hit")

        listPlans(expectedCount = 2)
        listPlans(expectedCount = 2)

        activatePlan(planA)
        activatePlan(planB)
        listPlans(expectedCount = 2, expectedActivePlanId = planB)

        assertEquals(2.0, cacheCounter("workout-plan-list", "miss") - listMissBefore)
        assertEquals(1.0, cacheCounter("workout-plan-list", "hit") - listHitBefore)
    }

    @Test
    fun `stale group cache version is ignored after invalidation`(): Unit =
        runBlocking {
            val userId = requireNotNull(userRepository.findByEmail(userEmail)?.id)
            val planId = createPlan("PPL")
            val staleLookup = workoutGroupReadCache.lookup(userId, planId)

            workoutGroupReadCache.invalidatePlan(userId, planId)
            workoutGroupReadCache.put(
                userId = userId,
                planId = planId,
                version = staleLookup.version,
                groups =
                    listOf(
                        WorkoutGroup(
                            id = UUID.randomUUID(),
                            workoutPlanId = planId,
                            title = "Stale Group",
                            orderIndex = 0,
                            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                            updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
                        ),
                    ),
            )

            val freshLookup = workoutGroupReadCache.lookup(userId, planId)

            assertEquals(staleLookup.version + 1, freshLookup.version)
            assertNull(freshLookup.value)
        }

    private fun listPlans(
        expectedCount: Int,
        expectedName: String? = null,
        expectedActivePlanId: UUID? = null,
    ) {
        val response =
            client
                .get()
                .uri("/api/plans")
                .header("Authorization", bearerToken())
                .exchange()
                .expectStatus().isOk
                .expectBodyList(WorkoutPlanResponse::class.java)
                .returnResult()
                .responseBody!!

        assertEquals(expectedCount, response.size)
        expectedName?.let { assertEquals(it, response.single().name) }
        expectedActivePlanId?.let { activeId ->
            assertEquals(activeId, response.single { it.isActive }.id)
        }
    }

    private fun getPlanDetail(
        planId: UUID,
        expectation: DetailExpectation,
    ) {
        val response =
            client
                .get()
                .uri("/api/plans/$planId")
                .header("Authorization", bearerToken())
                .exchange()
                .expectStatus().isOk
                .expectBody(WorkoutPlanDetailResponse::class.java)
                .returnResult()
                .responseBody!!

        assertEquals(expectation.expectedGroups, response.groups.size)
        expectation.expectedName?.let { assertEquals(it, response.name) }
        expectation.expectedGroupTitle?.let { assertEquals(it, response.groups.single().title) }
        expectation.expectedExerciseCount?.let { assertEquals(it, response.groups.single().exercises.size) }
        expectation.expectedSets?.let { assertEquals(it, response.groups.single().exercises.single().sets) }
        expectation.expectedExerciseName?.let {
            assertEquals(
                it,
                response.groups.single().exercises.single().exerciseName,
            )
        }
    }

    private suspend fun requireGroup(
        planId: UUID,
        groupId: UUID,
        expectedTitle: String = "Treino A",
    ) {
        val userId = requireNotNull(userRepository.findByEmail(userEmail)?.id)
        val group = workoutPlanService.getRequiredGroup(userId, planId, groupId)
        assertEquals(expectedTitle, group.title)
    }

    private fun createPlan(name: String): UUID =
        client
            .post()
            .uri("/api/plans")
            .header("Authorization", bearerToken())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to name))
            .exchange()
            .expectStatus().isCreated
            .expectBody(WorkoutPlanResponse::class.java)
            .returnResult()
            .responseBody!!
            .id

    private fun updatePlan(
        planId: UUID,
        name: String,
    ) {
        client
            .patch()
            .uri("/api/plans/$planId")
            .header("Authorization", bearerToken())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to name))
            .exchange()
            .expectStatus().isOk
    }

    private fun deletePlan(planId: UUID) {
        client
            .delete()
            .uri("/api/plans/$planId")
            .header("Authorization", bearerToken())
            .exchange()
            .expectStatus().isNoContent
    }

    private fun activatePlan(planId: UUID) {
        client
            .post()
            .uri("/api/plans/$planId/activate")
            .header("Authorization", bearerToken())
            .exchange()
            .expectStatus().isNoContent
    }

    private fun createGroup(
        planId: UUID,
        title: String,
    ): UUID =
        client
            .post()
            .uri("/api/plans/$planId/groups")
            .header("Authorization", bearerToken())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("title" to title))
            .exchange()
            .expectStatus().isCreated
            .expectBody(WorkoutGroupResponse::class.java)
            .returnResult()
            .responseBody!!
            .id

    private fun updateGroup(
        planId: UUID,
        groupId: UUID,
        title: String,
    ) {
        client
            .patch()
            .uri("/api/plans/$planId/groups/$groupId")
            .header("Authorization", bearerToken())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("title" to title))
            .exchange()
            .expectStatus().isOk
    }

    private fun deleteGroup(
        planId: UUID,
        groupId: UUID,
    ) {
        client
            .delete()
            .uri("/api/plans/$planId/groups/$groupId")
            .header("Authorization", bearerToken())
            .exchange()
            .expectStatus().isNoContent
    }

    private fun createWorkoutExercise(
        planId: UUID,
        groupId: UUID,
        catalogExerciseId: UUID,
    ): UUID =
        client
            .post()
            .uri("/api/plans/$planId/groups/$groupId/exercises")
            .header("Authorization", bearerToken())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("exerciseId" to catalogExerciseId, "sets" to 4, "reps" to 8))
            .exchange()
            .expectStatus().isCreated
            .expectBody(WorkoutExerciseResponse::class.java)
            .returnResult()
            .responseBody!!
            .id

    private fun updateWorkoutExercise(
        planId: UUID,
        groupId: UUID,
        workoutExerciseId: UUID,
        sets: Int,
    ) {
        client
            .patch()
            .uri("/api/plans/$planId/groups/$groupId/exercises/$workoutExerciseId")
            .header("Authorization", bearerToken())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("sets" to sets))
            .exchange()
            .expectStatus().isOk
    }

    private fun deleteWorkoutExercise(
        planId: UUID,
        groupId: UUID,
        workoutExerciseId: UUID,
    ) {
        client
            .delete()
            .uri("/api/plans/$planId/groups/$groupId/exercises/$workoutExerciseId")
            .header("Authorization", bearerToken())
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
            .header("Authorization", bearerToken())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to name, "muscleGroup" to muscleGroup))
            .exchange()
            .expectStatus().isCreated
            .expectBody(ExerciseResponse::class.java)
            .returnResult()
            .responseBody!!
            .id

    private fun renameExercise(
        exerciseId: UUID,
        name: String,
    ) {
        client
            .patch()
            .uri("/api/exercises/$exerciseId")
            .header("Authorization", bearerToken())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to name))
            .exchange()
            .expectStatus().isOk
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

    private fun bearerToken(): String = "Bearer $authToken"

    private fun cacheCounter(
        cacheName: String,
        result: String,
    ): Double =
        meterRegistry
            .find("satzwerk.cache.requests")
            .tags("cache", cacheName, "result", result)
            .counter()
            ?.count() ?: 0.0

    private data class DetailExpectation(
        val expectedGroups: Int,
        val expectedName: String? = null,
        val expectedGroupTitle: String? = null,
        val expectedExerciseCount: Int? = null,
        val expectedSets: Int? = null,
        val expectedExerciseName: String? = null,
    )
}
