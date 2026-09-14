package com.satzwerk.cache

class CacheInvalidationException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
