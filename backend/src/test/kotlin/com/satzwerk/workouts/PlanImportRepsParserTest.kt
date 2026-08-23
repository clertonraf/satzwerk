package com.satzwerk.workouts

import com.fasterxml.jackson.databind.node.IntNode
import com.fasterxml.jackson.databind.node.TextNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlanImportRepsParserTest {
    private val parser = PlanImportRepsParser()

    @Test
    fun `parse maps F to toFailure`() {
        assertEquals(
            PlanImportReps(reps = 0, toFailure = true),
            parser.parse(TextNode.valueOf("F")),
        )
    }

    @Test
    fun `parse keeps numeric reps when not toFailure`() {
        assertEquals(
            PlanImportReps(reps = 8, toFailure = false),
            parser.parse(IntNode.valueOf(8)),
        )
    }
}
