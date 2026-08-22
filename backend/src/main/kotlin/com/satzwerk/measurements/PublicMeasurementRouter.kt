package com.satzwerk.measurements

import com.satzwerk.auth.InsufficientScopeException
import com.satzwerk.auth.TokenScope
import com.satzwerk.common.ConflictException
import com.satzwerk.common.RequestContext
import com.satzwerk.common.body
import com.satzwerk.common.handleErrors
import com.satzwerk.common.requireScope
import com.satzwerk.common.validateOrBadRequest
import com.satzwerk.publicapi.PartnerWritePolicyService
import jakarta.validation.Validator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class PublicMeasurementRouter {
    @Bean
    fun publicMeasurementRoutes(
        measurementService: MeasurementService,
        partnerWritePolicyService: PartnerWritePolicyService,
        validator: Validator,
    ) = coRouter {
        "/api/public/measurements".nest {
            /**
             * Partner-accessible upsert endpoint for BodyMeasurement.
             * Delegates to the same [MeasurementService.upsert] used by the first-party UI,
             * preserving the upsert-by-date partial-merge semantics exactly.
             * Requires scope [TokenScope.MEASUREMENTS_WRITE].
             */
            POST("") { request ->
                handleErrors(
                    extra =
                        mapOf(
                            InsufficientScopeException::class to HttpStatus.FORBIDDEN,
                            ConflictException::class to HttpStatus.CONFLICT,
                        ),
                ) {
                    requireScope(request, TokenScope.MEASUREMENTS_WRITE)
                    val ctx = RequestContext(request)
                    val body = ctx.body<UpsertMeasurementRequest>()
                    validateOrBadRequest(validator, body) {
                        partnerWritePolicyService.execute(request, HttpStatus.OK) { userId ->
                            measurementService.upsert(userId, body)
                        }
                    }
                }
            }
        }
    }
}
