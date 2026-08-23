package com.satzwerk.sessions

import com.fasterxml.jackson.databind.ObjectMapper
import com.satzwerk.common.ConflictException
import com.satzwerk.common.ForbiddenException
import com.satzwerk.common.NotFoundException
import com.satzwerk.common.body
import com.satzwerk.common.validateOrBadRequest
import com.satzwerk.publicapi.PartnerWritePolicyService
import com.satzwerk.publicapi.PartnerWritePrincipalValidationService
import com.satzwerk.publicapi.PartnerWriteRequestFingerprintCodec
import com.satzwerk.publicapi.PublicScope
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

private data class PublicSessionMutationDependencies(
    val partnerWritePolicyService: PartnerWritePolicyService,
    val partnerWritePrincipalValidationService: PartnerWritePrincipalValidationService,
    val validator: Validator,
    val objectMapper: ObjectMapper,
)

@Configuration
class PublicSessionMutationRouter(
    private val partnerWritePrincipalValidationService: PartnerWritePrincipalValidationService,
) {
    @Bean
    fun publicSessionMutationRoutes(
        workoutSessionService: WorkoutSessionService,
        setLogService: SetLogService,
        partnerWritePolicyService: PartnerWritePolicyService,
        validator: Validator,
        objectMapper: ObjectMapper,
    ) = coRouter {
        val dependencies =
            PublicSessionMutationDependencies(
                partnerWritePolicyService = partnerWritePolicyService,
                partnerWritePrincipalValidationService = partnerWritePrincipalValidationService,
                validator = validator,
                objectMapper = objectMapper,
            )
        "/api/public/sessions".nest {
            publicSessionStartRoutes(
                workoutSessionService,
                partnerWritePolicyService,
                partnerWritePrincipalValidationService,
                validator,
            )
            publicSessionSetLogRoutes(
                workoutSessionService,
                setLogService,
                dependencies,
            )
            publicSessionLifecycleRoutes(
                workoutSessionService,
                partnerWritePolicyService,
                partnerWritePrincipalValidationService,
                validator,
            )
        }
    }
}

private fun CoRouterFunctionDsl.publicSessionStartRoutes(
    workoutSessionService: WorkoutSessionService,
    partnerWritePolicyService: PartnerWritePolicyService,
    partnerWritePrincipalValidationService: PartnerWritePrincipalValidationService,
    validator: Validator,
) {
    POST("") { request ->
        handlePublicScope(request, PublicScope.SESSIONS_WRITE, extra = publicSessionWriteErrors) { ctx ->
            val partnerPrincipal = partnerWritePrincipalValidationService.requireValidPrincipal(ctx)
            val body = ctx.body<StartSessionRequest>()
            validateOrBadRequest(validator, body) {
                partnerWritePolicyService.execute(
                    partnerPrincipal,
                    request,
                    HttpStatus.CREATED,
                    PartnerWriteRequestFingerprintCodec.body(body),
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
    dependencies: PublicSessionMutationDependencies,
) {
    POST("/{id}/set-logs") { request ->
        handlePublicScope(request, PublicScope.SESSIONS_WRITE, extra = publicSessionWriteErrors) { ctx ->
            val partnerPrincipal = dependencies.partnerWritePrincipalValidationService.requireValidPrincipal(ctx)
            val sessionId = ctx.pathId("id")
            val body = ctx.body<AddSetLogRequest>()
            validateOrBadRequest(dependencies.validator, body) {
                dependencies.partnerWritePolicyService.execute(
                    partnerPrincipal,
                    request,
                    HttpStatus.CREATED,
                    PartnerWriteRequestFingerprintCodec.body(body),
                ) { userId ->
                    val session = workoutSessionService.requireOwnedOpenSession(userId, sessionId)
                    setLogService.add(session, body)
                }
            }
        }
    }

    PATCH("/{id}/set-logs/{setLogId}") { request ->
        handlePublicScope(request, PublicScope.SESSIONS_WRITE, extra = publicSessionWriteErrors) { ctx ->
            val partnerPrincipal = dependencies.partnerWritePrincipalValidationService.requireValidPrincipal(ctx)
            val sessionId = ctx.pathId("id")
            val setLogId = ctx.pathId("setLogId")
            val body = parseUpdateSetLogRequest(ctx.body(), dependencies.objectMapper)
            validateOrBadRequest(dependencies.validator, body) {
                dependencies.partnerWritePolicyService.execute(
                    partnerPrincipal,
                    request,
                    HttpStatus.OK,
                    PartnerWriteRequestFingerprintCodec.body(body),
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
    partnerWritePolicyService: PartnerWritePolicyService,
    partnerWritePrincipalValidationService: PartnerWritePrincipalValidationService,
    validator: Validator,
) {
    POST("/{id}/complete") { request ->
        handlePublicScope(request, PublicScope.SESSIONS_WRITE, extra = publicSessionWriteErrors) { ctx ->
            val partnerPrincipal = partnerWritePrincipalValidationService.requireValidPrincipal(ctx)
            val sessionId = ctx.pathId("id")
            val body = ctx.body<CompleteSessionRequest>()
            validateOrBadRequest(validator, body) {
                partnerWritePolicyService.execute(
                    partnerPrincipal,
                    request,
                    HttpStatus.OK,
                    PartnerWriteRequestFingerprintCodec.body(body),
                ) { userId ->
                    workoutSessionService.complete(userId, sessionId, body)
                }
            }
        }
    }
}
