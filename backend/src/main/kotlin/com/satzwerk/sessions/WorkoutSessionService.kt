package com.satzwerk.sessions

import com.satzwerk.common.BadRequestException
import com.satzwerk.common.ConflictException
import com.satzwerk.common.NotFoundException
import com.satzwerk.workouts.WorkoutExerciseRepository
import com.satzwerk.workouts.WorkoutGroup
import com.satzwerk.workouts.WorkoutGroupRepository
import com.satzwerk.workouts.WorkoutPlanDetailResponse
import com.satzwerk.workouts.WorkoutPlanService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

private const val PR_RATIO_SCALE = 10

@Suppress("TooManyFunctions")
@Service
class WorkoutSessionService(
    private val workoutSessionRepository: WorkoutSessionRepository,
    private val setLogRepository: SetLogRepository,
    private val workoutGroupRepository: WorkoutGroupRepository,
    private val workoutExerciseRepository: WorkoutExerciseRepository,
    private val workoutPlanService: WorkoutPlanService,
    private val sessionQueryRepository: SessionQueryRepository,
) {
    suspend fun start(
        userId: UUID,
        workoutGroupId: UUID,
    ): WorkoutSessionResponse {
        val group = requireGroupInActivePlan(userId, workoutGroupId)
        workoutSessionRepository.findByUserIdAndCompletedAtIsNull(userId)?.let {
            throw ConflictException("User already has an open workout session")
        }

        val session = workoutSessionRepository.save(WorkoutSession(userId = userId, workoutGroupId = workoutGroupId))
        return session.toResponse(emptyList(), group.title)
    }

    private suspend fun requireGroupInActivePlan(
        userId: UUID,
        workoutGroupId: UUID,
    ): WorkoutGroup {
        val group =
            workoutGroupRepository.findById(workoutGroupId)
                ?: throw NotFoundException("Workout group not found")
        // getRequiredPlan enforces ownership (ForbiddenException if plan belongs to another user).
        workoutPlanService.getRequiredPlan(userId, group.workoutPlanId)
        val activePlan = workoutPlanService.requireActivePlan(userId)
        if (group.workoutPlanId != activePlan.id) {
            throw BadRequestException("Cannot start a session for a group belonging to an inactive workout plan")
        }
        return group
    }

    suspend fun getStartOptions(userId: UUID): WorkoutPlanDetailResponse = workoutPlanService.getActiveDetail(userId)

    suspend fun getOpenPlanDetail(userId: UUID): WorkoutPlanDetailResponse {
        val session =
            workoutSessionRepository.findByUserIdAndCompletedAtIsNull(userId)
                ?: throw NotFoundException("Open workout session not found")
        val group =
            workoutGroupRepository.findById(session.workoutGroupId)
                ?: throw NotFoundException("Workout group not found")
        return workoutPlanService.getDetail(userId, group.workoutPlanId)
    }

    suspend fun getOpen(userId: UUID): WorkoutSessionResponse {
        val session =
            workoutSessionRepository.findByUserIdAndCompletedAtIsNull(userId)
                ?: throw NotFoundException("Open workout session not found")
        val group =
            workoutGroupRepository.findById(session.workoutGroupId)
                ?: throw NotFoundException("Workout group not found")
        return session.toResponse(loadSetLogs(requireNotNull(session.id)), group.title)
    }

    suspend fun addSetLog(
        userId: UUID,
        sessionId: UUID,
        request: AddSetLogRequest,
    ): SetLogResponse {
        val session = getOwnedSession(userId, sessionId)
        requireOpenSession(session)

        val now = Instant.now()
        val isPr = calculateIsPr(userId, request.exerciseId, request.weight, request.reps, SetLogRef(null, now))

        return setLogRepository.save(
            SetLog(
                workoutSessionId = requireNotNull(session.id),
                exerciseId = request.exerciseId,
                setNumber = request.setNumber,
                weight = request.weight,
                reps = request.reps,
                loggedAt = now,
                isPr = isPr,
            ),
        ).toResponse()
    }

    suspend fun updateSetLog(
        userId: UUID,
        sessionId: UUID,
        setLogId: UUID,
        request: UpdateSetLogRequest,
    ): SetLogResponse {
        val session = getOwnedSession(userId, sessionId)
        requireOpenSession(session)

        val setLog =
            setLogRepository.findByIdAndWorkoutSessionId(setLogId, sessionId)
                ?: throw NotFoundException("Set log not found")

        val isPr =
            calculateIsPr(
                userId,
                setLog.exerciseId,
                request.weight,
                request.reps,
                SetLogRef(requireNotNull(setLog.id), setLog.loggedAt),
            )

        return setLogRepository.save(
            setLog.copy(
                weight = request.weight,
                reps = request.reps,
                isPr = isPr,
            ),
        ).toResponse()
    }

    suspend fun complete(
        userId: UUID,
        sessionId: UUID,
        request: CompleteSessionRequest,
    ): WorkoutSessionResponse {
        val session = getOwnedSession(userId, sessionId)
        requireOpenSession(session)

        val completedSession =
            workoutSessionRepository.save(
                session.copy(
                    completedAt = Instant.now(),
                    notes = request.notes,
                ),
            )

        val group =
            workoutGroupRepository.findById(session.workoutGroupId)
                ?: throw NotFoundException("Workout group not found")
        return completedSession.toResponse(loadSetLogs(sessionId), group.title)
    }

    @Transactional
    suspend fun deleteSetLog(
        userId: UUID,
        sessionId: UUID,
        setLogId: UUID,
    ) {
        val session = getOwnedSession(userId, sessionId)
        requireOpenSession(session)
        setLogRepository.findByIdAndWorkoutSessionId(setLogId, sessionId)
            ?: throw NotFoundException("Set log not found")
        setLogRepository.deleteById(setLogId)
    }

    @Transactional
    suspend fun discard(
        userId: UUID,
        sessionId: UUID,
    ) {
        val session = getOwnedSession(userId, sessionId)
        requireOpenSession(session)
        setLogRepository.deleteAllByWorkoutSessionId(sessionId)
        workoutSessionRepository.deleteById(sessionId)
    }

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

    suspend fun getById(
        userId: UUID,
        sessionId: UUID,
    ): WorkoutSessionResponse {
        val session = getOwnedSession(userId, sessionId)
        val group =
            workoutGroupRepository.findById(session.workoutGroupId)
                ?: throw NotFoundException("Workout group not found")
        val setLogs = loadSetLogs(sessionId)
        return session.toResponse(setLogs, group.title)
    }

    suspend fun getReferenceWeights(
        userId: UUID,
        sessionId: UUID,
    ): List<ExerciseReferenceWeights> {
        val session = getOwnedSession(userId, sessionId)
        val workoutExercises =
            workoutExerciseRepository.findAllByWorkoutGroupIdOrderByOrderIndex(session.workoutGroupId)
        // De-duplicate by exerciseId keeping the first occurrence (lowest orderIndex), matching UI order.
        val uniqueExercises = workoutExercises.distinctBy { it.exerciseId }
        val exerciseIds = uniqueExercises.map { it.exerciseId }
        val workoutExerciseMap = uniqueExercises.associateBy { it.exerciseId }

        return sessionQueryRepository.findReferenceWeights(userId, exerciseIds, sessionId, workoutExerciseMap)
    }

    private suspend fun getOwnedSession(
        userId: UUID,
        sessionId: UUID,
    ): WorkoutSession =
        workoutSessionRepository.findByIdAndUserId(sessionId, userId)
            ?: throw NotFoundException("Workout session not found")

    private suspend fun loadSetLogs(sessionId: UUID): List<SetLogResponse> =
        setLogRepository.findAllByWorkoutSessionId(sessionId)
            .sortedWith(compareBy(SetLog::setNumber, SetLog::loggedAt))
            .map(SetLog::toResponse)

    // Defensive guard: @Min(1) on request DTOs already blocks reps<=0 at the API boundary;
    // this branch protects against bypassed validation or future callers that skip the handler.
    private suspend fun calculateIsPr(
        userId: UUID,
        exerciseId: UUID,
        weight: BigDecimal,
        reps: Int,
        existing: SetLogRef? = null,
    ): Boolean {
        if (reps <= 0) return false
        val beforeDate = existing?.loggedAt ?: Instant.now()
        val prevMaxRatio =
            sessionQueryRepository.findMaxRatioForExercise(userId, exerciseId, beforeDate, existing?.id)
        val currentRatio = weight.divide(reps.toBigDecimal(), PR_RATIO_SCALE, RoundingMode.HALF_UP)
        return prevMaxRatio == null || currentRatio > prevMaxRatio
    }
}

private data class SetLogRef(val id: UUID?, val loggedAt: Instant)

private fun requireOpenSession(session: WorkoutSession) {
    if (session.completedAt != null) {
        throw ConflictException("Workout session is already completed")
    }
}

fun WorkoutSession.toResponse(
    setLogs: List<SetLogResponse>,
    workoutGroupTitle: String,
): WorkoutSessionResponse =
    WorkoutSessionResponse(
        id = requireNotNull(id),
        workoutGroupId = workoutGroupId,
        workoutGroupTitle = workoutGroupTitle,
        startedAt = startedAt,
        completedAt = completedAt,
        notes = notes,
        setLogs = setLogs,
        setCount = setLogs.size,
    )

fun SetLog.toResponse(): SetLogResponse =
    SetLogResponse(
        id = requireNotNull(id),
        exerciseId = exerciseId,
        setNumber = setNumber,
        weight = weight,
        reps = reps,
        loggedAt = loggedAt,
    )
