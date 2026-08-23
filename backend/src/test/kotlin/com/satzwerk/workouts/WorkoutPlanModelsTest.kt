package com.satzwerk.workouts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class WorkoutPlanModelsTest {
    @Test
    fun `WorkoutPlanResponse companion maps workout plan fields`() {
        val createdAt = Instant.parse("2026-08-23T11:00:00Z")
        val updatedAt = Instant.parse("2026-08-23T11:10:00Z")
        val plan =
            WorkoutPlan(
                id = UUID.randomUUID(),
                userId = UUID.randomUUID(),
                name = "PPL",
                source = WorkoutSource.IMPORTED.name,
                isActive = true,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )

        val response = WorkoutPlanResponse.from(plan)

        assertEquals(plan.id, response.id)
        assertEquals("PPL", response.name)
        assertEquals(WorkoutSource.IMPORTED.name, response.source)
        assertEquals(true, response.isActive)
        assertEquals(createdAt, response.createdAt)
        assertEquals(updatedAt, response.updatedAt)
    }

    @Test
    fun `WorkoutGroupResponse companion maps workout group fields`() {
        val group =
            WorkoutGroup(
                id = UUID.randomUUID(),
                workoutPlanId = UUID.randomUUID(),
                title = "Treino A",
                orderIndex = 2,
            )

        val response = WorkoutGroupResponse.from(group)

        assertEquals(group.id, response.id)
        assertEquals("Treino A", response.title)
        assertEquals(2, response.orderIndex)
    }

    @Test
    fun `WorkoutExerciseResponse companion maps workout exercise fields with exercise name`() {
        val workoutExercise =
            WorkoutExercise(
                id = UUID.randomUUID(),
                workoutGroupId = UUID.randomUUID(),
                exerciseId = UUID.randomUUID(),
                sets = 4,
                reps = 8,
                toFailure = true,
                advancedTechnique = AdvancedTechnique.REST_PAUSE.name,
                orderIndex = 1,
            )

        val response = WorkoutExerciseResponse.from(workoutExercise, "Bench Press")

        assertEquals(workoutExercise.id, response.id)
        assertEquals(workoutExercise.exerciseId, response.exerciseId)
        assertEquals("Bench Press", response.exerciseName)
        assertEquals(4, response.sets)
        assertEquals(8, response.reps)
        assertEquals(true, response.toFailure)
        assertEquals(AdvancedTechnique.REST_PAUSE.name, response.advancedTechnique)
        assertEquals(1, response.orderIndex)
    }

    @Test
    fun `WorkoutPlanDetailResponse companion maps groups and defaults missing exercises to empty list`() {
        val createdAt = Instant.parse("2026-08-23T12:00:00Z")
        val updatedAt = Instant.parse("2026-08-23T12:20:00Z")
        val plan =
            WorkoutPlan(
                id = UUID.randomUUID(),
                userId = UUID.randomUUID(),
                name = "PPL",
                source = WorkoutSource.MANUAL.name,
                isActive = false,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        val firstGroup =
            WorkoutGroup(
                id = UUID.randomUUID(),
                workoutPlanId = requireNotNull(plan.id),
                title = "Treino A",
                orderIndex = 0,
            )
        val secondGroup =
            WorkoutGroup(
                id = UUID.randomUUID(),
                workoutPlanId = requireNotNull(plan.id),
                title = "Treino B",
                orderIndex = 1,
            )
        val mappedExercise =
            WorkoutExerciseResponse(
                id = UUID.randomUUID(),
                exerciseId = UUID.randomUUID(),
                exerciseName = "Bench Press",
                sets = 4,
                reps = 8,
                toFailure = false,
                advancedTechnique = null,
                orderIndex = 0,
            )

        val response =
            WorkoutPlanDetailResponse.from(
                plan = plan,
                groups = listOf(firstGroup, secondGroup),
                exercisesByGroup =
                    mapOf(
                        requireNotNull(firstGroup.id) to listOf(mappedExercise),
                    ),
            )

        assertEquals(plan.id, response.id)
        assertEquals("PPL", response.name)
        assertEquals(createdAt, response.createdAt)
        assertEquals(updatedAt, response.updatedAt)
        assertEquals(listOf(mappedExercise), response.groups[0].exercises)
        assertEquals(emptyList<WorkoutExerciseResponse>(), response.groups[1].exercises)
    }

    @Test
    fun `AdvancedTechniqueMetadataResponse companion maps advanced technique metadata`() {
        val response = AdvancedTechniqueMetadataResponse.from(AdvancedTechnique.GVT)

        assertEquals(AdvancedTechnique.GVT.name, response.value)
        assertEquals(AdvancedTechnique.GVT.label, response.label)
        assertEquals(AdvancedTechnique.GVT.description, response.description)
        assertEquals(AdvancedTechnique.GVT.restSeconds, response.restSeconds)
    }
}
