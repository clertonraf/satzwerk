package com.satzwerk.sessions

import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

interface WorkoutSessionRepository : CoroutineCrudRepository<WorkoutSession, UUID> {
    suspend fun findByUserIdAndCompletedAtIsNull(userId: UUID): WorkoutSession?

    suspend fun findAllByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(userId: UUID): List<WorkoutSession>

    suspend fun findByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): WorkoutSession?
}

interface SetLogRepository : CoroutineCrudRepository<SetLog, UUID> {
    suspend fun findAllByWorkoutSessionId(sessionId: UUID): List<SetLog>

    suspend fun findByIdAndWorkoutSessionId(
        id: UUID,
        workoutSessionId: UUID,
    ): SetLog?

    suspend fun deleteAllByWorkoutSessionId(sessionId: UUID)
}
