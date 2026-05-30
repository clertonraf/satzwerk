package com.satzwerk.workouts

import java.util.UUID

fun Exercise.toResponse(): ExerciseResponse =
    ExerciseResponse(
        id = requireNotNull(id),
        name = name,
        muscleGroup = muscleGroup,
        description = description,
        videoUrl = videoUrl,
        equipment = equipment,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun WorkoutGroup.toResponse(): WorkoutGroupResponse =
    WorkoutGroupResponse(
        id = requireNotNull(id),
        title = title,
        orderIndex = orderIndex,
    )

fun WorkoutPlan.toResponse(): WorkoutPlanResponse =
    WorkoutPlanResponse(
        id = requireNotNull(id),
        name = name,
        source = source,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun WorkoutPlan.toDetailResponse(
    groups: List<WorkoutGroup>,
    exercisesByGroup: Map<UUID, List<WorkoutExerciseResponse>>,
): WorkoutPlanDetailResponse =
    WorkoutPlanDetailResponse(
        id = requireNotNull(id),
        name = name,
        source = source,
        isActive = isActive,
        groups =
            groups.map { group ->
                WorkoutGroupDetailResponse(
                    id = requireNotNull(group.id),
                    title = group.title,
                    orderIndex = group.orderIndex,
                    exercises = exercisesByGroup[requireNotNull(group.id)].orEmpty(),
                )
            },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun WorkoutExercise.toResponse(exerciseName: String): WorkoutExerciseResponse =
    WorkoutExerciseResponse(
        id = requireNotNull(id),
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        sets = sets,
        reps = reps,
        toFailure = toFailure,
        advancedTechnique = advancedTechnique,
        orderIndex = orderIndex,
    )

fun WorkoutExerciseWithName.toResponse(): WorkoutExerciseResponse =
    WorkoutExerciseResponse(
        id = id,
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        sets = sets,
        reps = reps,
        toFailure = toFailure,
        advancedTechnique = advancedTechnique,
        orderIndex = orderIndex,
    )
