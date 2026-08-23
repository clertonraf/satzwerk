package com.satzwerk.medications

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper
import io.r2dbc.postgresql.codec.Json
import jakarta.validation.constraints.Min
import org.springframework.stereotype.Component
import java.time.LocalDate

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = FrequencySpec.Daily::class, name = "DAILY"),
    JsonSubTypes.Type(value = FrequencySpec.Weekly::class, name = "WEEKLY"),
    JsonSubTypes.Type(value = FrequencySpec.Monthly::class, name = "MONTHLY"),
)
sealed class FrequencySpec {
    abstract val type: String

    abstract fun scheduledCountOn(day: LocalDate): Int

    fun isDueOn(day: LocalDate): Boolean = scheduledCountOn(day) > 0

    data class Daily(
        override val type: String = "DAILY",
        @field:Min(1)
        val timesPerDay: Int,
        val times: List<String> = emptyList(),
    ) : FrequencySpec() {
        override fun scheduledCountOn(day: LocalDate): Int = timesPerDay
    }

    data class Weekly(
        override val type: String = "WEEKLY",
        @field:Min(1)
        val timesPerWeek: Int,
        val weekdays: List<Int> = emptyList(),
    ) : FrequencySpec() {
        override fun scheduledCountOn(day: LocalDate): Int {
            val isoDay = day.dayOfWeek.value
            return if (weekdays.isEmpty() || isoDay in weekdays) 1 else 0
        }
    }

    data class Monthly(
        override val type: String = "MONTHLY",
        @field:Min(1)
        val timesPerMonth: Int,
        val daysOfMonth: List<Int> = emptyList(),
    ) : FrequencySpec() {
        override fun scheduledCountOn(day: LocalDate): Int {
            if (daysOfMonth.isEmpty()) return 1

            val daysInMonth = day.lengthOfMonth()
            val matchesExplicitDay = day.dayOfMonth in daysOfMonth
            val clampsToMonthEnd = day.dayOfMonth == daysInMonth && daysOfMonth.any { it > daysInMonth }
            return if (matchesExplicitDay || clampsToMonthEnd) 1 else 0
        }
    }
}

@Component
class FrequencySpecModule(
    private val objectMapper: ObjectMapper,
) {
    fun serialize(spec: FrequencySpec): Json = Json.of(objectMapper.writeValueAsString(spec))

    fun deserialize(json: Json): FrequencySpec = objectMapper.readValue(json.asString(), FrequencySpec::class.java)
}
