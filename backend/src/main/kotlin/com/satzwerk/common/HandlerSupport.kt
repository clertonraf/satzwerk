package com.satzwerk.common

import jakarta.validation.Validator
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import java.util.UUID

@Deprecated("Use RequestContext.userId() instead")
suspend fun currentUserId(request: ServerRequest): UUID {
    val principal = request.principal().awaitSingle()
    return UUID.fromString(principal.name)
}

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
    } catch (_: ForbiddenException) {
        ServerResponse.status(HttpStatus.FORBIDDEN).bodyValueAndAwait(ErrorResponse("Forbidden"))
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
