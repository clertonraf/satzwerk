package com.satzwerk.sessions

import com.satzwerk.common.ConflictException
import com.satzwerk.common.ForbiddenException
import com.satzwerk.common.NotFoundException
import com.satzwerk.workouts.WorkoutGroupRepository
import com.satzwerk.workouts.WorkoutPlanRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class WorkoutSessionService(
    private val workoutSessionRepository: WorkoutSessionRepository,
    private val setLogRepository: SetLogRepository,
    private val workoutGroupRepository: WorkoutGroupRepository,
    private val workoutPlanRepository: WorkoutPlanRepository,
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
        val group = getRequiredWorkoutGroup(workoutGroupRepository, workoutGroupId)
        val plan = getRequiredWorkoutPlan(workoutPlanRepository, group.workoutPlanId)
        if (plan.userId != userId) {
            throw ForbiddenException("Workout group does not belong to user")
        }
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

private suspend fun getRequiredWorkoutGroup(
    workoutGroupRepository: WorkoutGroupRepository,
    workoutGroupId: UUID,
): com.satzwerk.workouts.WorkoutGroup =
    workoutGroupRepository.findById(workoutGroupId)
        ?: throw NotFoundException("Workout group not found")

private suspend fun getRequiredWorkoutPlan(
    workoutPlanRepository: WorkoutPlanRepository,
    planId: UUID,
): com.satzwerk.workouts.WorkoutPlan =
    workoutPlanRepository.findById(planId)
        ?: throw NotFoundException("Workout plan not found")

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
