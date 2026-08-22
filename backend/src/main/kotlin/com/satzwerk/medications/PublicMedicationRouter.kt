package com.satzwerk.medications

import com.satzwerk.auth.InsufficientScopeException
import com.satzwerk.auth.TokenScope
import com.satzwerk.common.ConflictException
import com.satzwerk.common.RequestContext
import com.satzwerk.common.body
import com.satzwerk.common.handleErrors
import com.satzwerk.common.requireScope
import com.satzwerk.common.validateOrBadRequest
import jakarta.validation.Validator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class PublicMedicationRouter {
    @Bean
    fun publicMedicationRoutes(
        medicationService: MedicationService,
        validator: Validator,
    ) = coRouter {
        "/api/public/medications".nest {
            /**
             * Create a Medication for the consenting user.
             * Reuses [MedicationService.createMedication], which enforces per-user
             * case-insensitive name uniqueness and dosage-unit validation.
             * Requires scope [TokenScope.MEDICATIONS_WRITE].
             */
            POST("") { request ->
                handleErrors(
                    extra =
                        mapOf(
                            InsufficientScopeException::class to HttpStatus.FORBIDDEN,
                            ConflictException::class to HttpStatus.CONFLICT,
                        ),
                ) {
                    requireScope(request, TokenScope.MEDICATIONS_WRITE)
                    val ctx = RequestContext(request)
                    val body = ctx.body<CreateMedicationRequest>()
                    validateOrBadRequest(validator, body) {
                        val response = medicationService.createMedication(ctx.userId(), body)
                        ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(response)
                    }
                }
            }

            /**
             * Update an existing Medication owned by the consenting user.
             * Reuses [MedicationService.updateMedication], which validates ownership
             * and enforces name uniqueness exactly as the first-party UI does.
             * Requires scope [TokenScope.MEDICATIONS_WRITE].
             */
            PUT("/{id}") { request ->
                handleErrors(
                    extra =
                        mapOf(
                            InsufficientScopeException::class to HttpStatus.FORBIDDEN,
                            ConflictException::class to HttpStatus.CONFLICT,
                        ),
                ) {
                    requireScope(request, TokenScope.MEDICATIONS_WRITE)
                    val ctx = RequestContext(request)
                    val id = ctx.pathId("id")
                    val body = ctx.body<UpdateMedicationRequest>()
                    validateOrBadRequest(validator, body) {
                        val response = medicationService.updateMedication(ctx.userId(), id, body)
                        ServerResponse.ok().bodyValueAndAwait(response)
                    }
                }
            }

            /**
             * Log a dose for a Medication owned by the consenting user.
             * Reuses [MedicationService.logDose], which enforces medication ownership
             * before writing the MedicationLog record.
             * Requires scope [TokenScope.MEDICATIONS_WRITE].
             */
            POST("/{id}/logs") { request ->
                handleErrors(
                    extra = mapOf(InsufficientScopeException::class to HttpStatus.FORBIDDEN),
                ) {
                    requireScope(request, TokenScope.MEDICATIONS_WRITE)
                    val ctx = RequestContext(request)
                    val medicationId = ctx.pathId("id")
                    val body = ctx.body<LogDoseRequest>()
                    validateOrBadRequest(validator, body) {
                        val response = medicationService.logDose(ctx.userId(), medicationId, body)
                        ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(response)
                    }
                }
            }
        }
    }
}
