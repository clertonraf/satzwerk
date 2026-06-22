package com.satzwerk.sessions

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class SessionRouter {
    @Bean
    fun sessionRoutes(
        handler: SessionHandler,
        startHandler: SessionStartHandler,
    ) = coRouter {
        "/api/sessions".nest {
            POST("", startHandler::start)
            GET("/start-options", startHandler::getStartOptions)
            GET("/open/plan-detail", startHandler::getOpenPlanDetail)
            GET("/open", handler::getOpen)
            POST("/{id}/set-logs", handler::addSetLog)
            PATCH("/{id}/set-logs/{setLogId}", handler::updateSetLog)
            DELETE("/{id}/set-logs/{setLogId}", handler::deleteSetLog)
            POST("/{id}/complete", handler::complete)
            DELETE("/{id}", handler::discard)
            GET("/history", handler::history)
            GET("/{id}/reference-weights", handler::getReferenceWeights)
            GET("/{id}", handler::getById)
        }
    }
}
