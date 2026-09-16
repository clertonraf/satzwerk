package com.satzwerk.workouts

import com.satzwerk.common.TransactionRunner
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class WorkoutExerciseServiceTest {
    private val userId: UUID = UUID.randomUUID()
    private val planId: UUID = UUID.randomUUID()
    private val groupId: UUID = UUID.randomUUID()
    private val exerciseId: UUID = UUID.randomUUID()
    private val inlineTransactionRunner =
        object : TransactionRunner {
            override suspend fun <T> required(block: suspend () -> T): T = block()

            override suspend fun afterCommit(block: suspend () -> Unit) {
                block()
            }
        }

    @Test
    fun `reorder enriches returned workout exercises above repository seam`(): Unit =
        runBlocking {
            val planService = mockPlanService()
            val workoutExerciseRepository =
                mock<WorkoutExerciseRepository> {
                    onBlocking {
                        findAllByWorkoutGroupIdOrderByOrderIndex(groupId)
                    } doReturn listOf(workoutExercise(id = exerciseId, orderIndex = 0))
                }
            val exerciseRepository: ExerciseRepository = mock()
            whenever(exerciseRepository.findAllById(setOf(exerciseId))).thenReturn(
                flowOf(
                    Exercise(
                        id = exerciseId,
                        userId = userId,
                        name = "Bench Press",
                        muscleGroup = "CHEST",
                    ),
                ),
            )

            val service =
                WorkoutExerciseService(
                    workoutPlanService = planService,
                    workoutExerciseRepository = workoutExerciseRepository,
                    exerciseRepository = exerciseRepository,
                    workoutPlanReadCache = mock(),
                    transactionRunner = inlineTransactionRunner,
                )

            val result = service.reorder(userId, planId, groupId, exerciseId, ReorderDirection.UP)

            assertEquals(1, result.size)
            assertEquals("Bench Press", result.first().exerciseName)
            verify(exerciseRepository).findAllById(eq(setOf(exerciseId)))
        }

    @Test
    fun `create invalidates the WorkoutPlan detail cache after commit`(): Unit =
        runBlocking {
            val workoutPlanReadCache: WorkoutPlanReadCache = mock()
            val service =
                WorkoutExerciseService(
                    workoutPlanService = mockPlanService(),
                    workoutExerciseRepository =
                        mock {
                            onBlocking { save(any()) } doReturn workoutExercise(id = UUID.randomUUID(), orderIndex = 0)
                        },
                    exerciseRepository = exerciseRepository(),
                    workoutPlanReadCache = workoutPlanReadCache,
                    transactionRunner = inlineTransactionRunner,
                )

            service.create(
                userId,
                planId,
                groupId,
                CreateWorkoutExerciseRequest(exerciseId = exerciseId, sets = 3, reps = 8),
            )

            verify(workoutPlanReadCache).invalidateDetail(userId, planId)
        }

    @Test
    fun `update invalidates the WorkoutPlan detail cache after commit`(): Unit =
        runBlocking {
            val workoutPlanReadCache: WorkoutPlanReadCache = mock()
            val existing = workoutExercise(id = UUID.randomUUID(), orderIndex = 0)
            val service =
                WorkoutExerciseService(
                    workoutPlanService = mockPlanService(),
                    workoutExerciseRepository =
                        mock {
                            onBlocking { findByIdAndWorkoutGroupId(existing.id!!, groupId) } doReturn existing
                            onBlocking { save(any()) } doAnswer { it.arguments[0] as WorkoutExercise }
                        },
                    exerciseRepository = exerciseRepository(),
                    workoutPlanReadCache = workoutPlanReadCache,
                    transactionRunner = inlineTransactionRunner,
                )

            service.update(
                userId,
                planId,
                groupId,
                existing.id!!,
                UpdateWorkoutExerciseRequest(sets = 5),
            )

            verify(workoutPlanReadCache).invalidateDetail(userId, planId)
        }

    @Test
    fun `delete invalidates the WorkoutPlan detail cache after commit`(): Unit =
        runBlocking {
            val workoutPlanReadCache: WorkoutPlanReadCache = mock()
            val existing = workoutExercise(id = UUID.randomUUID(), orderIndex = 0)
            val service =
                WorkoutExerciseService(
                    workoutPlanService = mockPlanService(),
                    workoutExerciseRepository =
                        mock {
                            onBlocking { findByIdAndWorkoutGroupId(existing.id!!, groupId) } doReturn existing
                        },
                    exerciseRepository = exerciseRepository(),
                    workoutPlanReadCache = workoutPlanReadCache,
                    transactionRunner = inlineTransactionRunner,
                )

            service.delete(userId, planId, groupId, existing.id!!)

            verify(workoutPlanReadCache).invalidateDetail(userId, planId)
        }

    @Test
    fun `reorder invalidates the WorkoutPlan detail cache after commit`(): Unit =
        runBlocking {
            val workoutPlanReadCache: WorkoutPlanReadCache = mock()
            val first = workoutExercise(id = exerciseId, orderIndex = 0)
            val secondCatalogExerciseId = UUID.randomUUID()
            val second =
                workoutExercise(
                    id = UUID.randomUUID(),
                    orderIndex = 1,
                    catalogExerciseId = secondCatalogExerciseId,
                )
            val workoutExerciseRepository =
                mock<WorkoutExerciseRepository> {
                    onBlocking { findAllByWorkoutGroupIdOrderByOrderIndex(groupId) } doReturn listOf(first, second)
                    onBlocking { save(any()) } doAnswer { it.arguments[0] as WorkoutExercise }
                }
            val exerciseRepository: ExerciseRepository = mock()
            whenever(exerciseRepository.findAllById(setOf(exerciseId, secondCatalogExerciseId))).thenReturn(
                flowOf(
                    Exercise(id = exerciseId, userId = userId, name = "Bench Press", muscleGroup = "CHEST"),
                    Exercise(
                        id = secondCatalogExerciseId,
                        userId = userId,
                        name = "Incline Bench Press",
                        muscleGroup = "CHEST",
                    ),
                ),
            )
            val service =
                WorkoutExerciseService(
                    workoutPlanService = mockPlanService(),
                    workoutExerciseRepository = workoutExerciseRepository,
                    exerciseRepository = exerciseRepository,
                    workoutPlanReadCache = workoutPlanReadCache,
                    transactionRunner = inlineTransactionRunner,
                )

            service.reorder(userId, planId, groupId, first.id!!, ReorderDirection.DOWN)

            verify(workoutPlanReadCache).invalidateDetail(userId, planId)
        }

    private fun mockPlanService(): WorkoutPlanService =
        mock {
            onBlocking { getRequiredGroup(userId, planId, groupId) } doReturn
                WorkoutGroup(
                    id = groupId,
                    workoutPlanId = planId,
                    title = "Treino A",
                )
        }

    private fun exerciseRepository(): ExerciseRepository =
        mock {
            onBlocking { findById(exerciseId) } doReturn
                Exercise(
                    id = exerciseId,
                    userId = userId,
                    name = "Bench Press",
                    muscleGroup = "CHEST",
                )
        }

    private fun workoutExercise(
        id: UUID,
        orderIndex: Int,
        catalogExerciseId: UUID = exerciseId,
    ): WorkoutExercise =
        WorkoutExercise(
            id = id,
            workoutGroupId = groupId,
            exerciseId = catalogExerciseId,
            sets = 3,
            reps = 8,
            orderIndex = orderIndex,
        )
}
