package com.satzwerk.workouts

import com.satzwerk.common.ConflictException
import com.satzwerk.common.body
import com.satzwerk.common.validateOrBadRequest
import com.satzwerk.publicapi.PartnerWritePolicyService
import com.satzwerk.publicapi.PublicScope
import com.satzwerk.publicapi.handlePublicScope
import jakarta.validation.Validator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.coRouter
import kotlin.reflect.KClass

private val publicExerciseWriteErrors: Map<KClass<out Throwable>, HttpStatus> =
    mapOf(
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
                handlePublicScope(request, PublicScope.EXERCISES_WRITE, extra = publicExerciseWriteErrors) { ctx ->
                    ctx.requirePartnerAppPrincipal()
                    val body = ctx.body<CreateExerciseRequest>()
                    validateOrBadRequest(validator, body) {
                        partnerWritePolicyService.execute(request, HttpStatus.CREATED, body) { userId ->
                            exerciseService.create(userId, body)
                        }
                    }
                }
            }

            PUT("/{id}") { request ->
                handlePublicScope(request, PublicScope.EXERCISES_WRITE, extra = publicExerciseWriteErrors) { ctx ->
                    ctx.requirePartnerAppPrincipal()
                    val exerciseId = ctx.pathId("id")
                    val body = ctx.body<UpdateExerciseRequest>()
                    validateOrBadRequest(validator, body) {
                        partnerWritePolicyService.execute(request, HttpStatus.OK, body) { userId ->
                            exerciseService.update(userId, exerciseId, body)
                        }
                    }
                }
            }
        }
    }
}
