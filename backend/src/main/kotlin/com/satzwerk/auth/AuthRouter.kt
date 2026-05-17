package com.satzwerk.auth

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class AuthRouter {
    @Bean
    fun authRoutes(handler: AuthHandler) =
        coRouter {
            "/api/auth".nest {
                POST("/register", handler::register)
                POST("/login", handler::login)
                POST("/refresh", handler::refresh)
            }
        }
}
