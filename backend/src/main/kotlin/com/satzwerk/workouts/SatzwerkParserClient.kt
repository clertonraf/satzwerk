package com.satzwerk.workouts

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

data class SatzwerkParserResponse(
    @JsonProperty("rest_interval")
    val restInterval: List<Int>? = null,
    val workouts: List<ParsedWorkout>,
)

data class ParsedWorkout(
    val name: String,
    @JsonProperty("body_parts")
    val bodyParts: List<String> = emptyList(),
    val exercises: List<ParsedExercise>,
)

data class ParsedExercise(
    val exercise: String,
    @JsonProperty("advanced_technique")
    val advancedTechnique: String? = null,
    val sets: Int,
    val reps: JsonNode,
)

@Component
class SatzwerkParserClient(
    @Value("\${satzwerk-parser.url:http://satzwerk-parser:8080}")
    private val baseUrl: String,
) : PlanParser {
    private val webClient: WebClient = WebClient.builder().baseUrl(baseUrl).build()

    override suspend fun parse(filePart: FilePart): SatzwerkParserResponse {
        val builder = MultipartBodyBuilder()
        builder.asyncPart("file", filePart.content(), DataBuffer::class.java)
            .header("Content-Disposition", "form-data; name=\"file\"; filename=\"${filePart.filename()}\"")
            .header("Content-Type", MediaType.APPLICATION_OCTET_STREAM_VALUE)

        return webClient.post()
            .uri("/api/workout/parse")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(builder.build()))
            .retrieve()
            .awaitBody<SatzwerkParserResponse>()
    }
}
