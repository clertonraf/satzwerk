package com.satzwerk.workouts

import com.satzwerk.common.RequestContext
import com.satzwerk.common.body
import com.satzwerk.common.handleErrors
import com.satzwerk.common.validateOrBadRequest
import jakarta.validation.Validator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.buildAndAwait
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class ExerciseRouter {
    @Bean
    fun exerciseRoutes(
        exerciseService: ExerciseService,
        validator: Validator,
    ) = coRouter {
        "/api/exercises".nest {
            GET("") { request ->
                handleErrors {
                    val ctx = RequestContext(request)
                    ServerResponse.ok().bodyValueAndAwait(
                        exerciseService.list(ctx.userId(), ctx.queryParam("muscleGroup")),
                    )
                }
            }
            POST("") { request ->
                handleErrors {
                    val ctx = RequestContext(request)
                    val body = ctx.body<CreateExerciseRequest>()
                    validateOrBadRequest(validator, body) {
                        val response = exerciseService.create(ctx.userId(), body)
                        ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(response)
                    }
                }
            }
            GET("/{id}") { request ->
                handleErrors {
                    val ctx = RequestContext(request)
                    ServerResponse.ok().bodyValueAndAwait(
                        exerciseService.getOwned(ctx.userId(), ctx.pathId("id")),
                    )
                }
            }
            PATCH("/{id}") { request ->
                handleErrors {
                    val ctx = RequestContext(request)
                    ServerResponse.ok().bodyValueAndAwait(
                        exerciseService.update(ctx.userId(), ctx.pathId("id"), ctx.body<UpdateExerciseRequest>()),
                    )
                }
            }
            DELETE("/{id}") { request ->
                handleErrors {
                    val ctx = RequestContext(request)
                    exerciseService.delete(ctx.userId(), ctx.pathId("id"))
                    ServerResponse.noContent().buildAndAwait()
                }
            }
        }
    }
}
