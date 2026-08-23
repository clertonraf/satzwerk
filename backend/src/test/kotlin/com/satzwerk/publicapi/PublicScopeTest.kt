package com.satzwerk.publicapi

import com.satzwerk.common.BadRequestException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PublicScopeTest {
    @Test
    fun `list validation rejects unknown scopes`() {
        val error =
            assertThrows<BadRequestException> {
                validatePublicScopes(listOf(PublicScope.EXERCISES_READ, "admin:all"))
            }

        assertEquals("Unknown scopes: admin:all", error.message)
    }

    @Test
    fun `declared-scope validation normalises before validating`() {
        val scopes = validateDeclaredPublicScopes("  EXERCISES:WRITE, analytics:read exercises:write ")

        assertEquals("${PublicScope.ANALYTICS_READ} ${PublicScope.EXERCISES_WRITE}", scopes)
    }

    @Test
    fun `grant validation rejects scopes not declared by app after normalising`() {
        val error =
            assertThrows<BadRequestException> {
                validateGrantedPublicScopes(
                    grantedScopes = "EXERCISES:WRITE plans:write",
                    declaredScopes = PublicScope.EXERCISES_WRITE,
                )
            }

        assertEquals("Scopes not declared by app: plans:write", error.message)
    }
}
