package com.satzwerk.medications

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import jakarta.validation.constraints.Min

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = FrequencySpec.Daily::class, name = "DAILY"),
    JsonSubTypes.Type(value = FrequencySpec.Weekly::class, name = "WEEKLY"),
    JsonSubTypes.Type(value = FrequencySpec.Monthly::class, name = "MONTHLY"),
)
sealed class FrequencySpec {
    abstract val type: String

    data class Daily(
        override val type: String = "DAILY",
        @field:Min(1)
        val timesPerDay: Int,
        val times: List<String> = emptyList(),
    ) : FrequencySpec()

    data class Weekly(
        override val type: String = "WEEKLY",
        @field:Min(1)
        val timesPerWeek: Int,
        val weekdays: List<Int> = emptyList(),
    ) : FrequencySpec()

    data class Monthly(
        override val type: String = "MONTHLY",
        @field:Min(1)
        val timesPerMonth: Int,
        val daysOfMonth: List<Int> = emptyList(),
    ) : FrequencySpec()
}
