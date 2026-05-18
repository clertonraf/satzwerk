package com.satzwerk.workouts

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

enum class WorkoutSource {
    MANUAL,
    IMPORTED,
}

enum class AdvancedTechnique(val parserAliases: List<String>) {
    SST(listOf("strip")),
    REST_PAUSE(listOf("rest")),
    GVT(listOf("gvt")),
    FST_7(listOf("fst")),
    GIRONDA(listOf("gironda")), ;

    companion object {
        fun fromParserString(raw: String): AdvancedTechnique? {
            val lower = raw.lowercase()
            return entries.firstOrNull { technique -> technique.parserAliases.any { lower.contains(it) } }
        }
    }
}

@Table("workout_plans")
data class WorkoutPlan(
    @Id
    val id: UUID? = null,
    @Column("user_id")
    val userId: UUID,
    val name: String,
    val source: String = WorkoutSource.MANUAL.name,
    @Column("is_active")
    val isActive: Boolean = false,
    @Column("created_at")
    val createdAt: Instant = Instant.now(),
    @Column("updated_at")
    val updatedAt: Instant = Instant.now(),
)

@Table("workout_groups")
data class WorkoutGroup(
    @Id
    val id: UUID? = null,
    @Column("workout_plan_id")
    val workoutPlanId: UUID,
    val title: String,
    @Column("order_index")
    val orderIndex: Int = 0,
    @Column("created_at")
    val createdAt: Instant = Instant.now(),
    @Column("updated_at")
    val updatedAt: Instant = Instant.now(),
)

@Table("workout_exercises")
data class WorkoutExercise(
    @Id
    val id: UUID? = null,
    @Column("workout_group_id")
    val workoutGroupId: UUID,
    @Column("exercise_id")
    val exerciseId: UUID,
    val sets: Int,
    val reps: Int,
    @Column("to_failure")
    val toFailure: Boolean = false,
    @Column("advanced_technique")
    val advancedTechnique: String? = null,
    @Column("order_index")
    val orderIndex: Int = 0,
    @Column("created_at")
    val createdAt: Instant = Instant.now(),
    @Column("updated_at")
    val updatedAt: Instant = Instant.now(),
)
