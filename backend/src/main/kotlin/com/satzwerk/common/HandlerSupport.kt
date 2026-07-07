package com.satzwerk.common

import jakarta.validation.Validator
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import java.util.UUID

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

suspend fun handleErrors(
    withConflict: Boolean = false,
    withWebClient: Boolean = false,
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
    } catch (e: ConflictException) {
        if (withConflict) {
            ServerResponse.status(HttpStatus.CONFLICT).bodyValueAndAwait(ErrorResponse("Conflict"))
        } else {
            throw e
        }
    } catch (e: WebClientRequestException) {
        if (withWebClient) {
            val msg = ErrorResponse("Import service unavailable")
            ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE).bodyValueAndAwait(msg)
        } else {
            throw e
        }
    } catch (e: WebClientResponseException) {
        if (withWebClient) {
            val msg = ErrorResponse("Import service unavailable")
            ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE).bodyValueAndAwait(msg)
        } else {
            throw e
        }
    }

/**
 * [handleErrors] overload that builds a [RequestContext] from [request] and passes it to [block].
 * Eliminates the `val ctx = RequestContext(request)` boilerplate from handler methods.
 */
suspend fun handleErrors(
    request: ServerRequest,
    withConflict: Boolean = false,
    withWebClient: Boolean = false,
    block: suspend (RequestContext) -> ServerResponse,
): ServerResponse = handleErrors(withConflict, withWebClient) { block(RequestContext(request)) }
