package com.satzwerk.workouts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ExerciseModelsTest {
    @Test
    fun `ExerciseResponse companion maps exercise fields`() {
        val createdAt = Instant.parse("2026-08-23T10:00:00Z")
        val updatedAt = Instant.parse("2026-08-23T10:05:00Z")
        val exercise =
            Exercise(
                id = UUID.randomUUID(),
                userId = UUID.randomUUID(),
                name = "Bench Press",
                muscleGroup = "CHEST",
                description = "Flat barbell bench press",
                videoUrl = "https://example.com/bench",
                equipment = "Barbell",
                createdAt = createdAt,
                updatedAt = updatedAt,
            )

        val response = ExerciseResponse.from(exercise)

        assertEquals(exercise.id, response.id)
        assertEquals("Bench Press", response.name)
        assertEquals("CHEST", response.muscleGroup)
        assertEquals("Flat barbell bench press", response.description)
        assertEquals("https://example.com/bench", response.videoUrl)
        assertEquals("Barbell", response.equipment)
        assertEquals(createdAt, response.createdAt)
        assertEquals(updatedAt, response.updatedAt)
    }
}
