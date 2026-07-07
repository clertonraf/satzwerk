package com.satzwerk.common

import jakarta.validation.Validator
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientException
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

/**
 * Options for [handleErrors] that opt-in to converting additional exception types to HTTP responses.
 * Add a new object here when a new domain exception needs per-handler HTTP mapping.
 */
sealed interface ErrorHandlerOption {
    /** Maps [ConflictException] → 409 Conflict. */
    data object WithConflict : ErrorHandlerOption

    /** Maps [WebClientException] (request or response) → 503 Service Unavailable. */
    data object WithWebClient : ErrorHandlerOption
}

suspend fun handleErrors(
    vararg options: ErrorHandlerOption,
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
        if (ErrorHandlerOption.WithConflict in options) {
            ServerResponse.status(HttpStatus.CONFLICT).bodyValueAndAwait(ErrorResponse("Conflict"))
        } else {
            throw e
        }
    } catch (e: WebClientException) {
        if (ErrorHandlerOption.WithWebClient in options) {
            ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE).bodyValueAndAwait(
                ErrorResponse("Import service unavailable"),
            )
        } else {
            throw e
        }
    }
