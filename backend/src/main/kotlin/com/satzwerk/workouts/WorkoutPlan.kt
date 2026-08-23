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

enum class AdvancedTechnique(
    val label: String,
    val description: String,
    val restSeconds: Int?,
    val parserAliases: List<String>,
) {
    SST(
        label = "SST",
        description =
            "Perform until muscular failure, then immediately drop the load by 20-30% three consecutive " +
                "times with zero rest to push the muscle past its normal limits.",
        restSeconds = 0,
        parserAliases = listOf("strip"),
    ),
    REST_PAUSE(
        label = "REST PAUSE",
        description =
            "Perform until muscular failure, resting for a brief 15 to 20 seconds, and then immediately " +
                "performing a few more reps with the same weight to maximize high-intensity muscle stimulation.",
        restSeconds = 20,
        parserAliases = listOf("rest"),
    ),
    GVT(
        label = "GVT",
        description =
            "Perform a massive workload of 10 sets of 10 repetitions for a single exercise using a strict " +
                "60-second rest interval between sets. The weight remains identical for all 100 total reps, " +
                "typically set at about 60% of your one-repetition maximum to trigger extreme hypertrophy.",
        restSeconds = 60,
        parserAliases = listOf("gvt"),
    ),
    FST_7(
        label = "FST-7",
        description =
            "Perform 7 high-intensity sets of 10 to 12 repetitions for your final exercise, restricting " +
                "your rest periods to a strict 30 to 45 seconds between sets.",
        restSeconds = 30,
        parserAliases = listOf("fst"),
    ),
    GIRONDA(
        label = "GIRONDA",
        description =
            "Perform 8 sets of 8 repetitions for an exercise while aggressively keeping rest intervals " +
                "down to just 15 to 30 seconds.",
        restSeconds = 30,
        parserAliases = listOf("gironda"),
    ), ;

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
    @Column("activated_at")
    val activatedAt: Instant? = null,
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
