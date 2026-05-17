package com.satzwerk.common

data class ErrorResponse(
    val error: String,
)

data class ValidationErrorResponse(
    val errors: Map<String, String>,
)
