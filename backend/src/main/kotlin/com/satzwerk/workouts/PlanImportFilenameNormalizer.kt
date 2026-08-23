package com.satzwerk.workouts

import org.springframework.stereotype.Component

@Component
class PlanImportFilenameNormalizer {
    fun normalize(filename: String): String =
        filename.substringBeforeLast(".")
            .replace("_", " ")
            .replace("-", " ")
            .trim()
}
