package com.satzwerk.workouts

import com.satzwerk.auth.InsufficientScopeException
import com.satzwerk.auth.TokenScope
import com.satzwerk.common.RequestContext
import com.satzwerk.common.handleErrors
import com.satzwerk.common.requireScope
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.coRouter
import kotlin.reflect.KClass

private val scopeErrors: Map<KClass<out Throwable>, HttpStatus> =
    mapOf(InsufficientScopeException::class to HttpStatus.FORBIDDEN)

/**
 * Public workout read surfaces under `/api/public/exercises` and `/api/public/plans`.
 * Accepted credentials: personal API tokens (#204) and partner app tokens (#205).
 * Exercise endpoints require [TokenScope.EXERCISES_READ]; plan endpoints require [TokenScope.PLANS_READ].
 */
@Configuration
class PublicWorkoutsRouter {
    @Bean
    fun publicExerciseRoutes(exerciseService: ExerciseService) =
        coRouter {
            "/api/public/exercises".nest {
                GET("") { request ->
                    handleErrors(extra = scopeErrors) {
                        requireScope(request, TokenScope.EXERCISES_READ)
                        val ctx = RequestContext(request)
                        ServerResponse.ok().bodyValueAndAwait(
                            exerciseService.list(ctx.userId(), ctx.queryParam("muscleGroup")),
                        )
                    }
                }
                GET("/{id}") { request ->
                    handleErrors(extra = scopeErrors) {
                        requireScope(request, TokenScope.EXERCISES_READ)
                        val ctx = RequestContext(request)
                        ServerResponse.ok().bodyValueAndAwait(
                            exerciseService.getOwned(ctx.userId(), ctx.pathId("id")),
                        )
                    }
                }
            }
        }

    @Bean
    fun publicPlanRoutes(workoutPlanService: WorkoutPlanService) =
        coRouter {
            "/api/public/plans".nest {
                GET("") { request ->
                    handleErrors(extra = scopeErrors) {
                        requireScope(request, TokenScope.PLANS_READ)
                        val ctx = RequestContext(request)
                        ServerResponse.ok().bodyValueAndAwait(workoutPlanService.list(ctx.userId()))
                    }
                }
                GET("/{planId}") { request ->
                    handleErrors(extra = scopeErrors) {
                        requireScope(request, TokenScope.PLANS_READ)
                        val ctx = RequestContext(request)
                        ServerResponse.ok().bodyValueAndAwait(
                            workoutPlanService.getDetail(ctx.userId(), ctx.pathId("planId")),
                        )
                    }
                }
            }
        }
}
