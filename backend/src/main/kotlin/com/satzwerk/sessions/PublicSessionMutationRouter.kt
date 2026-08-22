package com.satzwerk.sessions

import com.satzwerk.auth.InsufficientScopeException
import com.satzwerk.auth.TokenScope
import com.satzwerk.common.ConflictException
import com.satzwerk.common.ForbiddenException
import com.satzwerk.common.NotFoundException
import com.satzwerk.common.RequestContext
import com.satzwerk.common.body
import com.satzwerk.common.handleErrors
import com.satzwerk.common.requireScope
import com.satzwerk.common.validateOrBadRequest
import com.satzwerk.publicapi.PartnerWritePolicyService
import jakarta.validation.Validator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.CoRouterFunctionDsl
import org.springframework.web.reactive.function.server.coRouter
import kotlin.reflect.KClass

@Configuration
class PublicSessionMutationRouter {
    @Bean
    fun publicSessionMutationRoutes(
        workoutSessionService: WorkoutSessionService,
        setLogService: SetLogService,
        partnerWritePolicyService: PartnerWritePolicyService,
        validator: Validator,
    ) = coRouter {
        "/api/public/sessions".nest {
            publicSessionStartRoutes(workoutSessionService, partnerWritePolicyService, validator)
            publicSessionSetLogRoutes(workoutSessionService, setLogService, partnerWritePolicyService, validator)
            publicSessionLifecycleRoutes(workoutSessionService, partnerWritePolicyService)
        }
    }
}

private val publicSessionScopeErrors: Map<KClass<out Throwable>, HttpStatus> =
    mapOf(
        InsufficientScopeException::class to HttpStatus.FORBIDDEN,
        ConflictException::class to HttpStatus.CONFLICT,
    )

private fun CoRouterFunctionDsl.publicSessionStartRoutes(
    workoutSessionService: WorkoutSessionService,
    partnerWritePolicyService: PartnerWritePolicyService,
    validator: Validator,
) {
    POST("") { request ->
        handleErrors(extra = publicSessionScopeErrors) {
            requireScope(request, TokenScope.SESSIONS_WRITE)
            val ctx = RequestContext(request)
            val body = ctx.body<StartSessionRequest>()
            validateOrBadRequest(validator, body) {
                partnerWritePolicyService.execute(request, HttpStatus.CREATED) { userId ->
                    try {
                        workoutSessionService.start(userId, body.workoutGroupId)
                    } catch (_: ForbiddenException) {
                        // Public partner writes must not reveal that another user's WorkoutGroup exists.
                        throw NotFoundException("Workout group not found")
                    }
                }
            }
        }
    }
}

private fun CoRouterFunctionDsl.publicSessionSetLogRoutes(
    workoutSessionService: WorkoutSessionService,
    setLogService: SetLogService,
    partnerWritePolicyService: PartnerWritePolicyService,
    validator: Validator,
) {
    POST("/{id}/set-logs") { request ->
        handleErrors(extra = publicSessionScopeErrors) {
            requireScope(request, TokenScope.SESSIONS_WRITE)
            val ctx = RequestContext(request)
            val sessionId = ctx.pathId("id")
            val body = ctx.body<AddSetLogRequest>()
            validateOrBadRequest(validator, body) {
                partnerWritePolicyService.execute(request, HttpStatus.CREATED) { userId ->
                    val session = workoutSessionService.requireOwnedOpenSession(userId, sessionId)
                    setLogService.add(session, body)
                }
            }
        }
    }

    PATCH("/{id}/set-logs/{setLogId}") { request ->
        handleErrors(extra = publicSessionScopeErrors) {
            requireScope(request, TokenScope.SESSIONS_WRITE)
            val ctx = RequestContext(request)
            val sessionId = ctx.pathId("id")
            val setLogId = ctx.pathId("setLogId")
            val body = ctx.body<UpdateSetLogRequest>()
            validateOrBadRequest(validator, body) {
                partnerWritePolicyService.execute(request, HttpStatus.OK) { userId ->
                    val session = workoutSessionService.requireOwnedOpenSession(userId, sessionId)
                    setLogService.update(session, setLogId, body)
                }
            }
        }
    }
}

private fun CoRouterFunctionDsl.publicSessionLifecycleRoutes(
    workoutSessionService: WorkoutSessionService,
    partnerWritePolicyService: PartnerWritePolicyService,
) {
    POST("/{id}/complete") { request ->
        handleErrors(extra = publicSessionScopeErrors) {
            requireScope(request, TokenScope.SESSIONS_WRITE)
            val ctx = RequestContext(request)
            val sessionId = ctx.pathId("id")
            val body = ctx.body<CompleteSessionRequest>()
            partnerWritePolicyService.execute(request, HttpStatus.OK) { userId ->
                workoutSessionService.complete(userId, sessionId, body)
            }
        }
    }
}
