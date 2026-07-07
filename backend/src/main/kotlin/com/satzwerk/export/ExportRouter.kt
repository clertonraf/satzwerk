package com.satzwerk.export

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class ExportRouter {
    @Bean
    fun exportRoutes(handler: ExportHandler) =
        coRouter {
            "/api".nest {
                GET("/export", handler::export)
                POST("/import", handler::import)
            }
        }
}
