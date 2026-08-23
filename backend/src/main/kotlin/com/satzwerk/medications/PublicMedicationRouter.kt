package com.satzwerk.medications

import com.satzwerk.common.ConflictException
import com.satzwerk.common.body
import com.satzwerk.common.validateOrBadRequest
import com.satzwerk.publicapi.PartnerWritePolicyService
import com.satzwerk.publicapi.PartnerWriteRequestFingerprintCodec
import com.satzwerk.publicapi.PublicScope
import com.satzwerk.publicapi.PublicWritePrincipalValidationService
import com.satzwerk.publicapi.handlePublicScope
import jakarta.validation.Validator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.coRouter
import kotlin.reflect.KClass

private val publicMedicationWriteErrors: Map<KClass<out Throwable>, HttpStatus> =
    mapOf(
        ConflictException::class to HttpStatus.CONFLICT,
    )

@Configuration
class PublicMedicationRouter {
    @Bean
    fun publicMedicationRoutes(
        medicationService: MedicationService,
        partnerWritePolicyService: PartnerWritePolicyService,
        partnerWritePrincipalValidationService: PublicWritePrincipalValidationService,
        validator: Validator,
    ) = coRouter {
        "/api/public/medications".nest {
            /**
             * Create a Medication for the consenting user.
             * Reuses [MedicationService.createMedication], which enforces per-user
             * case-insensitive name uniqueness and dosage-unit validation.
             * Requires scope [PublicScope.MEDICATIONS_WRITE].
             */
            POST("") { request ->
                handlePublicScope(request, PublicScope.MEDICATIONS_WRITE, extra = publicMedicationWriteErrors) { ctx ->
                    val partnerPrincipal = partnerWritePrincipalValidationService.requireValidPrincipal(ctx)
                    val body = ctx.body<CreateMedicationRequest>()
                    validateOrBadRequest(validator, body) {
                        partnerWritePolicyService.execute(
                            partnerPrincipal,
                            request,
                            HttpStatus.CREATED,
                            PartnerWriteRequestFingerprintCodec.body(body),
                        ) { userId ->
                            medicationService.createMedication(userId, body)
                        }
                    }
                }
            }

            /**
             * Update an existing Medication owned by the consenting user.
             * Reuses [MedicationService.updateMedication], which validates ownership
             * and enforces name uniqueness exactly as the first-party UI does.
             * Requires scope [PublicScope.MEDICATIONS_WRITE].
             */
            PUT("/{id}") { request ->
                handlePublicScope(request, PublicScope.MEDICATIONS_WRITE, extra = publicMedicationWriteErrors) { ctx ->
                    val partnerPrincipal = partnerWritePrincipalValidationService.requireValidPrincipal(ctx)
                    val id = ctx.pathId("id")
                    val body = ctx.body<UpdateMedicationRequest>()
                    validateOrBadRequest(validator, body) {
                        partnerWritePolicyService.execute(
                            partnerPrincipal,
                            request,
                            HttpStatus.OK,
                            PartnerWriteRequestFingerprintCodec.body(body),
                        ) { userId ->
                            medicationService.updateMedication(userId, id, body)
                        }
                    }
                }
            }

            /**
             * Log a dose for a Medication owned by the consenting user.
             * Reuses [MedicationService.logDose], which enforces medication ownership
             * before writing the MedicationLog record.
             * Requires scope [PublicScope.MEDICATIONS_WRITE].
             */
            POST("/{id}/logs") { request ->
                handlePublicScope(request, PublicScope.MEDICATIONS_WRITE, extra = publicMedicationWriteErrors) { ctx ->
                    val partnerPrincipal = partnerWritePrincipalValidationService.requireValidPrincipal(ctx)
                    val medicationId = ctx.pathId("id")
                    val body = ctx.body<LogDoseRequest>()
                    validateOrBadRequest(validator, body) {
                        partnerWritePolicyService.execute(
                            partnerPrincipal,
                            request,
                            HttpStatus.CREATED,
                            PartnerWriteRequestFingerprintCodec.body(body),
                        ) { userId ->
                            medicationService.logDose(userId, medicationId, body)
                        }
                    }
                }
            }
        }
    }
}
