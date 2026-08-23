package com.satzwerk.workouts

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Component

data class PlanImportReps(
    val reps: Int,
    val toFailure: Boolean,
)

@Component
class PlanImportRepsParser {
    fun parse(repsNode: JsonNode): PlanImportReps =
        if (repsNode.isTextual && repsNode.asText().uppercase() == "F") {
            PlanImportReps(reps = 0, toFailure = true)
        } else {
            PlanImportReps(reps = repsNode.asInt(), toFailure = false)
        }
}
