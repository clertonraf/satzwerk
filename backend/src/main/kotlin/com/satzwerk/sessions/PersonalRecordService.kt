package com.satzwerk.sessions

import com.satzwerk.workouts.WorkoutExerciseRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

private const val PR_RATIO_SCALE = 10

/** Identifies an existing set log being updated, so its ratio is excluded from the PR comparison. */
internal data class SetLogRef(val id: UUID?, val loggedAt: Instant)

@Service
class PersonalRecordService(
    private val sessionQueryRepository: SessionQueryRepository,
    private val workoutExerciseRepository: WorkoutExerciseRepository,
) {
    suspend fun findReferenceWeights(
        userId: UUID,
        workoutGroupId: UUID,
        sessionId: UUID,
    ): List<ExerciseReferenceWeights> {
        val workoutExercises =
            workoutExerciseRepository.findAllByWorkoutGroupIdOrderByOrderIndex(workoutGroupId)
        if (workoutExercises.isEmpty()) return emptyList()
        // De-duplicate by exerciseId keeping the first occurrence (lowest orderIndex), matching UI order.
        val uniqueExercises = workoutExercises.distinctBy { it.exerciseId }
        val exerciseIds = uniqueExercises.map { it.exerciseId }
        val workoutExerciseMap = uniqueExercises.associateBy { it.exerciseId }

        val previousWeights = sessionQueryRepository.findPreviousWeights(userId, exerciseIds, sessionId)
        val personalRecords = sessionQueryRepository.findPersonalRecords(userId, exerciseIds)

        return exerciseIds.map { exerciseId ->
            val previousWeight = previousWeights[exerciseId]
            val pr = personalRecords[exerciseId]
            val oneRepMax = pr.toEstimatedOneRepMaxKg()
            ExerciseReferenceWeights(
                exerciseId = exerciseId,
                previousWeightKg = previousWeight,
                prWeightKg = pr?.prWeight,
                estimatedOneRepMaxKg = oneRepMax,
                suggestedWeightKg = oneRepMax?.let { computeSuggestedWeight(it, workoutExerciseMap[exerciseId]) },
            )
        }
    }
}

/**
 * Returns true if the given weight/reps combination represents a new personal record for the exercise.
 *
 * Comparison is based on the weight-to-reps ratio (weight / reps) to normalise across different rep ranges.
 * [existing] pins the timestamp cutoff for the comparison window (logs at or before [SetLogRef.loggedAt]
 * are considered). When [SetLogRef.id] is non-null, the underlying query uses a strict
 * (loggedAt, id) cutoff: logs with the same timestamp and a higher id are also excluded. This covers
 * the update case where a set's previous ratio must not influence its own re-evaluation.
 *
 * Defensive guard: @Min(1) on request DTOs already blocks reps<=0 at the API boundary;
 * this branch protects against bypassed validation or future callers that skip the handler.
 */
internal suspend fun SessionQueryRepository.calculateIsPr(
    userId: UUID,
    exerciseId: UUID,
    weight: BigDecimal,
    reps: Int,
    existing: SetLogRef? = null,
): Boolean {
    if (reps <= 0) return false
    val beforeDate = existing?.loggedAt ?: Instant.now()
    val prevMaxRatio =
        findMaxRatioForExercise(userId, exerciseId, beforeDate, existing?.id)
    val currentRatio = weight.divide(reps.toBigDecimal(), PR_RATIO_SCALE, RoundingMode.HALF_UP)
    return prevMaxRatio == null || currentRatio > prevMaxRatio
}

private fun PersonalRecordRow?.toEstimatedOneRepMaxKg(): BigDecimal? {
    if (this?.prWeight == null || prReps == null) {
        return null
    }
    return epley(prWeight, prReps)
}
