package com.satzwerk.sessions

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.math.BigDecimal
import java.util.UUID

class PersonalRecordServiceTest {
    private val userId = UUID.randomUUID()
    private val exerciseId = UUID.randomUUID()

    private fun service(prevMaxRatio: BigDecimal?): PersonalRecordService {
        val queryRepo =
            mock<SessionQueryRepository> {
                onBlocking { findMaxRatioForExercise(any(), any(), any(), anyOrNull()) } doReturn prevMaxRatio
            }
        return PersonalRecordService(queryRepo, mock())
    }

    @Test
    fun `calculateIsPr returns true when no prior record exists`(): Unit =
        runBlocking {
            val svc = service(prevMaxRatio = null)
            assertTrue(svc.calculateIsPr(userId, exerciseId, BigDecimal("80"), 5))
        }

    @Test
    fun `calculateIsPr returns true when ratio beats previous record`(): Unit =
        runBlocking {
            // prev = 80/5 = 16.0; new = 90/5 = 18.0 → PR
            val svc = service(prevMaxRatio = BigDecimal("16.0000000000"))
            assertTrue(svc.calculateIsPr(userId, exerciseId, BigDecimal("90"), 5))
        }

    @Test
    fun `calculateIsPr returns false when ratio is below existing record`(): Unit =
        runBlocking {
            // prev = 80/5 = 16.0; new = 70/5 = 14.0 → not PR
            val svc = service(prevMaxRatio = BigDecimal("16.0000000000"))
            assertFalse(svc.calculateIsPr(userId, exerciseId, BigDecimal("70"), 5))
        }

    @Test
    fun `calculateIsPr returns false when ratio ties existing record`(): Unit =
        runBlocking {
            // prev = 80/5 = 16.0; new = 80/5 = 16.0 → tie is not a PR
            val svc = service(prevMaxRatio = BigDecimal("16.0000000000"))
            assertFalse(svc.calculateIsPr(userId, exerciseId, BigDecimal("80"), 5))
        }

    @Test
    fun `calculateIsPr returns false for zero or negative reps`(): Unit =
        runBlocking {
            val svc = service(prevMaxRatio = null)
            assertFalse(svc.calculateIsPr(userId, exerciseId, BigDecimal("80"), 0))
        }
}
