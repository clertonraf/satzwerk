package com.satzwerk.common

import jakarta.validation.Validator
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import java.util.UUID
import kotlin.reflect.KClass

fun parseUuid(value: String): UUID =
    try {
        UUID.fromString(value)
    } catch (_: IllegalArgumentException) {
        throw BadRequestException("Invalid UUID: $value")
    }

suspend fun <T : Any> validateOrBadRequest(
    validator: Validator,
    body: T,
    block: suspend () -> ServerResponse,
): ServerResponse {
    val violations = validator.validate(body)
    if (violations.isNotEmpty()) {
        return ServerResponse.badRequest().bodyValueAndAwait(
            ValidationErrorResponse(violations.associate { it.propertyPath.toString() to it.message }),
        )
    }
    return block()
}

/**
 * Wraps a handler block, converting well-known domain exceptions to HTTP responses.
 *
 * [ForbiddenException], [NotFoundException], and [BadRequestException] are always converted.
 * Additional exception-to-status mappings can be supplied via [extra]:
 *
 * ```kotlin
 * handleErrors(extra = mapOf(ConflictException::class to HttpStatus.CONFLICT)) { ... }
 * handleErrors(
 *     extra = mapOf(
 *         WebClientRequestException::class to HttpStatus.SERVICE_UNAVAILABLE,
 *         WebClientResponseException::class to HttpStatus.SERVICE_UNAVAILABLE,
 *     ),
 * ) { ... }
 * ```
 */
suspend fun handleErrors(
    extra: Map<KClass<out Throwable>, HttpStatus> = emptyMap(),
    block: suspend () -> ServerResponse,
): ServerResponse =
    try {
        block()
    } catch (e: ForbiddenException) {
        ServerResponse.status(HttpStatus.FORBIDDEN).bodyValueAndAwait(ErrorResponse(e.message ?: "Forbidden"))
    } catch (_: NotFoundException) {
        ServerResponse.status(HttpStatus.NOT_FOUND).bodyValueAndAwait(ErrorResponse("Not found"))
    } catch (e: BadRequestException) {
        ServerResponse.badRequest().bodyValueAndAwait(ErrorResponse(e.message ?: "Bad request"))
    } catch (e: Throwable) {
        val status =
            extra.entries.firstOrNull { (klass, _) -> klass.isInstance(e) }?.value
                ?: throw e
        ServerResponse.status(status).bodyValueAndAwait(ErrorResponse(e.message ?: status.reasonPhrase))
    }
