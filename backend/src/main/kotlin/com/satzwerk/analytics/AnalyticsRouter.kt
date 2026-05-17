package com.satzwerk.analytics

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class AnalyticsRouter {
    @Bean
    fun analyticsRoutes(handler: AnalyticsHandler) =
        coRouter {
            "/api/analytics".nest {
                GET("/heatmap", handler::heatmap)
                GET("/streak", handler::streak)
            }
        }
}
