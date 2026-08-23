package com.satzwerk.publicapi

import com.satzwerk.auth.InsufficientScopeException
import com.satzwerk.common.BadRequestException
import com.satzwerk.common.RequestContext
import com.satzwerk.common.handleErrors
import com.satzwerk.common.requireScope
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import kotlin.reflect.KClass

object PublicScope {
    const val EXERCISES_READ = "exercises:read"
    const val EXERCISES_WRITE = "exercises:write"
    const val PLANS_READ = "plans:read"
    const val PLANS_WRITE = "plans:write"
    const val SESSIONS_READ = "sessions:read"
    const val SESSIONS_WRITE = "sessions:write"
    const val ANALYTICS_READ = "analytics:read"
    const val MEASUREMENTS_READ = "measurements:read"
    const val MEASUREMENTS_WRITE = "measurements:write"
    const val MEDICATIONS_READ = "medications:read"
    const val MEDICATIONS_WRITE = "medications:write"

    val all =
        setOf(
            EXERCISES_READ, EXERCISES_WRITE,
            PLANS_READ, PLANS_WRITE,
            SESSIONS_READ, SESSIONS_WRITE,
            ANALYTICS_READ,
            MEASUREMENTS_READ, MEASUREMENTS_WRITE,
            MEDICATIONS_READ, MEDICATIONS_WRITE,
        )
}

fun validatePublicScopes(scopes: List<String>) {
    if (scopes.isEmpty()) throw BadRequestException("At least one scope is required")
    val invalid = scopes.filterNot { it in PublicScope.all }
    if (invalid.isNotEmpty()) throw BadRequestException("Unknown scopes: ${invalid.joinToString()}")
}

fun validateDeclaredPublicScopes(scopes: String): String {
    val normalisedScopes = normalisePublicScopes(scopes)
    val invalid = parsePublicScopes(normalisedScopes).filter { it !in PublicScope.all }
    if (invalid.isNotEmpty()) throw BadRequestException("Unknown scopes: ${invalid.joinToString()}")
    return normalisedScopes
}

fun validateGrantedPublicScopes(
    grantedScopes: String,
    declaredScopes: String,
): String {
    val normalisedGrantedScopes = normalisePublicScopes(grantedScopes)
    val declaredScopeSet = parsePublicScopes(declaredScopes).toSet()
    val invalid = parsePublicScopes(normalisedGrantedScopes).filter { it !in declaredScopeSet }
    if (invalid.isNotEmpty()) {
        throw BadRequestException("Scopes not declared by app: ${invalid.joinToString()}")
    }
    return normalisedGrantedScopes
}

suspend fun handlePublicScope(
    request: ServerRequest,
    requiredScope: String,
    extra: Map<KClass<out Throwable>, HttpStatus> = emptyMap(),
    block: suspend (RequestContext) -> ServerResponse,
): ServerResponse =
    handleErrors(request, publicScopeErrors + extra) { ctx ->
        requirePublicScope(request, requiredScope)
        block(ctx)
    }

suspend fun requirePublicScope(
    request: ServerRequest,
    requiredScope: String,
) {
    check(requiredScope in PublicScope.all) { "Unknown public scope: $requiredScope" }
    requireScope(request, requiredScope)
}

internal fun normalisePublicScopes(scopes: String): String =
    parsePublicScopes(scopes)
        .distinct()
        .sorted()
        .joinToString(" ")

private fun parsePublicScopes(scopes: String): List<String> =
    scopes
        .split(" ", ",")
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }

private val publicScopeErrors: Map<KClass<out Throwable>, HttpStatus> =
    mapOf(InsufficientScopeException::class to HttpStatus.FORBIDDEN)
