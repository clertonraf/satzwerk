package com.satzwerk.workouts

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.VALUE_PARAMETER,
)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [AdvancedTechniqueValidator::class])
annotation class ValidAdvancedTechnique(
    val message: String = "must be a valid advanced technique",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class AdvancedTechniqueValidator : ConstraintValidator<ValidAdvancedTechnique, String?> {
    override fun isValid(
        value: String?,
        context: ConstraintValidatorContext,
    ): Boolean = value == null || AdvancedTechnique.entries.any { it.name == value }
}
