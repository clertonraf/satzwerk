package com.satzwerk.partners

import com.satzwerk.common.BadRequestException
import com.satzwerk.common.ConflictException
import com.satzwerk.common.ForbiddenException
import com.satzwerk.common.body
import com.satzwerk.common.handleErrors
import com.satzwerk.common.requireJwtSession
import com.satzwerk.common.validateOrBadRequest
import com.satzwerk.config.APP_TOKEN_HEADER
import jakarta.validation.Validator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.buildAndAwait
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class PartnerAppRouter {
    /**
     * Routes for partner app registration and discovery.
     * All routes are JWT-session-only.
     */
    @Bean
    fun partnerAppRoutes(
        service: PartnerAppService,
        validator: Validator,
    ) = coRouter {
        "/api/partner-apps".nest {
            POST("") { request ->
                handleErrors(
                    request,
                    extra = mapOf(ConflictException::class to HttpStatus.CONFLICT),
                ) { ctx ->
                    requireJwtSession(request)
                    val body = ctx.body<RegisterPartnerAppRequest>()
                    validateOrBadRequest(validator, body) {
                        val result = service.registerApp(body)
                        ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(result)
                    }
                }
            }
            GET("") { request ->
                handleErrors {
                    requireJwtSession(request)
                    ServerResponse.ok().bodyValueAndAwait(service.listApps())
                }
            }
        }
    }

    /**
     * Routes for user consent grant management and the partner-token-accessible probe.
     *
     * - `GET /me` accepts `X-App-Token` (partner token) — read-only binding probe, no management.
     * - All other routes are JWT-session-only; presenting a partner token returns 401.
     */
    @Bean
    fun partnerGrantRoutes(
        service: PartnerAppService,
        validator: Validator,
    ) = coRouter {
        "/api/partner-grants".nest {
            GET("/me") { request ->
                handleErrors {
                    val rawToken =
                        request.headers().firstHeader(APP_TOKEN_HEADER)?.trim()
                            ?: throw ForbiddenException("X-App-Token header required")
                    val grant =
                        service.resolveActiveGrant(rawToken)
                            ?: throw ForbiddenException("Token invalid or revoked")
                    ServerResponse.ok().bodyValueAndAwait(service.resolveBinding(grant))
                }
            }
            POST("") { request ->
                handleErrors(
                    request,
                    extra = mapOf(ConflictException::class to HttpStatus.CONFLICT),
                ) { ctx ->
                    requireJwtSession(request)
                    val userId = ctx.userId()
                    val body = ctx.body<GrantAppAccessRequest>()
                    validateOrBadRequest(validator, body) {
                        val result = service.grantAccess(userId, body)
                        ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(result)
                    }
                }
            }
            GET("") { request ->
                handleErrors(request) { ctx ->
                    requireJwtSession(request)
                    ServerResponse.ok().bodyValueAndAwait(service.listActiveGrants(ctx.userId()))
                }
            }
            DELETE("/{grantId}") { request ->
                handleErrors(
                    request,
                    extra = mapOf(BadRequestException::class to HttpStatus.BAD_REQUEST),
                ) { ctx ->
                    requireJwtSession(request)
                    service.revokeGrant(ctx.userId(), ctx.pathId("grantId"))
                    ServerResponse.noContent().buildAndAwait()
                }
            }
        }
    }
}
