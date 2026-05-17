package com.satzwerk.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    val secret: String,
    val expiryMs: Long,
    val refreshExpiryMs: Long,
)
