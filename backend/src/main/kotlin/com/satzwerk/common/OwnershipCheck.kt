package com.satzwerk.common

import java.util.UUID

fun requireOwnership(
    ownerId: UUID,
    requesterId: UUID,
    entity: String = "resource",
) {
    if (ownerId != requesterId) {
        throw ForbiddenException("$entity does not belong to user")
    }
}
