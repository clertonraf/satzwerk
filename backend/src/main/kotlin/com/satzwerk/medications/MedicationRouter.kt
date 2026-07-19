package com.satzwerk.medications

import jakarta.validation.Validator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class MedicationRouter {
    @Bean
    fun medicationRoutes(
        medicationService: MedicationService,
        medicationAnalyticsService: MedicationAnalyticsService,
        validator: Validator,
    ) = coRouter {
        val handler = MedicationHandler(medicationService, medicationAnalyticsService, validator)
        "/api/medications".nest {
            GET("", handler::getAll)
            POST("", handler::create)
            GET("/today", handler::getToday)
            GET("/analytics/heatmap", handler::getAggregateHeatmap)
            GET("/{id}", handler::getOne)
            PUT("/{id}", handler::update)
            DELETE("/{id}", handler::deactivate)
            POST("/{id}/logs", handler::logDose)
            GET("/{id}/logs", handler::getLogs)
            GET("/{id}/analytics", handler::getPerMedicationAnalytics)
        }
    }
}
