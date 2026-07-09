package com.satzwerk.sessions

import com.satzwerk.workouts.WorkoutExercise
import com.satzwerk.workouts.WorkoutExerciseRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class PersonalRecordServiceTest {
    private val userId = UUID.randomUUID()
    private val exerciseId = UUID.randomUUID()

    private fun queryRepo(prevMaxRatio: BigDecimal?): SessionQueryRepository =
        mock {
            onBlocking { findMaxRatioForExercise(any(), any(), any(), anyOrNull()) } doReturn prevMaxRatio
        }

    @Test
    fun `calculateIsPr returns true when no prior record exists`(): Unit =
        runBlocking {
            assertTrue(queryRepo(null).calculateIsPr(userId, exerciseId, BigDecimal("80"), 5))
        }

    @Test
    fun `calculateIsPr returns true when ratio beats previous record`(): Unit =
        runBlocking {
            // prev = 80/5 = 16.0; new = 90/5 = 18.0 -> PR
            assertTrue(
                queryRepo(BigDecimal("16.0000000000"))
                    .calculateIsPr(userId, exerciseId, BigDecimal("90"), 5),
            )
        }

    @Test
    fun `calculateIsPr returns false when ratio is below existing record`(): Unit =
        runBlocking {
            // prev = 80/5 = 16.0; new = 70/5 = 14.0 -> not PR
            assertFalse(
                queryRepo(BigDecimal("16.0000000000"))
                    .calculateIsPr(userId, exerciseId, BigDecimal("70"), 5),
            )
        }

    @Test
    fun `calculateIsPr returns false when ratio ties existing record`(): Unit =
        runBlocking {
            // prev = 80/5 = 16.0; new = 80/5 = 16.0 -> tie is not a PR
            assertFalse(
                queryRepo(BigDecimal("16.0000000000"))
                    .calculateIsPr(userId, exerciseId, BigDecimal("80"), 5),
            )
        }

    @Test
    fun `calculateIsPr returns false for zero or negative reps`(): Unit =
        runBlocking {
            assertFalse(queryRepo(null).calculateIsPr(userId, exerciseId, BigDecimal("80"), 0))
        }

    @Test
    fun `calculateIsPr forwards existing loggedAt and id to findMaxRatioForExercise`(): Unit =
        runBlocking {
            val existingId = UUID.randomUUID()
            val existingLoggedAt = Instant.parse("2024-06-01T10:00:00Z")
            val existing = SetLogRef(existingId, existingLoggedAt)
            val repo = queryRepo(null)

            repo.calculateIsPr(userId, exerciseId, BigDecimal("80"), 5, existing)

            verify(repo).findMaxRatioForExercise(
                eq(userId),
                eq(exerciseId),
                eq(existingLoggedAt),
                eq(existingId),
            )
        }
}

class PersonalRecordServiceAssemblyTest {
    private val userId = UUID.randomUUID()
    private val sessionId = UUID.randomUUID()
    private val groupId = UUID.randomUUID()

    private fun workoutExercise(
        id: UUID,
        reps: Int = 8,
        toFailure: Boolean = false,
    ) = WorkoutExercise(
        id = id,
        workoutGroupId = groupId,
        exerciseId = id,
        sets = 3,
        reps = reps,
        toFailure = toFailure,
    )

    @Test
    fun `findReferenceWeights returns empty list when group has no exercises`(): Unit =
        runBlocking {
            val exerciseRepo =
                mock<WorkoutExerciseRepository> {
                    onBlocking {
                        findAllByWorkoutGroupIdOrderByOrderIndex(groupId)
                    } doReturn emptyList()
                }
            val service = PersonalRecordService(mock(), exerciseRepo)

            val result = service.findReferenceWeights(userId, groupId, sessionId)

            assertTrue(result.isEmpty())
        }

    @Test
    fun `findReferenceWeights populates previousWeightKg from findPreviousWeights`(): Unit =
        runBlocking {
            val exId = UUID.randomUUID()
            val exerciseRepo =
                mock<WorkoutExerciseRepository> {
                    onBlocking {
                        findAllByWorkoutGroupIdOrderByOrderIndex(groupId)
                    } doReturn listOf(workoutExercise(exId))
                }
            val queryRepo =
                mock<SessionQueryRepository> {
                    onBlocking {
                        findPreviousWeights(eq(userId), any(), eq(sessionId))
                    } doReturn mapOf(exId to BigDecimal("75.00"))
                    onBlocking {
                        findPersonalRecords(eq(userId), any())
                    } doReturn emptyMap()
                }
            val service = PersonalRecordService(queryRepo, exerciseRepo)

            val result = service.findReferenceWeights(userId, groupId, sessionId)

            assertEquals(1, result.size)
            assertEquals(BigDecimal("75.00"), result[0].previousWeightKg)
        }

