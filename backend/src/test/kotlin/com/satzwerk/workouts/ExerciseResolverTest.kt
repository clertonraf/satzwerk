package com.satzwerk.workouts

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class ExerciseResolverTest {
    private val exerciseRepository: ExerciseRepository = mock()
    private val resolver = ExerciseResolver(exerciseRepository)
    private val userId: UUID = UUID.randomUUID()

    @Test
    fun `empty input returns empty map without touching repository`(): Unit =
        runBlocking {
            val result = resolver.resolve(userId, emptyMap())

            assertTrue(result.isEmpty())
            verify(exerciseRepository, never()).findAllByUserIdAndNamesLowercase(any(), any())
            verify(exerciseRepository, never()).saveAll(any<Iterable<Exercise>>())
        }

    @Test
    fun `all exercises already exist - returns them keyed by lowercase name without creating new ones`(): Unit =
        runBlocking {
            val existing =
                Exercise(
                    id = UUID.randomUUID(),
                    userId = userId,
                    name = "Bench Press",
                    muscleGroup = "Chest",
                )
            whenever(exerciseRepository.findAllByUserIdAndNamesLowercase(userId, setOf("bench press")))
                .thenReturn(listOf(existing))

            val result = resolver.resolve(userId, mapOf("Bench Press" to "Chest"))

            assertEquals(1, result.size)
            assertEquals(existing, result["bench press"])
            verify(exerciseRepository, never()).saveAll(any<Iterable<Exercise>>())
        }

    @Test
    fun `no exercises exist - creates all with correct names and muscle groups`(): Unit =
        runBlocking {
            whenever(exerciseRepository.findAllByUserIdAndNamesLowercase(any(), any()))
                .thenReturn(emptyList())
            val saved =
                Exercise(
                    id = UUID.randomUUID(),
                    userId = userId,
                    name = "Squat",
                    muscleGroup = "Legs",
                )
            whenever(exerciseRepository.saveAll(any<Iterable<Exercise>>()))
                .thenReturn(flowOf(saved))

            val result = resolver.resolve(userId, mapOf("Squat" to "Legs"))

            assertEquals(1, result.size)
            assertEquals(saved, result["squat"])
        }

    @Test
    fun `mixed - existing exercises returned and new ones created and merged`(): Unit =
        runBlocking {
            val existing =
                Exercise(
                    id = UUID.randomUUID(),
                    userId = userId,
                    name = "Bench Press",
                    muscleGroup = "Chest",
                )
            whenever(
                exerciseRepository.findAllByUserIdAndNamesLowercase(userId, setOf("bench press", "squat")),
            ).thenReturn(listOf(existing))
            val saved =
                Exercise(
                    id = UUID.randomUUID(),
                    userId = userId,
                    name = "Squat",
                    muscleGroup = "Legs",
                )
            whenever(exerciseRepository.saveAll(any<Iterable<Exercise>>()))
                .thenReturn(flowOf(saved))

            val result = resolver.resolve(userId, mapOf("Bench Press" to "Chest", "Squat" to "Legs"))

            assertEquals(2, result.size)
            assertEquals(existing, result["bench press"])
            assertEquals(saved, result["squat"])
        }

    @Test
    fun `case-insensitive match - existing 'bench press' resolves 'Bench Press' without duplicate`(): Unit =
        runBlocking {
            val existing =
                Exercise(
                    id = UUID.randomUUID(),
                    userId = userId,
                    name = "bench press",
                    muscleGroup = "Chest",
                )
            whenever(exerciseRepository.findAllByUserIdAndNamesLowercase(userId, setOf("bench press")))
                .thenReturn(listOf(existing))

            val result = resolver.resolve(userId, mapOf("Bench Press" to "Chest"))

            assertEquals(1, result.size)
            assertEquals(existing, result["bench press"])
            verify(exerciseRepository, never()).saveAll(any<Iterable<Exercise>>())
        }

    @Test
    fun `new exercise is created with muscle group from input map`(): Unit =
        runBlocking {
            whenever(exerciseRepository.findAllByUserIdAndNamesLowercase(any(), any()))
                .thenReturn(emptyList())
            val saved =
                Exercise(
                    id = UUID.randomUUID(),
                    userId = userId,
                    name = "Deadlift",
                    muscleGroup = "Back",
                )
            whenever(exerciseRepository.saveAll(any<Iterable<Exercise>>()))
                .thenReturn(flowOf(saved))

            resolver.resolve(userId, mapOf("Deadlift" to "Back"))

            val captor = argumentCaptor<Iterable<Exercise>>()
            verify(exerciseRepository).saveAll(captor.capture())
            val toCreate = captor.firstValue.toList()
            assertEquals(1, toCreate.size)
            assertEquals("Deadlift", toCreate[0].name)
            assertEquals("Back", toCreate[0].muscleGroup)
            assertEquals(userId, toCreate[0].userId)
        }
}
