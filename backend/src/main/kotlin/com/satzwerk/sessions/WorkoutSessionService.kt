package com.satzwerk.sessions

import com.satzwerk.common.BadRequestException
import com.satzwerk.common.ConflictException
import com.satzwerk.common.NotFoundException
import com.satzwerk.workouts.WorkoutGroupRepository
import com.satzwerk.workouts.WorkoutPlan
import com.satzwerk.workouts.WorkoutPlanDetailResponse
import com.satzwerk.workouts.WorkoutPlanService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class WorkoutSessionService(
    private val workoutSessionRepository: WorkoutSessionRepository,
    private val workoutGroupRepository: WorkoutGroupRepository,
    private val workoutPlanService: WorkoutPlanService,
    private val personalRecordService: PersonalRecordService,
    private val setLogService: SetLogService,
) {
    suspend fun getStartOptions(userId: UUID): WorkoutPlanDetailResponse = workoutPlanService.getActiveDetail(userId)

    suspend fun start(
        userId: UUID,
        workoutGroupId: UUID,
    ): WorkoutSessionResponse {
        val group =
            workoutGroupRepository.findById(workoutGroupId)
                ?: throw NotFoundException("Workout group not found")
        val plan = workoutPlanService.getRequiredPlan(userId, group.workoutPlanId)
        requireActivePlan(plan)
        workoutSessionRepository.findByUserIdAndCompletedAtIsNull(userId)?.let {
            throw ConflictException("User already has an open workout session")
        }

        val session = workoutSessionRepository.save(WorkoutSession(userId = userId, workoutGroupId = workoutGroupId))
        return session.toResponse(emptyList(), group.title)
    }

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
        return session.toResponse(setLogService.loadSetLogs(requireNotNull(session.id)), group.title)
    }

    suspend fun complete(
        userId: UUID,
        sessionId: UUID,
        request: CompleteSessionRequest,
    ): WorkoutSessionResponse {
        val session = requireOwnedSession(userId, sessionId, workoutSessionRepository)
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
        return completedSession.toResponse(setLogService.loadSetLogs(sessionId), group.title)
    }

    @Transactional
    suspend fun discard(
        userId: UUID,
        sessionId: UUID,
    ) {
        val session = requireOwnedSession(userId, sessionId, workoutSessionRepository)
        requireOpenSession(session)
        setLogService.clearSetLogs(sessionId)
        workoutSessionRepository.deleteById(sessionId)
    }

    suspend fun history(userId: UUID): List<WorkoutSessionResponse> = personalRecordService.history(userId)

    suspend fun getById(
        userId: UUID,
        sessionId: UUID,
    ): WorkoutSessionResponse {
        val session = requireOwnedSession(userId, sessionId, workoutSessionRepository)
        val group =
            workoutGroupRepository.findById(session.workoutGroupId)
                ?: throw NotFoundException("Workout group not found")
        return session.toResponse(setLogService.loadSetLogs(sessionId), group.title)
    }

    suspend fun getReferenceWeights(
        userId: UUID,
        sessionId: UUID,
    ): List<ExerciseReferenceWeights> {
        val session = requireOwnedSession(userId, sessionId, workoutSessionRepository)
        return personalRecordService.findReferenceWeights(userId, session.workoutGroupId, sessionId)
    }

    /** Returns the session after verifying ownership and that it is still open (not completed). */
    suspend fun requireOwnedOpenSession(
        userId: UUID,
        sessionId: UUID,
    ): WorkoutSession {
        val session = requireOwnedSession(userId, sessionId, workoutSessionRepository)
        requireOpenSession(session)
        return session
    }
}

internal suspend fun requireOwnedSession(
    userId: UUID,
    sessionId: UUID,
    workoutSessionRepository: WorkoutSessionRepository,
): WorkoutSession =
    workoutSessionRepository.findByIdAndUserId(sessionId, userId)
        ?: throw NotFoundException("Workout session not found")

private fun requireActivePlan(plan: WorkoutPlan) {
    if (!plan.isActive) {
        throw BadRequestException("Cannot start a session for a group belonging to an inactive workout plan")
    }
}

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