    @Test
    fun `findReferenceWeights computes estimatedOneRepMax range from personal record via Epley and Brzycki`(): Unit =
        runBlocking {
            val exId = UUID.randomUUID()
            val exerciseRepo =
                mock<WorkoutExerciseRepository> {
                    onBlocking {
                        findAllByWorkoutGroupIdOrderByOrderIndex(groupId)
                    } doReturn listOf(workoutExercise(exId, reps = 8))
                }
            val queryRepo =
                mock<SessionQueryRepository> {
                    onBlocking {
                        findPreviousWeights(eq(userId), any(), eq(sessionId))
                    } doReturn emptyMap()
                    onBlocking {
                        findPersonalRecords(eq(userId), any())
                    } doReturn
                        mapOf(
                            exId to PersonalRecordRow(exId, prWeight = BigDecimal("100.00"), prReps = 5),
                        )
                }
            val service = PersonalRecordService(queryRepo, exerciseRepo)

            val result = service.findReferenceWeights(userId, groupId, sessionId)

            // Brzycki: 100 * 36 / 32 = 112.50 (min); Epley: 100 * (1 + 5/30) = 116.67 (max)
            assertEquals(BigDecimal("112.50"), result[0].estimatedOneRepMaxMinKg)
            assertEquals(BigDecimal("116.67"), result[0].estimatedOneRepMaxMaxKg)
            assertEquals(BigDecimal("100.00"), result[0].prWeightKg)
        }

    @Test
    fun `findReferenceWeights returns null estimatedOneRepMax range when no personal record`(): Unit =
        runBlocking {
            val exId = UUID.randomUUID()
            val exerciseRepo =
                mock<WorkoutExerciseRepository> {
                    onBlocking {
                        findAllByWorkoutGroupIdOrderByOrderIndex(groupId)
                    } doReturn listOf(workoutExercise(exId))
                }
            val queryRepo =
                mock<SessionQueryRepository> {
                    onBlocking {
                        findPreviousWeights(eq(userId), any(), eq(sessionId))
                    } doReturn emptyMap()
                    onBlocking {
                        findPersonalRecords(eq(userId), any())
                    } doReturn emptyMap()
                }
            val service = PersonalRecordService(queryRepo, exerciseRepo)

            val result = service.findReferenceWeights(userId, groupId, sessionId)

            assertNull(result[0].estimatedOneRepMaxMinKg)
            assertNull(result[0].estimatedOneRepMaxMaxKg)
            assertNull(result[0].suggestedWeightKg)
        }

    @Test
    fun `findReferenceWeights computes suggestedWeightKg from Epley 1RM for internal inverse consistency`(): Unit =
        runBlocking {
            val exId = UUID.randomUUID()
            // Epley: 100 * (1 + 5/30) = 116.67; suggest = 116.67 / (1 + 8/30) = 92.11
            val exerciseRepo =
                mock<WorkoutExerciseRepository> {
                    onBlocking {
                        findAllByWorkoutGroupIdOrderByOrderIndex(groupId)
                    } doReturn listOf(workoutExercise(exId, reps = 8))
                }
            val queryRepo =
                mock<SessionQueryRepository> {
                    onBlocking {
                        findPreviousWeights(eq(userId), any(), eq(sessionId))
                    } doReturn emptyMap()
                    onBlocking {
                        findPersonalRecords(eq(userId), any())
                    } doReturn
                        mapOf(
                            exId to PersonalRecordRow(exId, prWeight = BigDecimal("100.00"), prReps = 5),
                        )
                }
            val service = PersonalRecordService(queryRepo, exerciseRepo)

            val result = service.findReferenceWeights(userId, groupId, sessionId)

            // Epley-based suggestion: 116.67 / (1 + 8/30) = 92.11
            assertEquals(BigDecimal("92.11"), result[0].suggestedWeightKg)
        }
}
