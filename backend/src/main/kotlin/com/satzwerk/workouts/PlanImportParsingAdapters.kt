package com.satzwerk.workouts

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Component

@Component
class PlanImportParsingAdapters(
    private val planImportFilenameNormalizer: PlanImportFilenameNormalizer,
    private val planImportTechniqueParser: PlanImportTechniqueParser,
    private val planImportRepsParser: PlanImportRepsParser,
) {
    fun normalizeFilename(filename: String): String = planImportFilenameNormalizer.normalize(filename)

    fun parseTechnique(raw: String?): String? = planImportTechniqueParser.parse(raw)

    fun parseReps(repsNode: JsonNode): PlanImportReps = planImportRepsParser.parse(repsNode)
}
