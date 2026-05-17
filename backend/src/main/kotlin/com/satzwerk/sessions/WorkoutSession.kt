package com.satzwerk.sessions

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Table("workout_sessions")
data class WorkoutSession(
    @Id
    val id: UUID? = null,
    @Column("user_id")
    val userId: UUID,
    @Column("workout_group_id")
    val workoutGroupId: UUID,
    @Column("started_at")
    val startedAt: Instant = Instant.now(),
    @Column("completed_at")
    val completedAt: Instant? = null,
    val notes: String? = null,
)

@Table("set_logs")
data class SetLog(
    @Id
    val id: UUID? = null,
    @Column("workout_session_id")
    val workoutSessionId: UUID,
    @Column("exercise_id")
    val exerciseId: UUID,
    @Column("set_number")
    val setNumber: Int,
    val weight: BigDecimal,
    val reps: Int,
    @Column("logged_at")
    val loggedAt: Instant = Instant.now(),
)
