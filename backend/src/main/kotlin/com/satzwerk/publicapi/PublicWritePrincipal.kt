package com.satzwerk.publicapi

import java.util.UUID

enum class PublicWritePrincipalType {
    PERSONAL_API_TOKEN,
    PARTNER_APP,
}

data class PublicWritePrincipal(
    val principalType: PublicWritePrincipalType,
    val userId: UUID,
    val credentialId: UUID,
    val scopes: Set<String>,
    val appId: UUID? = null,
    val grantId: UUID? = null,
)
