package com.satzwerk.users

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("users")
data class User(
    @Id
    val id: UUID? = null,
    val email: String,
    @Column("password_hash")
    val passwordHash: String,
    @Column("display_name")
    val displayName: String,
    @Column("created_at")
    val createdAt: Instant = Instant.now(),
)
