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
import org.springframework.web.reactive.function.server.CoRouterFunctionDsl
import org.springframework.web.reactive.function.server.coRouter
import kotlin.reflect.KClass

private val publicWorkoutPlanWriteErrors: Map<KClass<out Throwable>, HttpStatus> =
    mapOf(
        InsufficientScopeException::class to HttpStatus.FORBIDDEN,
        ConflictException::class to HttpStatus.CONFLICT,
    )

@Configuration
class PublicWorkoutPlanRouter {
    @Bean
    fun publicWorkoutPlanRoutes(
        workoutPlanService: WorkoutPlanService,
        workoutGroupService: WorkoutGroupService,
        workoutExerciseService: WorkoutExerciseService,
        partnerWritePolicyService: PartnerWritePolicyService,
        validator: Validator,
    ) = coRouter {
        "/api/public/plans".nest {
            publicPlanCrudRoutes(workoutPlanService, partnerWritePolicyService, validator)
            publicGroupRoutes(workoutGroupService, partnerWritePolicyService, validator)
            publicWorkoutExerciseRoutes(workoutExerciseService, partnerWritePolicyService, validator)
        }
    }
}

private fun CoRouterFunctionDsl.publicPlanCrudRoutes(
    workoutPlanService: WorkoutPlanService,
    partnerWritePolicyService: PartnerWritePolicyService,
    validator: Validator,
) {
    POST("") { request ->
        handleErrors(extra = publicWorkoutPlanWriteErrors) {
            requirePartnerPrincipal(request)
            requireScope(request, TokenScope.PLANS_WRITE)
            val ctx = RequestContext(request)
            val body = ctx.body<CreatePlanRequest>()
            validateOrBadRequest(validator, body) {
                partnerWritePolicyService.execute(request, HttpStatus.CREATED) { userId ->
                    workoutPlanService.create(userId, body)
                }
            }
        }
    }

    PUT("/{planId}") { request ->
        handleErrors(extra = publicWorkoutPlanWriteErrors) {
            requirePartnerPrincipal(request)
            requireScope(request, TokenScope.PLANS_WRITE)
            val ctx = RequestContext(request)
            val planId = ctx.pathId("planId")
            val body = ctx.body<UpdatePlanRequest>()
            validateOrBadRequest(validator, body) {
                partnerWritePolicyService.execute(request, HttpStatus.OK) { userId ->
                    workoutPlanService.update(userId, planId, body)
                }
            }
        }
    }

    POST("/{planId}/activate") { request ->
        handleErrors(extra = publicWorkoutPlanWriteErrors) {
            requirePartnerPrincipal(request)
            requireScope(request, TokenScope.PLANS_WRITE)
            val ctx = RequestContext(request)
            val planId = ctx.pathId("planId")
            partnerWritePolicyService.execute(request, HttpStatus.OK) { userId ->
                workoutPlanService.activate(userId, planId)
                workoutPlanService.getDetail(userId, planId)
            }
        }
    }
}

private fun CoRouterFunctionDsl.publicGroupRoutes(
    workoutGroupService: WorkoutGroupService,
    partnerWritePolicyService: PartnerWritePolicyService,
    validator: Validator,
) {
    POST("/{planId}/groups") { request ->
        handleErrors(extra = publicWorkoutPlanWriteErrors) {
            requirePartnerPrincipal(request)
            requireScope(request, TokenScope.PLANS_WRITE)
            val ctx = RequestContext(request)
            val planId = ctx.pathId("planId")
            val body = ctx.body<CreateGroupRequest>()
            validateOrBadRequest(validator, body) {
                partnerWritePolicyService.execute(request, HttpStatus.CREATED) { userId ->
                    workoutGroupService.create(userId, planId, body)
                }
            }
        }
    }

    PUT("/{planId}/groups/{groupId}") { request ->
        handleErrors(extra = publicWorkoutPlanWriteErrors) {
            requirePartnerPrincipal(request)
            requireScope(request, TokenScope.PLANS_WRITE)
            val ctx = RequestContext(request)
            val planId = ctx.pathId("planId")
            val groupId = ctx.pathId("groupId")
            val body = ctx.body<UpdateGroupRequest>()
            validateOrBadRequest(validator, body) {
                partnerWritePolicyService.execute(request, HttpStatus.OK) { userId ->
                    workoutGroupService.update(userId, planId, groupId, body)
                }
            }
        }
    }
}

private fun CoRouterFunctionDsl.publicWorkoutExerciseRoutes(
    workoutExerciseService: WorkoutExerciseService,
    partnerWritePolicyService: PartnerWritePolicyService,
    validator: Validator,
) {
    POST("/{planId}/groups/{groupId}/exercises") { request ->
        handleErrors(extra = publicWorkoutPlanWriteErrors) {
            requirePartnerPrincipal(request)
            requireScope(request, TokenScope.PLANS_WRITE)
            val ctx = RequestContext(request)
            val planId = ctx.pathId("planId")
            val groupId = ctx.pathId("groupId")
            val body = ctx.body<CreateWorkoutExerciseRequest>()
            validateOrBadRequest(validator, body) {
                partnerWritePolicyService.execute(request, HttpStatus.CREATED) { userId ->
                    workoutExerciseService.create(userId, planId, groupId, body)
                }
            }
        }
    }

    PUT("/{planId}/groups/{groupId}/exercises/{exerciseId}") { request ->
        handleErrors(extra = publicWorkoutPlanWriteErrors) {
            requirePartnerPrincipal(request)
            requireScope(request, TokenScope.PLANS_WRITE)
            val ctx = RequestContext(request)
            val planId = ctx.pathId("planId")
            val groupId = ctx.pathId("groupId")
            val exerciseId = ctx.pathId("exerciseId")
            val body = ctx.body<UpdateWorkoutExerciseRequest>()
            validateOrBadRequest(validator, body) {
                partnerWritePolicyService.execute(request, HttpStatus.OK) { userId ->
                    workoutExerciseService.update(userId, planId, groupId, exerciseId, body)
                }
            }
        }
    }
}
