package com.satzwerk.workouts

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class WorkoutPlanRouter {
    @Bean
    fun workoutPlanRoutes(
        handler: WorkoutPlanHandler,
        groupHandler: WorkoutGroupHandler,
        exerciseHandler: WorkoutExerciseHandler,
    ) = coRouter {
        "/api/plans".nest {
            GET("", handler::list)
            POST("", handler::create)
            POST("/import", handler::import)
            GET("/{planId}", handler::getDetail)
            PATCH("/{planId}", handler::update)
            DELETE("/{planId}", handler::delete)
            POST("/{planId}/activate", handler::activate)
            POST("/{planId}/groups", groupHandler::create)
            PATCH("/{planId}/groups/{groupId}", groupHandler::update)
            DELETE("/{planId}/groups/{groupId}", groupHandler::delete)
            POST("/{planId}/groups/{groupId}/exercises", exerciseHandler::create)
            PATCH("/{planId}/groups/{groupId}/exercises/{exerciseId}", exerciseHandler::update)
            PATCH("/{planId}/groups/{groupId}/exercises/{exerciseId}/order", exerciseHandler::reorder)
            DELETE("/{planId}/groups/{groupId}/exercises/{exerciseId}", exerciseHandler::delete)
        }
    }
}
