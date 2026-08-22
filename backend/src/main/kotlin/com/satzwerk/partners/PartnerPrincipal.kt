package com.satzwerk.partners

import java.security.Principal

/**
 * Spring Security principal for an authenticated partner app request.
 *
 * The [name] is the userId (UUID string) so that [com.satzwerk.common.RequestContext.userId]
 * works unchanged — partner-authenticated routes resolve the acting user identically to
 * first-party JWT routes.
 *
 * Downstream code that needs to check scopes or inspect the app identity can cast the
 * principal to [PartnerPrincipal] after obtaining it from the security context.
 */
data class PartnerPrincipal(
    /** UUID string of the consenting user — used as [Principal.name] for compatibility. */
    val userId: String,
    val appId: String,
    val grantId: String,
    /** Space-separated scopes granted for this token. */
    val grantedScopes: String,
) : Principal {
    override fun getName(): String = userId
}
