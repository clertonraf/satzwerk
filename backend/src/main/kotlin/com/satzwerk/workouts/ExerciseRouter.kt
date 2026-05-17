package com.satzwerk.workouts

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class ExerciseRouter {
    @Bean
    fun exerciseRoutes(handler: ExerciseHandler) =
        coRouter {
            "/api/exercises".nest {
                GET("", handler::list)
                POST("", handler::create)
                GET("/{id}", handler::getById)
                PATCH("/{id}", handler::update)
                DELETE("/{id}", handler::delete)
            }
        }
}
