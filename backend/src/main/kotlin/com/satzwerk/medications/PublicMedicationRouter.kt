package com.satzwerk.medications

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
import kotlin.reflect.KClass

private val publicMedicationWriteErrors: Map<KClass<out Throwable>, HttpStatus> =
    mapOf(
        InsufficientScopeException::class to HttpStatus.FORBIDDEN,
        ConflictException::class to HttpStatus.CONFLICT,
    )

@Configuration
class PublicMedicationRouter {
    @Bean
    fun publicMedicationRoutes(
        medicationService: MedicationService,
        partnerWritePolicyService: PartnerWritePolicyService,
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
                handleErrors(extra = publicMedicationWriteErrors) {
                    requireScope(request, TokenScope.MEDICATIONS_WRITE)
                    val ctx = RequestContext(request)
                    val body = ctx.body<CreateMedicationRequest>()
                    validateOrBadRequest(validator, body) {
                        partnerWritePolicyService.execute(request, HttpStatus.CREATED, body) { userId ->
                            medicationService.createMedication(userId, body)
                        }
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
                handleErrors(extra = publicMedicationWriteErrors) {
                    requireScope(request, TokenScope.MEDICATIONS_WRITE)
                    val ctx = RequestContext(request)
                    val id = ctx.pathId("id")
                    val body = ctx.body<UpdateMedicationRequest>()
                    validateOrBadRequest(validator, body) {
                        partnerWritePolicyService.execute(request, HttpStatus.OK, body) { userId ->
                            medicationService.updateMedication(userId, id, body)
                        }
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
                handleErrors(extra = publicMedicationWriteErrors) {
                    requireScope(request, TokenScope.MEDICATIONS_WRITE)
                    val ctx = RequestContext(request)
                    val medicationId = ctx.pathId("id")
                    val body = ctx.body<LogDoseRequest>()
                    validateOrBadRequest(validator, body) {
                        partnerWritePolicyService.execute(request, HttpStatus.CREATED, body) { userId ->
                            medicationService.logDose(userId, medicationId, body)
                        }
                    }
                }
            }
        }
    }
}
