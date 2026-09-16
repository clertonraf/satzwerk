package com.satzwerk.workouts

import org.springframework.stereotype.Component

@Component
class WorkoutPlanDetailDeps(
    val workoutGroupRepository: WorkoutGroupRepository,
    val workoutExerciseRepository: WorkoutExerciseRepository,
    val exerciseRepository: ExerciseRepository,
)
