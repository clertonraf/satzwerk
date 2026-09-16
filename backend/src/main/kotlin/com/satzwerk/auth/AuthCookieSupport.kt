package com.satzwerk.auth

import org.springframework.core.env.Environment
import org.springframework.http.ResponseCookie

const val REFRESH_COOKIE_NAME = "refresh_token"
const val REFRESH_CSRF_COOKIE_NAME = "refresh_csrf"
const val REFRESH_CSRF_HEADER_NAME = "X-CSRF-Token"

private const val REFRESH_COOKIE_PATH = "/api/auth"
private const val REFRESH_COOKIE_SAME_SITE = "Lax"

fun refreshTokenCookie(
    token: String,
    maxAgeSeconds: Long,
    environment: Environment,
): ResponseCookie =
    ResponseCookie
        .from(REFRESH_COOKIE_NAME, token)
        .httpOnly(true)
        .secure(environment.usesSecureRefreshCookies())
        .sameSite(REFRESH_COOKIE_SAME_SITE)
        .path(REFRESH_COOKIE_PATH)
        .maxAge(maxAgeSeconds)
        .build()

fun refreshCsrfCookie(
    token: String,
    maxAgeSeconds: Long,
    environment: Environment,
): ResponseCookie =
    ResponseCookie
        .from(REFRESH_CSRF_COOKIE_NAME, token)
        .httpOnly(false)
        .secure(environment.usesSecureRefreshCookies())
        .sameSite(REFRESH_COOKIE_SAME_SITE)
        .path(REFRESH_COOKIE_PATH)
        .maxAge(maxAgeSeconds)
        .build()

fun clearRefreshTokenCookie(environment: Environment): ResponseCookie =
    refreshTokenCookie(token = "", maxAgeSeconds = 0, environment = environment)

fun clearRefreshCsrfCookie(environment: Environment): ResponseCookie =
    refreshCsrfCookie(token = "", maxAgeSeconds = 0, environment = environment)

private fun Environment.usesSecureRefreshCookies(): Boolean = activeProfiles.any { it == "prod" }
