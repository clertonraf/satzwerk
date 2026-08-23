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
import org.springframework.web.reactive.function.server.CoRouterFunctionDsl
import org.springframework.web.reactive.function.server.coRouter
import kotlin.reflect.KClass

private val publicWorkoutPlanWriteErrors: Map<KClass<out Throwable>, HttpStatus> =
    mapOf(
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
        handlePublicScope(request, PublicScope.PLANS_WRITE, extra = publicWorkoutPlanWriteErrors) { ctx ->
            ctx.requirePartnerAppPrincipal()
            val body = ctx.body<CreatePlanRequest>()
            validateOrBadRequest(validator, body) {
                partnerWritePolicyService.execute(request, HttpStatus.CREATED, body) { userId ->
                    workoutPlanService.create(userId, body)
                }
            }
        }
    }

    PUT("/{planId}") { request ->
        handlePublicScope(request, PublicScope.PLANS_WRITE, extra = publicWorkoutPlanWriteErrors) { ctx ->
            ctx.requirePartnerAppPrincipal()
            val planId = ctx.pathId("planId")
            val body = ctx.body<UpdatePlanRequest>()
            validateOrBadRequest(validator, body) {
                partnerWritePolicyService.execute(request, HttpStatus.OK, body) { userId ->
                    workoutPlanService.update(userId, planId, body)
                }
            }
        }
    }

    POST("/{planId}/activate") { request ->
        handlePublicScope(request, PublicScope.PLANS_WRITE, extra = publicWorkoutPlanWriteErrors) { ctx ->
            ctx.requirePartnerAppPrincipal()
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
        handlePublicScope(request, PublicScope.PLANS_WRITE, extra = publicWorkoutPlanWriteErrors) { ctx ->
            ctx.requirePartnerAppPrincipal()
            val planId = ctx.pathId("planId")
            val body = ctx.body<CreateGroupRequest>()
            validateOrBadRequest(validator, body) {
                partnerWritePolicyService.execute(request, HttpStatus.CREATED, body) { userId ->
                    workoutGroupService.create(userId, planId, body)
                }
            }
        }
    }

    PUT("/{planId}/groups/{groupId}") { request ->
        handlePublicScope(request, PublicScope.PLANS_WRITE, extra = publicWorkoutPlanWriteErrors) { ctx ->
            ctx.requirePartnerAppPrincipal()
            val planId = ctx.pathId("planId")
            val groupId = ctx.pathId("groupId")
            val body = ctx.body<UpdateGroupRequest>()
            validateOrBadRequest(validator, body) {
                partnerWritePolicyService.execute(request, HttpStatus.OK, body) { userId ->
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
        handlePublicScope(request, PublicScope.PLANS_WRITE, extra = publicWorkoutPlanWriteErrors) { ctx ->
            ctx.requirePartnerAppPrincipal()
            val planId = ctx.pathId("planId")
            val groupId = ctx.pathId("groupId")
            val body = ctx.body<CreateWorkoutExerciseRequest>()
            validateOrBadRequest(validator, body) {
                partnerWritePolicyService.execute(request, HttpStatus.CREATED, body) { userId ->
                    workoutExerciseService.create(userId, planId, groupId, body)
                }
            }
        }
    }

    PUT("/{planId}/groups/{groupId}/exercises/{exerciseId}") { request ->
        handlePublicScope(request, PublicScope.PLANS_WRITE, extra = publicWorkoutPlanWriteErrors) { ctx ->
            ctx.requirePartnerAppPrincipal()
            val planId = ctx.pathId("planId")
            val groupId = ctx.pathId("groupId")
            val exerciseId = ctx.pathId("exerciseId")
            val body = ctx.body<UpdateWorkoutExerciseRequest>()
            validateOrBadRequest(validator, body) {
                partnerWritePolicyService.execute(request, HttpStatus.OK, body) { userId ->
                    workoutExerciseService.update(userId, planId, groupId, exerciseId, body)
                }
            }
        }
    }
}
