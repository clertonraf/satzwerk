package com.satzwerk.sessions

import com.satzwerk.common.ConflictException
import com.satzwerk.common.ForbiddenException
import com.satzwerk.common.NotFoundException
import com.satzwerk.common.body
import com.satzwerk.common.validateOrBadRequest
import com.satzwerk.publicapi.PublicScope
import com.satzwerk.publicapi.PublicWritePolicyService
import com.satzwerk.publicapi.PublicWritePrincipalValidationService
import com.satzwerk.publicapi.PublicWriteRequestFingerprintCodec
import com.satzwerk.publicapi.handlePublicScope
import jakarta.validation.Validator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.CoRouterFunctionDsl
import org.springframework.web.reactive.function.server.coRouter
import kotlin.reflect.KClass

private val publicSessionWriteErrors: Map<KClass<out Throwable>, HttpStatus> =
    mapOf(
        ConflictException::class to HttpStatus.CONFLICT,
    )

@Configuration
class PublicSessionMutationRouter(
    private val publicWritePrincipalValidationService: PublicWritePrincipalValidationService,
) {
    @Bean
    fun publicSessionMutationRoutes(
        workoutSessionService: WorkoutSessionService,
        setLogService: SetLogService,
        publicWritePolicyService: PublicWritePolicyService,
        validator: Validator,
    ) = coRouter {
        "/api/public/sessions".nest {
            publicSessionStartRoutes(
                workoutSessionService,
                publicWritePolicyService,
                publicWritePrincipalValidationService,
                validator,
            )
            publicSessionSetLogRoutes(
                workoutSessionService,
                setLogService,
                publicWritePolicyService,
                publicWritePrincipalValidationService,
                validator,
            )
            publicSessionLifecycleRoutes(
                workoutSessionService,
                publicWritePolicyService,
                publicWritePrincipalValidationService,
                validator,
            )
        }
    }
}

private fun CoRouterFunctionDsl.publicSessionStartRoutes(
    workoutSessionService: WorkoutSessionService,
    publicWritePolicyService: PublicWritePolicyService,
    publicWritePrincipalValidationService: PublicWritePrincipalValidationService,
    validator: Validator,
) {
    POST("") { request ->
        handlePublicScope(request, PublicScope.SESSIONS_WRITE, extra = publicSessionWriteErrors) { ctx ->
            val publicWritePrincipal = publicWritePrincipalValidationService.requireValidPrincipal(ctx)
            val body = ctx.body<StartSessionRequest>()
            validateOrBadRequest(validator, body) {
                publicWritePolicyService.execute(
                    publicWritePrincipal,
                    request,
                    HttpStatus.CREATED,
                    PublicWriteRequestFingerprintCodec.body(body),
                ) { userId ->
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
    publicWritePolicyService: PublicWritePolicyService,
    publicWritePrincipalValidationService: PublicWritePrincipalValidationService,
    validator: Validator,
) {
    POST("/{id}/set-logs") { request ->
        handlePublicScope(request, PublicScope.SESSIONS_WRITE, extra = publicSessionWriteErrors) { ctx ->
            val publicWritePrincipal = publicWritePrincipalValidationService.requireValidPrincipal(ctx)
            val sessionId = ctx.pathId("id")
            val body = ctx.body<AddSetLogRequest>()
            validateOrBadRequest(validator, body) {
                publicWritePolicyService.execute(
                    publicWritePrincipal,
                    request,
                    HttpStatus.CREATED,
                    PublicWriteRequestFingerprintCodec.body(body),
                ) { userId ->
                    val session = workoutSessionService.requireOwnedOpenSession(userId, sessionId)
                    setLogService.add(session, body)
                }
            }
        }
    }

    PATCH("/{id}/set-logs/{setLogId}") { request ->
        handlePublicScope(request, PublicScope.SESSIONS_WRITE, extra = publicSessionWriteErrors) { ctx ->
            val publicWritePrincipal = publicWritePrincipalValidationService.requireValidPrincipal(ctx)
            val sessionId = ctx.pathId("id")
            val setLogId = ctx.pathId("setLogId")
            val body = ctx.body<UpdateSetLogRequest>()
            validateOrBadRequest(validator, body) {
                publicWritePolicyService.execute(
                    publicWritePrincipal,
                    request,
                    HttpStatus.OK,
                    PublicWriteRequestFingerprintCodec.body(body),
                ) { userId ->
                    val session = workoutSessionService.requireOwnedOpenSession(userId, sessionId)
                    setLogService.update(session, setLogId, body)
                }
            }
        }
    }
}

private fun CoRouterFunctionDsl.publicSessionLifecycleRoutes(
    workoutSessionService: WorkoutSessionService,
    publicWritePolicyService: PublicWritePolicyService,
    publicWritePrincipalValidationService: PublicWritePrincipalValidationService,
    validator: Validator,
) {
    POST("/{id}/complete") { request ->
        handlePublicScope(request, PublicScope.SESSIONS_WRITE, extra = publicSessionWriteErrors) { ctx ->
            val publicWritePrincipal = publicWritePrincipalValidationService.requireValidPrincipal(ctx)
            val sessionId = ctx.pathId("id")
            val body = ctx.body<CompleteSessionRequest>()
            validateOrBadRequest(validator, body) {
                publicWritePolicyService.execute(
                    publicWritePrincipal,
                    request,
                    HttpStatus.OK,
                    PublicWriteRequestFingerprintCodec.body(body),
                ) { userId ->
                    workoutSessionService.complete(userId, sessionId, body)
                }
            }
        }
    }
}
