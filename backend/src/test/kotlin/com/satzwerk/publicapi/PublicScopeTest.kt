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

        assertEquals(
            "${PublicScope.ANALYTICS_READ} ${PublicScope.EXERCISES_WRITE}",
            scopes,
        )
    }

    @Test
    fun `partner declared-scope validation rejects metrics read`() {
        val error =
            assertThrows<BadRequestException> {
                validateDeclaredPublicScopes(PublicScope.METRICS_READ)
            }

        assertEquals("Unknown scopes: ${PublicScope.METRICS_READ}", error.message)
    }

    @Test
    fun `partner declared-scope validation still accepts every non-metrics scope`() {
        val scopes = validateDeclaredPublicScopes(PublicScope.partnerGrantable.joinToString(" "))

        assertEquals(PublicScope.partnerGrantable.sorted().joinToString(" "), scopes)
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

    @Test
    fun `partner grant validation rejects metrics read even when legacy app declares it`() {
        val error =
            assertThrows<BadRequestException> {
                validateGrantedPublicScopes(
                    grantedScopes = PublicScope.METRICS_READ,
                    declaredScopes = "${PublicScope.EXERCISES_READ} ${PublicScope.METRICS_READ}",
                )
            }

        assertEquals("Unknown scopes: ${PublicScope.METRICS_READ}", error.message)
    }

    @Test
    fun `partner grant validation still accepts every non-metrics scope`() {
        val scopes =
            validateGrantedPublicScopes(
                grantedScopes = PublicScope.partnerGrantable.joinToString(" "),
                declaredScopes = PublicScope.partnerGrantable.joinToString(" "),
            )

        assertEquals(PublicScope.partnerGrantable.sorted().joinToString(" "), scopes)
    }
}
