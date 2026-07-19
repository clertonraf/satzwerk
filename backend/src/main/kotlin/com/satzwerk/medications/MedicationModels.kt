package com.satzwerk.medications

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateMedicationRequest(
    @field:NotBlank
    val name: String,
    @field:NotNull
    @field:DecimalMin("0.0001")
    val dosageAmount: BigDecimal,
    @field:NotNull
    val dosageUnit: DosageUnit,
    @field:NotNull
    val frequency: FrequencySpec,
    val purpose: String? = null,
)

data class UpdateMedicationRequest(
    @field:NotBlank
    val name: String,
    @field:NotNull
    @field:DecimalMin("0.0001")
    val dosageAmount: BigDecimal,
    @field:NotNull
    val dosageUnit: DosageUnit,
    @field:NotNull
    val frequency: FrequencySpec,
    val purpose: String? = null,
)

data class MedicationResponse(
    val id: UUID,
    val name: String,
    val dosageAmount: BigDecimal,
    val dosageUnit: DosageUnit,
    val frequency: FrequencySpec,
    val purpose: String?,
    val isActive: Boolean,
    val createdAt: Instant,
    val currentStreak: Int,
)

data class LogDoseRequest(
    @field:NotNull
    val takenAt: Instant,
    @field:NotNull
    val taken: Boolean,
    @field:DecimalMin("0.0001")
    val doseAmount: BigDecimal? = null,
    val notes: String? = null,
)

data class MedicationLogResponse(
    val id: UUID,
    val medicationId: UUID,
    val takenAt: Instant,
    val taken: Boolean,
    val doseAmount: BigDecimal?,
    val notes: String?,
)

data class ScheduledDoseSummaryDto(
    val medication: MedicationResponse,
    val scheduledCount: Int,
    val logs: List<MedicationLogResponse>,
)

data class AdherenceHeatmapDayDto(
    val date: String,
    val adherenceRatio: Double,
    val takenCount: Int,
    val scheduledCount: Int,
)

data class AdherenceHeatmapDto(
    val days: List<AdherenceHeatmapDayDto>,
)

data class BarChartPeriodDto(
    val period: String,
    val taken: Int,
    val skipped: Int,
)

data class PerMedicationAnalyticsDto(
    val heatmap: AdherenceHeatmapDto,
    val barChart: List<BarChartPeriodDto>,
    val currentStreak: Int,
)

enum class BarChartGranularity { WEEKLY, MONTHLY }

@Min(1)
annotation class MinOne

fun MedicationLog.toResponse(): MedicationLogResponse =
    MedicationLogResponse(
        id = requireNotNull(id),
        medicationId = medicationId,
        takenAt = takenAt,
        taken = taken,
        doseAmount = doseAmount,
        notes = notes,
    )
