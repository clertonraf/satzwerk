package com.satzwerk.sessions

import com.satzwerk.common.ConflictException
import com.satzwerk.common.ErrorResponse
import com.satzwerk.common.ForbiddenException
import com.satzwerk.common.NotFoundException
import com.satzwerk.common.ValidationErrorResponse
import jakarta.validation.Validator
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import java.util.UUID

internal suspend fun currentUserId(request: ServerRequest): UUID {
    val principal = request.principal().awaitSingle()
    return UUID.fromString(principal.name)
}

internal fun <T : Any> validate(
    validator: Validator,
    body: T,
): ValidationErrorResponse? {
    val violations = validator.validate(body)
    if (violations.isEmpty()) {
        return null
    }

    return ValidationErrorResponse(
        violations.associate { it.propertyPath.toString() to it.message },
    )
}

internal suspend fun handleSessionErrors(block: suspend () -> ServerResponse): ServerResponse =
    try {
        block()
    } catch (_: ForbiddenException) {
        ServerResponse.status(HttpStatus.FORBIDDEN).bodyValueAndAwait(ErrorResponse("Forbidden"))
    } catch (_: NotFoundException) {
        ServerResponse.status(HttpStatus.NOT_FOUND).bodyValueAndAwait(ErrorResponse("Not found"))
    } catch (_: ConflictException) {
        ServerResponse.status(HttpStatus.CONFLICT).bodyValueAndAwait(ErrorResponse("Conflict"))
    }
