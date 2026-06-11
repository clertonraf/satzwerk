package com.satzwerk.common

import java.util.UUID

data class Owned<T>(val value: T, val ownerId: UUID)

fun <T> Owned<T>.assertOwner(
    requesterId: UUID,
    resourceName: String = "resource",
) {
    if (ownerId != requesterId) {
        throw ForbiddenException("$resourceName does not belong to user")
    }
}
