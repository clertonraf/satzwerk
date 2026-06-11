package com.satzwerk.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class OwnedTest {
    data class Widget(val name: String)

    @Test
    fun `assertOwner does not throw when requester is the owner`() {
        val ownerId = UUID.randomUUID()
        val owned = Owned(Widget("barbell"), ownerId)

        owned.assertOwner(ownerId)
    }

    @Test
    fun `assertOwner throws ForbiddenException when requester is not the owner`() {
        val ownerId = UUID.randomUUID()
        val requesterId = UUID.randomUUID()
        val owned = Owned(Widget("barbell"), ownerId)

        val ex =
            assertThrows<ForbiddenException> {
                owned.assertOwner(requesterId)
            }
        assertEquals("resource does not belong to user", ex.message)
    }

    @Test
    fun `assertOwner throws ForbiddenException with custom resource name`() {
        val ownerId = UUID.randomUUID()
        val requesterId = UUID.randomUUID()
        val owned = Owned(Widget("barbell"), ownerId)

        val ex =
            assertThrows<ForbiddenException> {
                owned.assertOwner(requesterId, "Exercise")
            }
        assertEquals("Exercise does not belong to user", ex.message)
    }
}
