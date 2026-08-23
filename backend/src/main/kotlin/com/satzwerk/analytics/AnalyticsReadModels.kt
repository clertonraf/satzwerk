package com.satzwerk.analytics

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class DashboardSummaryRow(
    val totalSessions: Int,
    val sessionsThisMonth: Int,
    val setsThisWeek: Int,
    val prsThisMonth: Int,
    val activePlanDays: Int?,
    val avgSessionDurationMinutes: Int?,
)

data class WeeklyTrendRow(
    val week: String,
    val setCount: Int,
    val sessionCount: Int,
)

data class PersonalRecordRow(
    val exerciseId: UUID,
    val exerciseName: String,
    val weightKg: BigDecimal,
    val reps: Int,
    val achievedAt: Instant,
)

data class TopExerciseRow(
    val exerciseId: UUID,
    val exerciseName: String,
    val setCount: Int,
)

data class ExerciseProgressRow(
    val sessionId: UUID,
    val sessionDate: LocalDate,
    val workoutGroupTitle: String,
    val exerciseId: UUID,
    val exerciseName: String,
    val topSetWeightKg: BigDecimal,
    val topSetReps: Int,
)
