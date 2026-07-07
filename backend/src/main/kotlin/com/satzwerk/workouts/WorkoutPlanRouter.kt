package com.satzwerk.workouts

import com.satzwerk.common.ErrorResponse
import com.satzwerk.common.RequestContext
import com.satzwerk.common.body
import com.satzwerk.common.handleErrors
import com.satzwerk.common.validateOrBadRequest
import jakarta.validation.Validator
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.codec.multipart.FilePart
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.server.CoRouterFunctionDsl
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.buildAndAwait
import org.springframework.web.reactive.function.server.coRouter
import kotlin.reflect.KClass

private val webClientErrors: Map<KClass<out Throwable>, HttpStatus> =
    mapOf(
        WebClientRequestException::class to HttpStatus.SERVICE_UNAVAILABLE,
        WebClientResponseException::class to HttpStatus.SERVICE_UNAVAILABLE,
    )

@Configuration
class WorkoutPlanRouter {
    @Bean
    fun workoutPlanRoutes(
        workoutPlanService: WorkoutPlanService,
        workoutGroupService: WorkoutGroupService,
        workoutExerciseService: WorkoutExerciseService,
        planImportService: PlanImportService,
        validator: Validator,
    ) = coRouter {
        "/api/plans".nest {
            planCrudRoutes(workoutPlanService, planImportService, validator)
            groupRoutes(workoutGroupService, validator)
            workoutExerciseRoutes(workoutExerciseService, validator)
        }
    }
}

private fun CoRouterFunctionDsl.planCrudRoutes(
    workoutPlanService: WorkoutPlanService,
    planImportService: PlanImportService,
    validator: Validator,
) {
    GET("") { request ->
        handleErrors(extra = webClientErrors) {
            val ctx = RequestContext(request)
            ServerResponse.ok().bodyValueAndAwait(workoutPlanService.list(ctx.userId()))
        }
    }
    POST("") { request ->
        handleErrors(extra = webClientErrors) {
            val ctx = RequestContext(request)
            val body = ctx.body<CreatePlanRequest>()
            validateOrBadRequest(validator, body) {
                ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(
                    workoutPlanService.create(ctx.userId(), body),
                )
            }
        }
    }
    planImportRoute(planImportService)
    GET("/{planId}") { request ->
        handleErrors(extra = webClientErrors) {
            val ctx = RequestContext(request)
            ServerResponse.ok().bodyValueAndAwait(
                workoutPlanService.getDetail(ctx.userId(), ctx.pathId("planId")),
            )
        }
    }
    PATCH("/{planId}") { request ->
        handleErrors(extra = webClientErrors) {
            val ctx = RequestContext(request)
            val body = ctx.body<UpdatePlanRequest>()
            validateOrBadRequest(validator, body) {
                ServerResponse.ok().bodyValueAndAwait(
                    workoutPlanService.update(ctx.userId(), ctx.pathId("planId"), body),
                )
            }
        }
    }
    DELETE("/{planId}") { request ->
        handleErrors(extra = webClientErrors) {
            val ctx = RequestContext(request)
            workoutPlanService.delete(ctx.userId(), ctx.pathId("planId"))
            ServerResponse.noContent().buildAndAwait()
        }
    }
    POST("/{planId}/activate") { request ->
        handleErrors(extra = webClientErrors) {
            val ctx = RequestContext(request)
            workoutPlanService.activate(ctx.userId(), ctx.pathId("planId"))
            ServerResponse.noContent().buildAndAwait()
        }
    }
}

private fun CoRouterFunctionDsl.planImportRoute(planImportService: PlanImportService) {
    POST("/import") { request ->
        handleErrors(extra = webClientErrors) {
            val ctx = RequestContext(request)
            val filePart =
                request.multipartData().awaitSingle().getFirst("file") as? FilePart
                    ?: return@handleErrors ServerResponse.badRequest()
                        .bodyValueAndAwait(ErrorResponse("Missing 'file' part"))
            ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(
                planImportService.import(ctx.userId(), filePart),
            )
        }
    }
}

private fun CoRouterFunctionDsl.groupRoutes(
    workoutGroupService: WorkoutGroupService,
    validator: Validator,
) {
    POST("/{planId}/groups") { request ->
        handleErrors {
            val ctx = RequestContext(request)
            val body = ctx.body<CreateGroupRequest>()
            validateOrBadRequest(validator, body) {
                ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(
                    workoutGroupService.create(ctx.userId(), ctx.pathId("planId"), body),
                )
            }
        }
    }
    PATCH("/{planId}/groups/{groupId}") { request ->
        handleErrors {
            val ctx = RequestContext(request)
            val body = ctx.body<UpdateGroupRequest>()
            validateOrBadRequest(validator, body) {
                ServerResponse.ok().bodyValueAndAwait(
                    workoutGroupService.update(
                        ctx.userId(),
                        ctx.pathId("planId"),
                        ctx.pathId("groupId"),
                        body,
                    ),
                )
            }
        }
    }
    DELETE("/{planId}/groups/{groupId}") { request ->
        handleErrors {
            val ctx = RequestContext(request)
            workoutGroupService.delete(ctx.userId(), ctx.pathId("planId"), ctx.pathId("groupId"))
            ServerResponse.noContent().buildAndAwait()
        }
    }
}

private fun CoRouterFunctionDsl.workoutExerciseRoutes(
    workoutExerciseService: WorkoutExerciseService,
    validator: Validator,
) {
    POST("/{planId}/groups/{groupId}/exercises") { request ->
        handleErrors {
            val ctx = RequestContext(request)
            val body = ctx.body<CreateWorkoutExerciseRequest>()
            validateOrBadRequest(validator, body) {
                ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(
                    workoutExerciseService.create(
                        ctx.userId(),
                        ctx.pathId("planId"),
                        ctx.pathId("groupId"),
                        body,
                    ),
                )
            }
        }
    }
    workoutExerciseMutationRoutes(workoutExerciseService, validator)
}

private fun CoRouterFunctionDsl.workoutExerciseMutationRoutes(
    workoutExerciseService: WorkoutExerciseService,
    validator: Validator,
) {
    PATCH("/{planId}/groups/{groupId}/exercises/{exerciseId}") { request ->
        handleErrors {
            val ctx = RequestContext(request)
            val body = ctx.body<UpdateWorkoutExerciseRequest>()
            validateOrBadRequest(validator, body) {
                ServerResponse.ok().bodyValueAndAwait(
                    workoutExerciseService.update(
                        ctx.userId(),
                        ctx.pathId("planId"),
                        ctx.pathId("groupId"),
                        ctx.pathId("exerciseId"),
                        body,
                    ),
                )
            }
        }
    }
    PATCH("/{planId}/groups/{groupId}/exercises/{exerciseId}/order") { request ->
        handleErrors {
            val ctx = RequestContext(request)
            val body = ctx.body<ReorderRequest>()
            validateOrBadRequest(validator, body) {
                ServerResponse.ok().bodyValueAndAwait(
                    workoutExerciseService.reorder(
                        ctx.userId(),
                        ctx.pathId("planId"),
                        ctx.pathId("groupId"),
                        ctx.pathId("exerciseId"),
                        body.direction,
                    ),
                )
            }
        }
    }
    DELETE("/{planId}/groups/{groupId}/exercises/{exerciseId}") { request ->
        handleErrors {
            val ctx = RequestContext(request)
            workoutExerciseService.delete(
                ctx.userId(),
                ctx.pathId("planId"),
                ctx.pathId("groupId"),
                ctx.pathId("exerciseId"),
            )
            ServerResponse.noContent().buildAndAwait()
        }
    }
}
