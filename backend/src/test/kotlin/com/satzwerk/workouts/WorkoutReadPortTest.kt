package com.satzwerk.workouts

import com.satzwerk.sessions.SetLog
import com.satzwerk.sessions.SetLogRepository
import com.satzwerk.sessions.WorkoutSession
import com.satzwerk.sessions.WorkoutSessionRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class WorkoutReadPortTest {
    @Test
    fun `findExportPlans returns plans with ordered groups and exercises`(): Unit =
        runBlocking {
            val fixture = buildExportPlanFixture()
            val exportPlans = fixture.port.findExportPlans(fixture.userId)

            assertEquals(1, exportPlans.size)
            assertEquals(fixture.planId, exportPlans.single().plan.id)
            assertEquals(listOf("Push Day", "Pull Day"), exportPlans.single().groups.map { it.group.title })
            assertEquals(1, exportPlans.single().groups.first().exercises.size)
            assertEquals(fixture.exerciseId, exportPlans.single().groups.first().exercises.single().exerciseId)
        }

    @Test
    fun `findExportSessions groups set logs by workout session`(): Unit =
        runBlocking {
            val userId = UUID.randomUUID()
            val sessionId = UUID.randomUUID()
            val workoutGroupId = UUID.randomUUID()
            val exerciseId = UUID.randomUUID()
            val firstLog =
                SetLog(
                    id = UUID.randomUUID(),
                    workoutSessionId = sessionId,
                    exerciseId = exerciseId,
                    setNumber = 1,
                    weight = BigDecimal("80.0"),
                    reps = 8,
                    loggedAt = Instant.parse("2026-01-01T10:00:00Z"),
                )
            val secondLog =
                firstLog.copy(
                    id = UUID.randomUUID(),
                    setNumber = 2,
                    loggedAt = Instant.parse("2026-01-01T10:03:00Z"),
                )

            val workoutSessionRepository =
                mock<WorkoutSessionRepository> {
                    onBlocking { findAllByUserId(userId) } doReturn
                        listOf(
                            WorkoutSession(
                                id = sessionId,
                                userId = userId,
                                workoutGroupId = workoutGroupId,
                            ),
                        )
                }
            val setLogRepository =
                mock<SetLogRepository> {
                    onBlocking { findAllByWorkoutSessionIdIn(listOf(sessionId)) } doReturn listOf(firstLog, secondLog)
                }

            val port =
                buildWorkoutReadPort(
                    workoutPlanRepository = mock(),
                    workoutGroupRepository = mock(),
                    workoutExerciseRepository = mock(),
                    workoutSessionRepository = workoutSessionRepository,
                    setLogRepository = setLogRepository,
                )

            val exportSessions = port.findExportSessions(userId)

            assertEquals(1, exportSessions.size)
            assertEquals(sessionId, exportSessions.single().session.id)
            assertEquals(listOf(1, 2), exportSessions.single().setLogs.map { it.setNumber })
        }
}

private data class ExportPlanFixture(
    val userId: UUID,
    val planId: UUID,
    val exerciseId: UUID,
    val port: WorkoutReadPort,
)

private fun buildExportPlanFixture(): ExportPlanFixture {
    val userId = UUID.randomUUID()
    val planId = UUID.randomUUID()
    val firstGroupId = UUID.randomUUID()
    val secondGroupId = UUID.randomUUID()
    val exerciseId = UUID.randomUUID()

    val workoutPlanRepository = buildPlanRepository(userId, planId)
    val workoutGroupRepository = buildGroupRepository(planId, firstGroupId, secondGroupId)
    val workoutExerciseRepository = buildExerciseRepository(firstGroupId, secondGroupId, exerciseId)
    return ExportPlanFixture(
        userId = userId,
        planId = planId,
        exerciseId = exerciseId,
        port =
            buildWorkoutReadPort(
                workoutPlanRepository = workoutPlanRepository,
                workoutGroupRepository = workoutGroupRepository,
                workoutExerciseRepository = workoutExerciseRepository,
                workoutSessionRepository = mock(),
                setLogRepository = mock(),
            ),
    )
}

private fun buildPlanRepository(
    userId: UUID,
    planId: UUID,
) = mock<WorkoutPlanRepository> {
    onBlocking { findAllByUserId(userId) } doReturn
        listOf(
            WorkoutPlan(id = planId, userId = userId, name = "Push Pull Legs"),
        )
}

private fun buildGroupRepository(
    planId: UUID,
    firstGroupId: UUID,
    secondGroupId: UUID,
) = mock<WorkoutGroupRepository> {
    onBlocking { findAllByWorkoutPlanIdOrderByOrderIndex(planId) } doReturn
        listOf(
            WorkoutGroup(
                id = firstGroupId,
                workoutPlanId = planId,
                title = "Push Day",
                orderIndex = 0,
            ),
            WorkoutGroup(
                id = secondGroupId,
                workoutPlanId = planId,
                title = "Pull Day",
                orderIndex = 1,
            ),
        )
}

private fun buildExerciseRepository(
    firstGroupId: UUID,
    secondGroupId: UUID,
    exerciseId: UUID,
) = mock<WorkoutExerciseRepository> {
    onBlocking { findAllByWorkoutGroupIdOrderByOrderIndex(firstGroupId) } doReturn
        listOf(
            WorkoutExercise(
                id = UUID.randomUUID(),
                workoutGroupId = firstGroupId,
                exerciseId = exerciseId,
                sets = 4,
                reps = 8,
                orderIndex = 0,
            ),
        )
    onBlocking { findAllByWorkoutGroupIdOrderByOrderIndex(secondGroupId) } doReturn emptyList()
}

private fun buildWorkoutReadPort(
    workoutPlanRepository: WorkoutPlanRepository,
    workoutGroupRepository: WorkoutGroupRepository,
    workoutExerciseRepository: WorkoutExerciseRepository,
    workoutSessionRepository: WorkoutSessionRepository,
    setLogRepository: SetLogRepository,
) = WorkoutReadPort(
    workoutPlanRepository = workoutPlanRepository,
    workoutGroupRepository = workoutGroupRepository,
    workoutExerciseRepository = workoutExerciseRepository,
    workoutSessionRepository = workoutSessionRepository,
    setLogRepository = setLogRepository,
    databaseClient = mock(),
)
