package com.satzwerk.sessions

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class SessionRouter {
    @Bean
    fun sessionRoutes(handler: SessionHandler) =
        coRouter {
            "/api/sessions".nest {
                POST("", handler::start)
                GET("/open", handler::getOpen)
                POST("/{id}/set-logs", handler::addSetLog)
                POST("/{id}/complete", handler::complete)
                DELETE("/{id}", handler::discard)
                GET("/history", handler::history)
                GET("/{id}", handler::getById)
            }
        }
}
