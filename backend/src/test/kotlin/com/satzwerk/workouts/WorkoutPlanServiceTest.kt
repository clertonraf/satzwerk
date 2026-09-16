package com.satzwerk.workouts

import com.satzwerk.analytics.AnalyticsReadCache
import com.satzwerk.cache.VersionedCacheValue
import com.satzwerk.common.ForbiddenException
import com.satzwerk.common.NotFoundException
import com.satzwerk.common.TransactionRunner
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class WorkoutPlanServiceTest {
    private val userId = UUID.randomUUID()
    private val otherUserId = UUID.randomUUID()
    private val planId = UUID.randomUUID()
    private val otherPlanId = UUID.randomUUID()
    private val groupId = UUID.randomUUID()
    private val inlineTransactionRunner =
        object : TransactionRunner {
            override suspend fun <T> required(block: suspend () -> T): T = block()

            override suspend fun afterCommit(block: suspend () -> Unit) {
                block()
            }
        }

    @Test
    fun `getRequiredGroup returns group when user owns WorkoutPlan`(): Unit =
        runBlocking {
            val plan = WorkoutPlan(id = planId, userId = userId, name = "PPL")
            val group = WorkoutGroup(id = groupId, workoutPlanId = planId, title = "Treino A")
            val service =
                service(
                    plan = plan,
                    group = group,
                )
            assertEquals(group, service.getRequiredGroup(userId, planId, groupId))
        }

    @Test
    fun `getRequiredGroup throws ForbiddenException when WorkoutPlan belongs to another user`(): Unit =
        runBlocking {
            val plan = WorkoutPlan(id = planId, userId = otherUserId, name = "PPL")
            val service = service(plan = plan)

            val exception =
                assertThrows<ForbiddenException> {
                    runBlocking {
                        service.getRequiredGroup(userId, planId, groupId)
                    }
                }

            assertEquals("Workout plan does not belong to user", exception.message)
        }

    @Test
    fun `getRequiredGroup throws NotFoundException when group does not belong to WorkoutPlan`(): Unit =
        runBlocking {
            val plan = WorkoutPlan(id = planId, userId = userId, name = "PPL")
            val service = service(plan = plan)

            val exception =
                assertThrows<NotFoundException> {
                    runBlocking {
                        service.getRequiredGroup(userId, planId, groupId)
                    }
                }

            assertEquals("Workout group not found", exception.message)
        }

    @Test
    fun `getDetail enriches workout exercises above repository seam`(): Unit =
        runBlocking {
            val workoutExercise = detailWorkoutExercise()
            val exercise = detailExercise(workoutExercise.exerciseId)
            val workoutExerciseRepository =
                mock<WorkoutExerciseRepository> {
                    onBlocking {
                        findAllByWorkoutGroupIdInOrderByWorkoutGroupIdAscOrderIndexAsc(listOf(groupId))
                    } doReturn listOf(workoutExercise)
                }
            val exerciseRepository = detailExerciseRepository(workoutExercise.exerciseId, exercise)
            val service = detailService(workoutExerciseRepository, exerciseRepository)

            val result = service.getDetail(userId, planId)

            assertEquals("Bench Press", result.groups.single().exercises.single().exerciseName)
            verify(workoutExerciseRepository).findAllByWorkoutGroupIdInOrderByWorkoutGroupIdAscOrderIndexAsc(
                eq(listOf(groupId)),
            )
            verify(exerciseRepository).findAllById(eq(setOf(workoutExercise.exerciseId)))
        }

    @Test
    fun `create invalidates the list cache after commit`(): Unit =
        runBlocking {
            val workoutPlanReadCache: WorkoutPlanReadCache = mock()
            val service =
                WorkoutPlanService(
                    workoutPlanRepository =
                        mock {
                            onBlocking { save(any()) } doReturn WorkoutPlan(id = planId, userId = userId, name = "PPL")
                        },
                    workoutPlanDetailDeps =
                        WorkoutPlanDetailDeps(
                            workoutGroupRepository = mock(),
                            workoutExerciseRepository = mock(),
                            exerciseRepository = mock(),
                        ),
                    workoutReadCaches =
                        WorkoutReadCaches(
                            exerciseCatalogCache = mock(),
                            workoutPlanReadCache = workoutPlanReadCache,
                            workoutGroupReadCache = mock(),
                        ),
                    analyticsReadCache = mock(),
                    transactionRunner = inlineTransactionRunner,
                )

            service.create(userId, CreatePlanRequest(name = "PPL"))

            verify(workoutPlanReadCache).invalidateList(userId)
            verify(workoutPlanReadCache, never()).invalidateDetail(any(), any())
        }

    @Test
    fun `update invalidates the list and detail caches after commit`(): Unit =
        runBlocking {
            val workoutPlanReadCache: WorkoutPlanReadCache = mock()
            val service =
                service(
                    plan = WorkoutPlan(id = planId, userId = userId, name = "PPL"),
                    workoutPlanReadCache = workoutPlanReadCache,
                )

            service.update(userId, planId, UpdatePlanRequest(name = "Push Pull Legs"))

            verify(workoutPlanReadCache).invalidateList(userId)
            verify(workoutPlanReadCache).invalidateDetail(userId, planId)
        }

    @Test
    fun `delete invalidates list cache and removes plan-scoped caches after commit`(): Unit =
        runBlocking {
            val workoutPlanReadCache: WorkoutPlanReadCache = mock()
            val workoutGroupReadCache: WorkoutGroupReadCache = mock()
            val analyticsReadCache: AnalyticsReadCache = mock()
            val service =
                service(
                    plan = WorkoutPlan(id = planId, userId = userId, name = "PPL"),
                    workoutPlanReadCache = workoutPlanReadCache,
                    workoutGroupReadCache = workoutGroupReadCache,
                    analyticsReadCache = analyticsReadCache,
                )

            service.delete(userId, planId)

            verify(workoutPlanReadCache).invalidateList(userId)
            verify(workoutPlanReadCache).deleteDetail(userId, planId)
            verify(workoutGroupReadCache).deletePlan(userId, planId)
            verify(analyticsReadCache).invalidateUser(userId)
        }

    @Test
    fun `activate invalidates the list cache and both affected detail caches after commit`(): Unit =
        runBlocking {
            val targetPlan = WorkoutPlan(id = planId, userId = userId, name = "Plan B")
            val currentlyActivePlan = WorkoutPlan(id = otherPlanId, userId = userId, name = "Plan A", isActive = true)
            val workoutPlanReadCache: WorkoutPlanReadCache = mock()
            val workoutPlanRepository =
                mock<WorkoutPlanRepository> {
                    onBlocking { findById(planId) } doReturn targetPlan
                    onBlocking { findAllByUserIdAndIsActive(userId, true) } doReturn listOf(currentlyActivePlan)
                    onBlocking { save(any()) } doAnswer { it.arguments[0] as WorkoutPlan }
                }
            val service =
                WorkoutPlanService(
                    workoutPlanRepository = workoutPlanRepository,
                    workoutPlanDetailDeps =
                        WorkoutPlanDetailDeps(
                            workoutGroupRepository = mock(),
                            workoutExerciseRepository = mock(),
                            exerciseRepository = mock(),
                        ),
                    workoutReadCaches =
                        WorkoutReadCaches(
                            exerciseCatalogCache = mock(),
                            workoutPlanReadCache = workoutPlanReadCache,
                            workoutGroupReadCache = mock(),
                        ),
                    analyticsReadCache = mock(),
                    transactionRunner = inlineTransactionRunner,
                )

            service.activate(userId, planId)

            verify(workoutPlanReadCache).invalidateList(userId)
            verify(workoutPlanReadCache).invalidateDetail(userId, planId)
            verify(workoutPlanReadCache).invalidateDetail(userId, otherPlanId)
        }

    private fun service(
        plan: WorkoutPlan,
        group: WorkoutGroup? = null,
        workoutPlanReadCache: WorkoutPlanReadCache = stubWorkoutPlanReadCache(),
        workoutGroupReadCache: WorkoutGroupReadCache = stubWorkoutGroupReadCache(listOfNotNull(group)),
        analyticsReadCache: AnalyticsReadCache = mock(),
    ): WorkoutPlanService {
        val workoutPlanRepository =
            mock<WorkoutPlanRepository> {
                onBlocking { findById(planId) } doReturn plan
                onBlocking { save(any()) } doAnswer { it.arguments[0] as WorkoutPlan }
            }
        val workoutGroupRepository =
            mock<WorkoutGroupRepository> {
                onBlocking { findByIdAndWorkoutPlanId(groupId, planId) } doReturn group
                onBlocking { findAllByWorkoutPlanIdOrderByOrderIndex(planId) } doReturn listOfNotNull(group)
            }

        return WorkoutPlanService(
            workoutPlanRepository = workoutPlanRepository,
            workoutPlanDetailDeps =
                WorkoutPlanDetailDeps(
                    workoutGroupRepository = workoutGroupRepository,
                    workoutExerciseRepository = mock(),
                    exerciseRepository = mock(),
                ),
            workoutReadCaches =
                WorkoutReadCaches(
                    exerciseCatalogCache = mock(),
                    workoutPlanReadCache = workoutPlanReadCache,
                    workoutGroupReadCache = workoutGroupReadCache,
                ),
            analyticsReadCache = analyticsReadCache,
            transactionRunner = inlineTransactionRunner,
        )
    }

    private fun stubWorkoutPlanReadCache(): WorkoutPlanReadCache =
        mock {
            onBlocking { lookupList(any()) } doReturn VersionedCacheValue(version = 0, value = null)
            onBlocking { lookupDetail(any(), any()) } doReturn
                WorkoutPlanDetailCacheValue(detailVersion = 0, groupVersion = 0, value = null)
        }

    private fun stubWorkoutGroupReadCache(groups: List<WorkoutGroup> = emptyList()): WorkoutGroupReadCache =
        mock {
            onBlocking { lookup(any(), any()) } doReturn VersionedCacheValue(version = 0, value = groups)
            onBlocking { lookup(any(), any(), any()) } doReturn groups
        }

    private fun detailService(
        workoutExerciseRepository: WorkoutExerciseRepository,
        exerciseRepository: ExerciseRepository,
    ): WorkoutPlanService {
        val plan = WorkoutPlan(id = planId, userId = userId, name = "PPL")
        val group = WorkoutGroup(id = groupId, workoutPlanId = planId, title = "Treino A")
        return WorkoutPlanService(
            workoutPlanRepository =
                mock<WorkoutPlanRepository> {
                    onBlocking { findById(planId) } doReturn plan
                },
            workoutPlanDetailDeps =
                WorkoutPlanDetailDeps(
                    workoutGroupRepository =
                        mock<WorkoutGroupRepository> {
                            onBlocking { findAllByWorkoutPlanIdOrderByOrderIndex(planId) } doReturn listOf(group)
                        },
                    workoutExerciseRepository = workoutExerciseRepository,
                    exerciseRepository = exerciseRepository,
                ),
            workoutReadCaches =
                WorkoutReadCaches(
                    exerciseCatalogCache = mock(),
                    workoutPlanReadCache = stubWorkoutPlanReadCache(),
                    workoutGroupReadCache = stubWorkoutGroupReadCache(groups = listOf(group)),
                ),
            analyticsReadCache = mock(),
            transactionRunner = inlineTransactionRunner,
        )
    }

    private fun detailExerciseRepository(
        exerciseId: UUID,
        exercise: Exercise,
    ): ExerciseRepository =
        mock {
            whenever(it.findAllById(setOf(exerciseId))).thenReturn(flowOf(exercise))
        }

    private fun detailExercise(exerciseId: UUID): Exercise =
        Exercise(
            id = exerciseId,
            userId = userId,
            name = "Bench Press",
            muscleGroup = "CHEST",
        )

    private fun detailWorkoutExercise(): WorkoutExercise =
        WorkoutExercise(
            id = UUID.randomUUID(),
            workoutGroupId = groupId,
            exerciseId = UUID.randomUUID(),
            sets = 3,
            reps = 8,
            orderIndex = 0,
        )
}
