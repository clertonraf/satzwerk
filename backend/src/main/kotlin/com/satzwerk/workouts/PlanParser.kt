package com.satzwerk.workouts

import org.springframework.http.codec.multipart.FilePart

fun interface PlanParser {
    suspend fun parse(filePart: FilePart): SatzwerkParserResponse
}
