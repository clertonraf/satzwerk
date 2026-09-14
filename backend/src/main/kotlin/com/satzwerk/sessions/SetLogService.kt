package com.satzwerk.sessions

import com.satzwerk.common.NotFoundException
import com.satzwerk.common.TransactionRunner
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class SetLogService(
    private val setLogRepository: SetLogRepository,
    private val sessionQueryRepository: SessionQueryRepository,
    private val analyticsReadCache: com.satzwerk.analytics.AnalyticsReadCache,
    private val transactionRunner: TransactionRunner,
) {
    suspend fun add(
        session: WorkoutSession,
        request: AddSetLogRequest,
    ): SetLogResponse {
        return transactionRunner.required {
            val now = Instant.now()
            val isPr =
                sessionQueryRepository.calculateIsPr(
                    session.userId,
                    request.exerciseId,
                    request.weight,
                    request.reps,
                    SetLogRef(null, now),
                )
            val response =
                setLogRepository.save(
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
                ).toResponse()
            transactionRunner.afterCommit {
                analyticsReadCache.invalidateUser(session.userId)
            }
            response
        }
    }

    suspend fun update(
        session: WorkoutSession,
        setLogId: UUID,
        request: UpdateSetLogRequest,
    ): SetLogResponse {
        return transactionRunner.required {
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
            val response =
                setLogRepository.save(
                    setLog.copy(weight = request.weight, reps = request.reps, rir = rir, isPr = isPr),
                ).toResponse()
            transactionRunner.afterCommit {
                analyticsReadCache.invalidateUser(session.userId)
            }
            response
        }
    }

    suspend fun delete(
        session: WorkoutSession,
        setLogId: UUID,
    ) {
        transactionRunner.required {
            setLogRepository.findByIdAndWorkoutSessionId(setLogId, requireNotNull(session.id))
                ?: throw NotFoundException("Set log not found")
            setLogRepository.deleteById(setLogId)
            transactionRunner.afterCommit {
                analyticsReadCache.invalidateUser(session.userId)
            }
        }
    }

    suspend fun loadSetLogs(sessionId: UUID): List<SetLogResponse> =
        setLogRepository.findAllByWorkoutSessionId(sessionId)
            .sortedWith(compareBy(SetLog::setNumber, SetLog::loggedAt))
            .map(SetLog::toResponse)

    suspend fun clearSetLogs(session: WorkoutSession) {
        transactionRunner.required {
            clearSetLogsInCurrentTransaction(requireNotNull(session.id))
            transactionRunner.afterCommit {
                analyticsReadCache.invalidateUser(session.userId)
            }
        }
    }

    internal suspend fun clearSetLogsInCurrentTransaction(sessionId: UUID) {
        setLogRepository.deleteAllByWorkoutSessionId(sessionId)
    }
}
