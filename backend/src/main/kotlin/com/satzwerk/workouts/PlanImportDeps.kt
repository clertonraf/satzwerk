package com.satzwerk.workouts

import org.springframework.stereotype.Component

@Component
class PlanImportDeps(
    val workoutPlanRepository: WorkoutPlanRepository,
    val workoutGroupRepository: WorkoutGroupRepository,
    val workoutExerciseRepository: WorkoutExerciseRepository,
    val exerciseResolver: ExerciseResolver,
    val planImportParsingAdapters: PlanImportParsingAdapters,
    val workoutReadCaches: WorkoutReadCaches,
)
