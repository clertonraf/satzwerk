package com.satzwerk.sessions

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

private const val MAX_RIR = 10L

data class WorkoutSessionResponse(
    val id: UUID,
    val workoutGroupId: UUID,
    val workoutGroupTitle: String,
    val startedAt: Instant,
    val completedAt: Instant?,
    val notes: String?,
    val setLogs: List<SetLogResponse>,
    val setCount: Int,
    val exerciseCount: Int,
)

data class SetLogResponse(
    val id: UUID,
    val exerciseId: UUID,
    val setNumber: Int,
    val weight: BigDecimal,
    val reps: Int,
    val rir: Int?,
    val loggedAt: Instant,
)

data class StartSessionRequest(
    @field:NotNull
    val workoutGroupId: UUID,
)

data class AddSetLogRequest(
    @field:NotNull
    val exerciseId: UUID,
    @field:Min(1)
    val setNumber: Int,
    @field:DecimalMin("0.0")
    val weight: BigDecimal,
    @field:Min(1)
    val reps: Int,
    @field:Min(0)
    @field:Max(MAX_RIR)
    val rir: Int? = null,
)

data class UpdateSetLogRequest(
    @field:DecimalMin("0.0")
    val weight: BigDecimal,
    @field:Min(1)
    val reps: Int,
    @field:Min(0)
    @field:Max(MAX_RIR)
    val rir: Int? = null,
)

data class CompleteSessionRequest(
    val notes: String? = null,
)

data class ExerciseReferenceWeights(
    val exerciseId: UUID,
    val previousWeightKg: BigDecimal?,
    val prWeightKg: BigDecimal?,
    val estimatedOneRepMaxMinKg: BigDecimal?,
    val estimatedOneRepMaxMaxKg: BigDecimal?,
    val suggestedWeightKg: BigDecimal?,
)
