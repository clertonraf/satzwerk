package com.satzwerk.export

import com.satzwerk.analytics.AnalyticsReadCache
import com.satzwerk.workouts.WorkoutReadCaches
import com.satzwerk.workouts.WorkoutReadPort
import org.springframework.stereotype.Component

@Component
class ExportWorkoutDeps(
    val workoutReadPort: WorkoutReadPort,
    val workoutImportPort: WorkoutImportPort,
    val workoutReadCaches: WorkoutReadCaches,
    val analyticsReadCache: AnalyticsReadCache,
)
