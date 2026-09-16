package com.satzwerk.workouts

import org.springframework.stereotype.Component

@Component
class WorkoutReadCaches(
    val exerciseCatalogCache: ExerciseCatalogCache,
    val workoutPlanReadCache: WorkoutPlanReadCache,
    val workoutGroupReadCache: WorkoutGroupReadCache,
)
