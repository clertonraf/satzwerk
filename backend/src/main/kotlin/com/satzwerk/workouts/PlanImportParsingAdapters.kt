package com.satzwerk.workouts

import org.springframework.stereotype.Component

@Component
class PlanImportParsingAdapters(
    private val planImportFilenameNormalizer: PlanImportFilenameNormalizer,
    private val planImportTechniqueParser: PlanImportTechniqueParser,
) {
    fun normalizeFilename(filename: String): String = planImportFilenameNormalizer.normalize(filename)

    fun parseTechnique(raw: String?): String? = planImportTechniqueParser.parse(raw)
}
