package com.satzwerk.export

import com.satzwerk.workouts.WorkoutReadPort
import org.springframework.stereotype.Component

@Component
class ExportWorkoutDeps(
    val workoutReadPort: WorkoutReadPort,
    val workoutImportPort: WorkoutImportPort,
)
