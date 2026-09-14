package com.satzwerk.cache

data class VersionedCacheValue<T>(
    val version: Long,
    val value: T?,
)
