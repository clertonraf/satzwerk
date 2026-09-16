package com.satzwerk.sessions

import com.satzwerk.common.NotFoundException
import com.satzwerk.common.TransactionRunner
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

private data class SetLogBatchDependencies(
    val setLogRepository: SetLogRepository,
    val setLogWriteRepository: SetLogWriteRepository,
    val sessionQueryRepository: SessionQueryRepository,
)

@Service
class SetLogService(
    private val setLogRepository: SetLogRepository,
    private val setLogWriteRepository: SetLogWriteRepository,
    private val sessionQueryRepository: SessionQueryRepository,
    private val analyticsReadCache: com.satzwerk.analytics.AnalyticsReadCache,
    private val transactionRunner: TransactionRunner,
) {
    suspend fun add(
        session: WorkoutSession,
        request: AddSetLogRequest,
    ): SetLogResponse {
        return transactionRunner.required {
            val response =
                addSetLogInCurrentTransaction(
                    session = session,
                    request = request,
                    loggedAt = Instant.now(),
                    setLogWriteRepository = setLogWriteRepository,
                )
            transactionRunner.afterCommit {
                analyticsReadCache.invalidateUser(session.userId)
            }
            response
        }
    }

    suspend fun batch(
        session: WorkoutSession,
        request: BatchSetLogRequest,
    ): BatchSetLogResponse =
        transactionRunner.required {
            // Add operations share one timestamp anchor so later batch ops see earlier
            // successful siblings during PR comparison while preserving request order.
            val batchLoggedAt = Instant.now()
            val dependencies =
                SetLogBatchDependencies(
                    setLogRepository = setLogRepository,
                    setLogWriteRepository = setLogWriteRepository,
                    sessionQueryRepository = sessionQueryRepository,
                )
            val results =
                request.operations.map { operation ->
                    processBatchOperation(
                        operation = operation,
                        session = session,
                        batchLoggedAt = batchLoggedAt,
                        dependencies = dependencies,
                    )
                }
            if (results.any(BatchSetLogOperationResult::succeeded)) {
                transactionRunner.afterCommit {
                    analyticsReadCache.invalidateUser(session.userId)
                }
            }
            BatchSetLogResponse(results)
        }

    suspend fun update(
        session: WorkoutSession,
        setLogId: UUID,
        request: UpdateSetLogRequest,
    ): SetLogResponse {
        return transactionRunner.required {
            val response =
                updateSetLogInCurrentTransaction(
                    session = session,
                    setLogId = setLogId,
                    request = request,
                    setLogRepository = setLogRepository,
                    sessionQueryRepository = sessionQueryRepository,
                )
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
            deleteSetLogInCurrentTransaction(
                sessionId = requireNotNull(session.id),
                setLogId = setLogId,
                setLogRepository = setLogRepository,
            )
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

private suspend fun addSetLogInCurrentTransaction(
    session: WorkoutSession,
    request: AddSetLogRequest,
    loggedAt: Instant,
    setLogWriteRepository: SetLogWriteRepository,
): SetLogResponse =
    setLogWriteRepository.insertWithCalculatedPr(
        session.userId,
        SetLog(
            workoutSessionId = requireNotNull(session.id),
            exerciseId = request.exerciseId,
            setNumber = request.setNumber,
            weight = request.weight,
            reps = request.reps,
            rir = request.rir,
            loggedAt = loggedAt,
        ),
    ).toResponse()

private suspend fun updateSetLogInCurrentTransaction(
    session: WorkoutSession,
    setLogId: UUID,
    request: UpdateSetLogRequest,
    setLogRepository: SetLogRepository,
    sessionQueryRepository: SessionQueryRepository,
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
    ).toResponse()
}

private suspend fun deleteSetLogInCurrentTransaction(
    sessionId: UUID,
    setLogId: UUID,
    setLogRepository: SetLogRepository,
) {
    setLogRepository.findByIdAndWorkoutSessionId(setLogId, sessionId)
        ?: throw NotFoundException("Set log not found")
    setLogRepository.deleteById(setLogId)
}

private fun AddBatchSetLogOperationRequest.toRequest(): AddSetLogRequest =
    AddSetLogRequest(
        exerciseId = exerciseId,
        setNumber = setNumber,
        weight = weight,
        reps = reps,
        rir = rir,
    )

private fun UpdateBatchSetLogOperationRequest.toRequest(): UpdateSetLogRequest =
    UpdateSetLogRequest(
        weight = weight,
        reps = reps,
        rir = rir,
        rirProvided = true,
    )

private fun BatchSetLogOperationRequest.typeName(): String =
    when (this) {
        is AddBatchSetLogOperationRequest -> "add-set"
        is UpdateBatchSetLogOperationRequest -> "update-set"
        is DeleteBatchSetLogOperationRequest -> "delete-set"
    }

private suspend fun processBatchOperation(
    operation: BatchSetLogOperationRequest,
    session: WorkoutSession,
    batchLoggedAt: Instant,
    dependencies: SetLogBatchDependencies,
): BatchSetLogOperationResult =
    try {
        when (operation) {
            is AddBatchSetLogOperationRequest ->
                BatchSetLogOperationResult(
                    type = "add-set",
                    succeeded = true,
                    setLog =
                        addSetLogInCurrentTransaction(
                            session = session,
                            request = operation.toRequest(),
                            loggedAt = batchLoggedAt,
                            setLogWriteRepository = dependencies.setLogWriteRepository,
                        ),
                    error = null,
                )

            is UpdateBatchSetLogOperationRequest -> {
                updateSetLogInCurrentTransaction(
                    session = session,
                    setLogId = operation.setLogId,
                    request = operation.toRequest(),
                    setLogRepository = dependencies.setLogRepository,
                    sessionQueryRepository = dependencies.sessionQueryRepository,
                )
                BatchSetLogOperationResult(
                    type = "update-set",
                    succeeded = true,
                    setLog = null,
                    error = null,
                )
            }

            is DeleteBatchSetLogOperationRequest -> {
                deleteSetLogInCurrentTransaction(
                    sessionId = requireNotNull(session.id),
                    setLogId = operation.setLogId,
                    setLogRepository = dependencies.setLogRepository,
                )
                BatchSetLogOperationResult(
                    type = "delete-set",
                    succeeded = true,
                    setLog = null,
                    error = null,
                )
            }
        }
    } catch (error: NotFoundException) {
        BatchSetLogOperationResult(
            type = operation.typeName(),
            succeeded = false,
            setLog = null,
            error = error.message,
        )
    }
