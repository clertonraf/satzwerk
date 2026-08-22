package com.satzwerk.workouts

import com.satzwerk.auth.InsufficientScopeException
import com.satzwerk.auth.TokenScope
import com.satzwerk.common.ConflictException
import com.satzwerk.common.RequestContext
import com.satzwerk.common.body
import com.satzwerk.common.handleErrors
import com.satzwerk.common.requirePartnerPrincipal
import com.satzwerk.common.requireScope
import com.satzwerk.common.validateOrBadRequest
import com.satzwerk.publicapi.PartnerWritePolicyService
import jakarta.validation.Validator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.coRouter
import kotlin.reflect.KClass

private val publicExerciseWriteErrors: Map<KClass<out Throwable>, HttpStatus> =
    mapOf(
        InsufficientScopeException::class to HttpStatus.FORBIDDEN,
        ConflictException::class to HttpStatus.CONFLICT,
    )

@Configuration
class PublicExerciseRouter {
    @Bean
    fun publicExerciseRoutes(
        exerciseService: ExerciseService,
        partnerWritePolicyService: PartnerWritePolicyService,
        validator: Validator,
    ) = coRouter {
        "/api/public/exercises".nest {
            POST("") { request ->
                handleErrors(extra = publicExerciseWriteErrors) {
                    requirePartnerPrincipal(request)
                    requireScope(request, TokenScope.EXERCISES_WRITE)
                    val ctx = RequestContext(request)
                    val body = ctx.body<CreateExerciseRequest>()
                    validateOrBadRequest(validator, body) {
                        partnerWritePolicyService.execute(request, HttpStatus.CREATED) { userId ->
                            exerciseService.create(userId, body)
                        }
                    }
                }
            }

            PUT("/{id}") { request ->
                handleErrors(extra = publicExerciseWriteErrors) {
                    requirePartnerPrincipal(request)
                    requireScope(request, TokenScope.EXERCISES_WRITE)
                    val ctx = RequestContext(request)
                    val exerciseId = ctx.pathId("id")
                    val body = ctx.body<UpdateExerciseRequest>()
                    validateOrBadRequest(validator, body) {
                        partnerWritePolicyService.execute(request, HttpStatus.OK) { userId ->
                            exerciseService.update(userId, exerciseId, body)
                        }
                    }
                }
            }
        }
    }
}
