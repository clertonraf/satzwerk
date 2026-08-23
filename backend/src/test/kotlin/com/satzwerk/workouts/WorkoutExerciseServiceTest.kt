package com.satzwerk.workouts

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
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
                )

            val result = service.reorder(userId, planId, groupId, exerciseId, ReorderDirection.UP)

            assertEquals(1, result.size)
            assertEquals("Bench Press", result.first().exerciseName)
            verify(exerciseRepository).findAllById(eq(setOf(exerciseId)))
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

    private fun workoutExercise(
        id: UUID,
        orderIndex: Int,
    ): WorkoutExercise =
        WorkoutExercise(
            id = id,
            workoutGroupId = groupId,
            exerciseId = exerciseId,
            sets = 3,
            reps = 8,
            orderIndex = orderIndex,
        )
}
