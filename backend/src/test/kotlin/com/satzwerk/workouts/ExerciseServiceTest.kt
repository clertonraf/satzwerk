package com.satzwerk.workouts

import com.satzwerk.common.TransactionRunner
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.UUID

class ExerciseServiceTest {
    private val userId = UUID.randomUUID()
    private val exerciseId = UUID.randomUUID()
    private val planId = UUID.randomUUID()
    private val inlineTransactionRunner =
        object : TransactionRunner {
            override suspend fun <T> required(block: suspend () -> T): T = block()

            override suspend fun afterCommit(block: suspend () -> Unit) {
                block()
            }
        }

    @Test
    fun `update invalidates Exercise list and affected WorkoutPlan details after commit`(): Unit =
        runBlocking {
            val exerciseCatalogCache: ExerciseCatalogCache = mock()
            val workoutPlanReadCache: WorkoutPlanReadCache = mock()
            val service =
                ExerciseService(
                    exerciseRepository =
                        mock {
                            onBlocking { findById(exerciseId) } doReturn existingExercise()
                            onBlocking { save(any()) } doAnswer { it.arguments[0] as Exercise }
                        },
                    workoutExerciseRepository =
                        mock {
                            onBlocking { findDistinctWorkoutPlanIdsByExerciseId(exerciseId) } doReturn listOf(planId)
                        },
                    exerciseCatalogCache = exerciseCatalogCache,
                    workoutPlanReadCache = workoutPlanReadCache,
                    transactionRunner = inlineTransactionRunner,
                )

            service.update(userId, exerciseId, UpdateExerciseRequest(name = "Paused Bench Press"))

            verify(exerciseCatalogCache).invalidateUser(userId)
            verify(workoutPlanReadCache).invalidateDetail(userId, planId)
        }

    @Test
    fun `delete invalidates Exercise list and affected WorkoutPlan details after commit`(): Unit =
        runBlocking {
            val exerciseCatalogCache: ExerciseCatalogCache = mock()
            val workoutPlanReadCache: WorkoutPlanReadCache = mock()
            val service =
                ExerciseService(
                    exerciseRepository =
                        mock {
                            onBlocking { findById(exerciseId) } doReturn existingExercise()
                        },
                    workoutExerciseRepository =
                        mock {
                            onBlocking { findDistinctWorkoutPlanIdsByExerciseId(exerciseId) } doReturn listOf(planId)
                        },
                    exerciseCatalogCache = exerciseCatalogCache,
                    workoutPlanReadCache = workoutPlanReadCache,
                    transactionRunner = inlineTransactionRunner,
                )

            service.delete(userId, exerciseId)

            verify(exerciseCatalogCache).invalidateUser(userId)
            verify(workoutPlanReadCache).invalidateDetail(userId, planId)
        }

    private fun existingExercise(): Exercise =
        Exercise(
            id = exerciseId,
            userId = userId,
            name = "Bench Press",
            muscleGroup = "CHEST",
        )
}
