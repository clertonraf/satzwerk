package com.satzwerk.workouts

import com.satzwerk.common.ForbiddenException
import com.satzwerk.common.NotFoundException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class WorkoutPlanServiceTest {
    private val userId = UUID.randomUUID()
    private val otherUserId = UUID.randomUUID()
    private val planId = UUID.randomUUID()
    private val groupId = UUID.randomUUID()

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
            val workoutExercise =
                WorkoutExercise(
                    id = UUID.randomUUID(),
                    workoutGroupId = groupId,
                    exerciseId = UUID.randomUUID(),
                    sets = 3,
                    reps = 8,
                    orderIndex = 0,
                )
            val plan = WorkoutPlan(id = planId, userId = userId, name = "PPL")
            val group = WorkoutGroup(id = groupId, workoutPlanId = planId, title = "Treino A")
            val exercise =
                Exercise(
                    id = workoutExercise.exerciseId,
                    userId = userId,
                    name = "Bench Press",
                    muscleGroup = "CHEST",
                )
            val workoutPlanRepository =
                mock<WorkoutPlanRepository> {
                    onBlocking { findById(planId) } doReturn plan
                }
            val workoutGroupRepository =
                mock<WorkoutGroupRepository> {
                    onBlocking { findAllByWorkoutPlanIdOrderByOrderIndex(planId) } doReturn listOf(group)
                }
            val workoutExerciseRepository =
                mock<WorkoutExerciseRepository> {
                    onBlocking {
                        findAllByWorkoutGroupIdInOrderByWorkoutGroupIdAscOrderIndexAsc(listOf(groupId))
                    } doReturn listOf(workoutExercise)
                }
            val exerciseRepository: ExerciseRepository = mock()
            whenever(exerciseRepository.findAllById(setOf(workoutExercise.exerciseId))).thenReturn(flowOf(exercise))

            val service =
                WorkoutPlanService(
                    workoutPlanRepository = workoutPlanRepository,
                    workoutGroupRepository = workoutGroupRepository,
                    workoutExerciseRepository = workoutExerciseRepository,
                    exerciseRepository = exerciseRepository,
                )

            val result = service.getDetail(userId, planId)

            assertEquals("Bench Press", result.groups.single().exercises.single().exerciseName)
            verify(workoutExerciseRepository).findAllByWorkoutGroupIdInOrderByWorkoutGroupIdAscOrderIndexAsc(
                eq(listOf(groupId)),
            )
            verify(exerciseRepository).findAllById(eq(setOf(workoutExercise.exerciseId)))
        }

    private fun service(
        plan: WorkoutPlan,
        group: WorkoutGroup? = null,
    ): WorkoutPlanService {
        val workoutPlanRepository =
            mock<WorkoutPlanRepository> {
                onBlocking { findById(planId) } doReturn plan
            }
        val workoutGroupRepository =
            mock<WorkoutGroupRepository> {
                onBlocking { findByIdAndWorkoutPlanId(groupId, planId) } doReturn group
            }

        return WorkoutPlanService(
            workoutPlanRepository = workoutPlanRepository,
            workoutGroupRepository = workoutGroupRepository,
            workoutExerciseRepository = mock(),
            exerciseRepository = mock(),
        )
    }
}
