package com.satzwerk.common

import com.satzwerk.config.AUTHORITY_JWT_SESSION
import jakarta.validation.Validator
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.web.reactive.function.server.ServerRequest
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
): ServerResponse {
    return try {
        block()
    } catch (e: ForbiddenException) {
        ServerResponse.status(HttpStatus.FORBIDDEN).bodyValueAndAwait(ErrorResponse(e.message ?: "Forbidden"))
    } catch (_: NotFoundException) {
        ServerResponse.status(HttpStatus.NOT_FOUND).bodyValueAndAwait(ErrorResponse("Not found"))
    } catch (e: BadRequestException) {
        ServerResponse.badRequest().bodyValueAndAwait(ErrorResponse(e.message ?: "Bad request"))
    } catch (_: UnauthorizedException) {
        ServerResponse.status(HttpStatus.UNAUTHORIZED).bodyValueAndAwait(ErrorResponse("Unauthorized"))
    } catch (e: Throwable) {
        val status =
            extra.entries.firstOrNull { (klass, _) -> klass.isInstance(e) }?.value
                ?: throw e
        ServerResponse.status(status).bodyValueAndAwait(ErrorResponse(e.message ?: status.reasonPhrase))
    }
}

/**
 * [handleErrors] overload that builds a [RequestContext] from [request] and passes it to [block].
 * Eliminates the `val ctx = RequestContext(request)` boilerplate from handler methods.
 */
suspend fun handleErrors(
    request: ServerRequest,
    extra: Map<KClass<out Throwable>, HttpStatus> = emptyMap(),
    block: suspend (RequestContext) -> ServerResponse,
): ServerResponse = handleErrors(extra) { block(RequestContext(request)) }

/**
 * Enforces that the current request was authenticated via a first-party JWT session.
 * Personal API tokens (and any future partner-app tokens) do not carry [AUTHORITY_JWT_SESSION],
 * so they are rejected here with 401.
 *
 * Call this at the top of any management handler that must not be accessible by automation tokens.
 * #205 should reuse this guard unchanged.
 */
suspend fun requireJwtSession(request: ServerRequest) {
    val principal = request.principal().awaitSingle()
    val authorities =
        (principal as? UsernamePasswordAuthenticationToken)?.authorities ?: emptyList()
    if (SimpleGrantedAuthority(AUTHORITY_JWT_SESSION) !in authorities) {
        throw UnauthorizedException()
    }
}

/** Thrown when a valid credential is present but is not a first-party JWT session. */
class UnauthorizedException : RuntimeException("JWT session required")
