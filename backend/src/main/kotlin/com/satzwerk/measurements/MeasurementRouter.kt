package com.satzwerk.measurements

import jakarta.validation.Validator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class MeasurementRouter {
    @Bean
    fun measurementRoutes(
        measurementService: MeasurementService,
        validator: Validator,
    ) = coRouter {
        val handler = MeasurementHandler(measurementService, validator)
        "/api/measurements".nest {
            POST("", handler::upsert)
            GET("", handler::findAll)
            DELETE("/{date}", handler::deleteByDate)
        }
    }
}
