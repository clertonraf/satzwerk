package com.satzwerk.sessions

import com.satzwerk.common.ConflictException
import com.satzwerk.common.NotFoundException
import com.satzwerk.workouts.WorkoutGroupRepository
import com.satzwerk.workouts.WorkoutPlanService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class WorkoutSessionService(
    private val workoutSessionRepository: WorkoutSessionRepository,
    private val setLogRepository: SetLogRepository,
    private val workoutGroupRepository: WorkoutGroupRepository,
    private val workoutPlanService: WorkoutPlanService,
) {
    suspend fun start(
        userId: UUID,
        workoutGroupId: UUID,
    ): WorkoutSessionResponse {
        validateOwnedWorkoutGroup(userId, workoutGroupId)
        workoutSessionRepository.findByUserIdAndCompletedAtIsNull(userId)?.let {
            throw ConflictException("User already has an open workout session")
        }

        val session = workoutSessionRepository.save(WorkoutSession(userId = userId, workoutGroupId = workoutGroupId))
        return session.toResponse(emptyList())
    }

    suspend fun getOpen(userId: UUID): WorkoutSessionResponse {
        val session =
            workoutSessionRepository.findByUserIdAndCompletedAtIsNull(userId)
                ?: throw NotFoundException("Open workout session not found")
        return session.toResponse(loadSetLogs(requireNotNull(session.id)))
    }

    suspend fun addSetLog(
        userId: UUID,
        sessionId: UUID,
        request: AddSetLogRequest,
    ): SetLogResponse {
        val session = getOwnedSession(userId, sessionId)
        requireOpenSession(session)

        return setLogRepository.save(
            SetLog(
                workoutSessionId = requireNotNull(session.id),
                exerciseId = request.exerciseId,
                setNumber = request.setNumber,
                weight = request.weight,
                reps = request.reps,
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

        return setLogRepository.save(
            setLog.copy(
                weight = request.weight,
                reps = request.reps,
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

        return completedSession.toResponse(loadSetLogs(sessionId))
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
        workoutSessionRepository.findAllByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(userId)
            .map { it.toResponse(emptyList()) }

    private suspend fun validateOwnedWorkoutGroup(
        userId: UUID,
        workoutGroupId: UUID,
    ) {
        val group =
            workoutGroupRepository.findById(workoutGroupId)
                ?: throw NotFoundException("Workout group not found")
        workoutPlanService.getOwnedPlan(userId, group.workoutPlanId)
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
}

private fun requireOpenSession(session: WorkoutSession) {
    if (session.completedAt != null) {
        throw ConflictException("Workout session is already completed")
    }
}

fun WorkoutSession.toResponse(setLogs: List<SetLogResponse>): WorkoutSessionResponse =
    WorkoutSessionResponse(
        id = requireNotNull(id),
        workoutGroupId = workoutGroupId,
        startedAt = startedAt,
        completedAt = completedAt,
        notes = notes,
        setLogs = setLogs,
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
