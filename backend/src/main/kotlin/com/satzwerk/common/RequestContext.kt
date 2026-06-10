package com.satzwerk.common

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.core.codec.CodecException
import org.springframework.web.reactive.function.server.ServerRequest
import java.util.UUID

class RequestContext(
    private val request: ServerRequest,
) {
    suspend fun userId(): UUID {
        val principal = request.principal().awaitSingle()
        return parseUuid(principal.name)
    }

    suspend fun <T : Any> body(clazz: Class<T>): T =
        try {
            request.bodyToMono(clazz).awaitSingleOrNull()
                ?: throw BadRequestException("Request body is required")
        } catch (e: CodecException) {
            throw BadRequestException("Invalid request body: ${e.message ?: "malformed input"}", e)
        }

    fun pathId(name: String): UUID = parseUuid(request.pathVariable(name))

    fun queryParam(name: String): String? = request.queryParam(name).orElse(null)
}

suspend inline fun <reified T : Any> RequestContext.body(): T = body(T::class.java)
