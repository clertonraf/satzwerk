package com.satzwerk.measurements

import com.satzwerk.common.ConflictException
import com.satzwerk.common.body
import com.satzwerk.common.validateOrBadRequest
import com.satzwerk.publicapi.PublicScope
import com.satzwerk.publicapi.PublicWritePolicyService
import com.satzwerk.publicapi.PublicWritePrincipalValidationService
import com.satzwerk.publicapi.PublicWriteRequestFingerprintCodec
import com.satzwerk.publicapi.handlePublicScope
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
        publicWritePolicyService: PublicWritePolicyService,
        publicWritePrincipalValidationService: PublicWritePrincipalValidationService,
        validator: Validator,
    ) = coRouter {
        "/api/public/measurements".nest {
            /**
             * Partner-accessible upsert endpoint for BodyMeasurement.
             * Delegates to the same [MeasurementService.upsert] used by the first-party UI,
             * preserving the upsert-by-date partial-merge semantics exactly.
             * Requires scope [PublicScope.MEASUREMENTS_WRITE].
             */
            POST("") { request ->
                handlePublicScope(
                    request = request,
                    requiredScope = PublicScope.MEASUREMENTS_WRITE,
                    extra =
                        mapOf(
                            ConflictException::class to HttpStatus.CONFLICT,
                        ),
                ) { ctx ->
                    val publicWritePrincipal = publicWritePrincipalValidationService.requireValidPrincipal(ctx)
                    val body = ctx.body<UpsertMeasurementRequest>()
                    validateOrBadRequest(validator, body) {
                        publicWritePolicyService.execute(
                            publicWritePrincipal,
                            request,
                            HttpStatus.OK,
                            PublicWriteRequestFingerprintCodec.body(body),
                        ) { userId ->
                            measurementService.upsert(userId, body)
                        }
                    }
                }
            }
        }
    }
}
