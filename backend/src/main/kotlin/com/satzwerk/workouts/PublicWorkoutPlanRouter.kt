package com.satzwerk.workouts

import com.satzwerk.common.ConflictException
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

private val publicWorkoutPlanWriteErrors: Map<KClass<out Throwable>, HttpStatus> =
    mapOf(
        ConflictException::class to HttpStatus.CONFLICT,
    )

@Configuration
class PublicWorkoutPlanRouter(
    private val publicWritePrincipalValidationService: PublicWritePrincipalValidationService,
) {
    @Bean
    fun publicWorkoutPlanRoutes(
        workoutPlanService: WorkoutPlanService,
        workoutGroupService: WorkoutGroupService,
        workoutExerciseService: WorkoutExerciseService,
        publicWritePolicyService: PublicWritePolicyService,
        validator: Validator,
    ) = coRouter {
        "/api/public/plans".nest {
            publicPlanCrudRoutes(
                workoutPlanService,
                publicWritePolicyService,
                publicWritePrincipalValidationService,
                validator,
            )
            publicGroupRoutes(
                workoutGroupService,
                publicWritePolicyService,
                publicWritePrincipalValidationService,
                validator,
            )
            publicWorkoutExerciseRoutes(
                workoutExerciseService,
                publicWritePolicyService,
                publicWritePrincipalValidationService,
                validator,
            )
        }
    }
}

private fun CoRouterFunctionDsl.publicPlanCrudRoutes(
    workoutPlanService: WorkoutPlanService,
    publicWritePolicyService: PublicWritePolicyService,
    publicWritePrincipalValidationService: PublicWritePrincipalValidationService,
    validator: Validator,
) {
    POST("") { request ->
        handlePublicScope(request, PublicScope.PLANS_WRITE, extra = publicWorkoutPlanWriteErrors) { ctx ->
            val publicWritePrincipal = publicWritePrincipalValidationService.requireValidPrincipal(ctx)
            val body = ctx.body<CreatePlanRequest>()
            validateOrBadRequest(validator, body) {
                publicWritePolicyService.execute(
                    publicWritePrincipal,
                    request,
                    HttpStatus.CREATED,
                    PublicWriteRequestFingerprintCodec.body(body),
                ) { userId ->
                    workoutPlanService.create(userId, body)
                }
            }
        }
    }

    PUT("/{planId}") { request ->
        handlePublicScope(request, PublicScope.PLANS_WRITE, extra = publicWorkoutPlanWriteErrors) { ctx ->
            val publicWritePrincipal = publicWritePrincipalValidationService.requireValidPrincipal(ctx)
            val planId = ctx.pathId("planId")
            val body = ctx.body<UpdatePlanRequest>()
            validateOrBadRequest(validator, body) {
                publicWritePolicyService.execute(
                    publicWritePrincipal,
                    request,
                    HttpStatus.OK,
                    PublicWriteRequestFingerprintCodec.body(body),
                ) { userId ->
                    workoutPlanService.update(userId, planId, body)
                }
            }
        }
    }

    POST("/{planId}/activate") { request ->
        handlePublicScope(request, PublicScope.PLANS_WRITE, extra = publicWorkoutPlanWriteErrors) { ctx ->
            val publicWritePrincipal = publicWritePrincipalValidationService.requireValidPrincipal(ctx)
            val planId = ctx.pathId("planId")
            publicWritePolicyService.execute(
                publicWritePrincipal,
                request,
                HttpStatus.OK,
                PublicWriteRequestFingerprintCodec.stateless("activate-workout-plan"),
            ) { userId ->
                workoutPlanService.activate(userId, planId)
                workoutPlanService.getDetail(userId, planId)
            }
        }
    }
}

private fun CoRouterFunctionDsl.publicGroupRoutes(
    workoutGroupService: WorkoutGroupService,
    publicWritePolicyService: PublicWritePolicyService,
    publicWritePrincipalValidationService: PublicWritePrincipalValidationService,
    validator: Validator,
) {
    POST("/{planId}/groups") { request ->
        handlePublicScope(request, PublicScope.PLANS_WRITE, extra = publicWorkoutPlanWriteErrors) { ctx ->
            val publicWritePrincipal = publicWritePrincipalValidationService.requireValidPrincipal(ctx)
            val planId = ctx.pathId("planId")
            val body = ctx.body<CreateGroupRequest>()
            validateOrBadRequest(validator, body) {
                publicWritePolicyService.execute(
                    publicWritePrincipal,
                    request,
                    HttpStatus.CREATED,
                    PublicWriteRequestFingerprintCodec.body(body),
                ) { userId ->
                    workoutGroupService.create(userId, planId, body)
                }
            }
        }
    }

    PUT("/{planId}/groups/{groupId}") { request ->
        handlePublicScope(request, PublicScope.PLANS_WRITE, extra = publicWorkoutPlanWriteErrors) { ctx ->
            val publicWritePrincipal = publicWritePrincipalValidationService.requireValidPrincipal(ctx)
            val planId = ctx.pathId("planId")
            val groupId = ctx.pathId("groupId")
            val body = ctx.body<UpdateGroupRequest>()
            validateOrBadRequest(validator, body) {
                publicWritePolicyService.execute(
                    publicWritePrincipal,
                    request,
                    HttpStatus.OK,
                    PublicWriteRequestFingerprintCodec.body(body),
                ) { userId ->
                    workoutGroupService.update(userId, planId, groupId, body)
                }
            }
        }
    }
}

private fun CoRouterFunctionDsl.publicWorkoutExerciseRoutes(
    workoutExerciseService: WorkoutExerciseService,
    publicWritePolicyService: PublicWritePolicyService,
    publicWritePrincipalValidationService: PublicWritePrincipalValidationService,
    validator: Validator,
) {
    POST("/{planId}/groups/{groupId}/exercises") { request ->
        handlePublicScope(request, PublicScope.PLANS_WRITE, extra = publicWorkoutPlanWriteErrors) { ctx ->
            val publicWritePrincipal = publicWritePrincipalValidationService.requireValidPrincipal(ctx)
            val planId = ctx.pathId("planId")
            val groupId = ctx.pathId("groupId")
            val body = ctx.body<CreateWorkoutExerciseRequest>()
            validateOrBadRequest(validator, body) {
                publicWritePolicyService.execute(
                    publicWritePrincipal,
                    request,
                    HttpStatus.CREATED,
                    PublicWriteRequestFingerprintCodec.body(body),
                ) { userId ->
                    workoutExerciseService.create(userId, planId, groupId, body)
                }
            }
        }
    }

    PUT("/{planId}/groups/{groupId}/exercises/{exerciseId}") { request ->
        handlePublicScope(request, PublicScope.PLANS_WRITE, extra = publicWorkoutPlanWriteErrors) { ctx ->
            val publicWritePrincipal = publicWritePrincipalValidationService.requireValidPrincipal(ctx)
            val planId = ctx.pathId("planId")
            val groupId = ctx.pathId("groupId")
            val exerciseId = ctx.pathId("exerciseId")
            val body = ctx.body<UpdateWorkoutExerciseRequest>()
            validateOrBadRequest(validator, body) {
                publicWritePolicyService.execute(
                    publicWritePrincipal,
                    request,
                    HttpStatus.OK,
                    PublicWriteRequestFingerprintCodec.body(body),
                ) { userId ->
                    workoutExerciseService.update(userId, planId, groupId, exerciseId, body)
                }
            }
        }
    }
}
