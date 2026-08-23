package com.satzwerk.common

import com.satzwerk.auth.InsufficientScopeException
import com.satzwerk.config.AUTHORITY_JWT_SESSION
import com.satzwerk.partners.PartnerPrincipal
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.core.codec.CodecException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.web.reactive.function.server.ServerRequest
import java.security.Principal
import java.util.UUID

enum class RequestPrincipalKind {
    JWT_SESSION,
    PERSONAL_API_TOKEN,
    PARTNER_APP,
}

sealed interface RequestPrincipal {
    val userId: UUID
    val kind: RequestPrincipalKind
    val scopes: Set<String>

    fun hasScope(scope: String): Boolean = scope in scopes

    fun owns(ownerId: UUID): Boolean = userId == ownerId

    fun requireOwner(
        ownerId: UUID,
        resourceName: String = "resource",
    ) {
        if (!owns(ownerId)) {
            throw ForbiddenException("$resourceName does not belong to user")
        }
    }
}

data class JwtSessionRequestPrincipal(
    override val userId: UUID,
) : RequestPrincipal {
    override val kind: RequestPrincipalKind = RequestPrincipalKind.JWT_SESSION
    override val scopes: Set<String> = emptySet()
}

data class PersonalApiTokenRequestPrincipal(
    override val userId: UUID,
    override val scopes: Set<String>,
) : RequestPrincipal {
    override val kind: RequestPrincipalKind = RequestPrincipalKind.PERSONAL_API_TOKEN
}

data class PartnerAppRequestPrincipal(
    override val userId: UUID,
    val appId: UUID,
    val grantId: UUID,
    override val scopes: Set<String>,
    val partnerPrincipal: PartnerPrincipal,
) : RequestPrincipal {
    override val kind: RequestPrincipalKind = RequestPrincipalKind.PARTNER_APP
}

class RequestContext(
    private val request: ServerRequest,
) {
    suspend fun principal(): RequestPrincipal {
        val principal = request.principal().awaitSingle()
        return resolveRequestPrincipal(principal)
    }

    suspend fun userId(): UUID = principal().userId

    suspend fun requireJwtSession() {
        if (principal().kind != RequestPrincipalKind.JWT_SESSION) {
            throw UnauthorizedException()
        }
    }

    suspend fun requireScope(scope: String) {
        if (!principal().hasScope(scope)) {
            throw InsufficientScopeException(scope)
        }
    }

    suspend fun requirePartnerAppPrincipal(): PartnerAppRequestPrincipal =
        principal() as? PartnerAppRequestPrincipal ?: throw UnauthorizedException()

    suspend fun <T : Any> body(clazz: Class<T>): T =
        try {
            request.bodyToMono(clazz).awaitSingleOrNull()
                ?: throw BadRequestException("Request body is required")
        } catch (e: CodecException) {
            throw BadRequestException("Invalid request body: ${e.message ?: "malformed input"}", e)
        }

    fun header(name: String): String? = request.headers().firstHeader(name)

    fun pathId(name: String): UUID = parseUuid(request.pathVariable(name))

    fun queryParam(name: String): String? = request.queryParam(name).orElse(null)
}

suspend inline fun <reified T : Any> RequestContext.body(): T = body(T::class.java)

private fun resolveRequestPrincipal(principal: Principal): RequestPrincipal =
    (principal as? UsernamePasswordAuthenticationToken)?.let(::resolveAuthenticationPrincipal)
        ?: JwtSessionRequestPrincipal(parseUuid(principal.name))

private fun resolveAuthenticationPrincipal(authentication: UsernamePasswordAuthenticationToken): RequestPrincipal {
    val userId = parseUuid(authentication.name)
    val scopes =
        authentication.authorities
            .map { it.authority }
            .filter { it != AUTHORITY_JWT_SESSION }
            .toSet()
    val partnerPrincipal = authentication.credentials as? PartnerPrincipal
    return partnerPrincipal?.let {
        PartnerAppRequestPrincipal(
            userId = userId,
            appId = parseUuid(it.appId),
            grantId = parseUuid(it.grantId),
            scopes = scopes,
            partnerPrincipal = it,
        )
    }
        ?: if (authentication.authorities.any { it.authority == AUTHORITY_JWT_SESSION }) {
            JwtSessionRequestPrincipal(userId)
        } else {
            PersonalApiTokenRequestPrincipal(userId, scopes)
        }
}
