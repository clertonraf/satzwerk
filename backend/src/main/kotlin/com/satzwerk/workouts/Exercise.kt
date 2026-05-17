package com.satzwerk.workouts

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("exercises")
data class Exercise(
    @Id
    val id: UUID? = null,
    @Column("user_id")
    val userId: UUID,
    val name: String,
    @Column("muscle_group")
    val muscleGroup: String,
    val description: String? = null,
    @Column("video_url")
    val videoUrl: String? = null,
    val equipment: String? = null,
    @Column("created_at")
    val createdAt: Instant = Instant.now(),
    @Column("updated_at")
    val updatedAt: Instant = Instant.now(),
)
