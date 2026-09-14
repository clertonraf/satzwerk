package com.satzwerk.sessions

import com.satzwerk.common.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class SetLogService(
    private val setLogRepository: SetLogRepository,
    private val sessionQueryRepository: SessionQueryRepository,
    private val analyticsReadCache: com.satzwerk.analytics.AnalyticsReadCache,
) {
    suspend fun add(
        session: WorkoutSession,
        request: AddSetLogRequest,
    ): SetLogResponse {
        val now = Instant.now()
        val isPr =
            sessionQueryRepository.calculateIsPr(
                session.userId,
                request.exerciseId,
                request.weight,
                request.reps,
                SetLogRef(null, now),
            )
        return setLogRepository.save(
            SetLog(
                workoutSessionId = requireNotNull(session.id),
                exerciseId = request.exerciseId,
                setNumber = request.setNumber,
                weight = request.weight,
                reps = request.reps,
                rir = request.rir,
                loggedAt = now,
                isPr = isPr,
            ),
        ).toResponse().also {
            analyticsReadCache.invalidateUser(session.userId)
        }
    }

    suspend fun update(
        session: WorkoutSession,
        setLogId: UUID,
        request: UpdateSetLogRequest,
    ): SetLogResponse {
        val setLog =
            setLogRepository.findByIdAndWorkoutSessionId(setLogId, requireNotNull(session.id))
                ?: throw NotFoundException("Set log not found")
        val isPr =
            sessionQueryRepository.calculateIsPr(
                session.userId,
                setLog.exerciseId,
                request.weight,
                request.reps,
                SetLogRef(requireNotNull(setLog.id), setLog.loggedAt),
            )
        val rir = if (request.rirProvided) request.rir else setLog.rir
        return setLogRepository.save(
            setLog.copy(weight = request.weight, reps = request.reps, rir = rir, isPr = isPr),
        ).toResponse().also {
            analyticsReadCache.invalidateUser(session.userId)
        }
    }

    @Transactional
    suspend fun delete(
        session: WorkoutSession,
        setLogId: UUID,
    ) {
        setLogRepository.findByIdAndWorkoutSessionId(setLogId, requireNotNull(session.id))
            ?: throw NotFoundException("Set log not found")
        setLogRepository.deleteById(setLogId)
        analyticsReadCache.invalidateUser(session.userId)
    }

    suspend fun loadSetLogs(sessionId: UUID): List<SetLogResponse> =
        setLogRepository.findAllByWorkoutSessionId(sessionId)
            .sortedWith(compareBy(SetLog::setNumber, SetLog::loggedAt))
            .map(SetLog::toResponse)

    @Transactional
    suspend fun clearSetLogs(session: WorkoutSession) {
        setLogRepository.deleteAllByWorkoutSessionId(requireNotNull(session.id))
        analyticsReadCache.invalidateUser(session.userId)
    }
}
