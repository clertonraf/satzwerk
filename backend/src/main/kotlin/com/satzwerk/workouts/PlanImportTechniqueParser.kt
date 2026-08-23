package com.satzwerk.workouts

import org.springframework.stereotype.Component

@Component
class PlanImportTechniqueParser {
    fun parse(raw: String?): String? =
        raw?.let {
            AdvancedTechnique.fromParserString(it)?.name
        }
}
