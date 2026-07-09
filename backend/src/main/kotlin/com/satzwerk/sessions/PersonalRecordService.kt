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
    suspend fun history(userId: UUID): List<WorkoutSessionResponse> =
        sessionQueryRepository.findHistoryWithDetails(userId).map { row ->
            WorkoutSessionResponse(
                id = row.id,
                workoutGroupId = row.workoutGroupId,
                workoutGroupTitle = row.workoutGroupTitle,
                startedAt = row.startedAt,
                completedAt = row.completedAt,
                notes = row.notes,
                setLogs = emptyList(),
                setCount = row.setCount,
            )
        }

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
        return sessionQueryRepository.findReferenceWeights(userId, exerciseIds, sessionId, workoutExerciseMap)
    }
}

/**
 * Returns true if the given weight/reps combination represents a new personal record for the exercise.
 *
 * Comparison is based on the weight-to-reps ratio (weight / reps) to normalise across different rep ranges.
 * [existing] pins the timestamp cutoff for the comparison window (logs at or before [SetLogRef.loggedAt]
 * are considered). When [SetLogRef.id] is non-null the identified set log is excluded from the comparison,
 * which is used when re-evaluating PR status during an update.
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
