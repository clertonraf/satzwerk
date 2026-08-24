package com.satzwerk.publicapi

import com.satzwerk.PostgresTestContainer
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

class PublicWritePolicyMigrationTest : PostgresTestContainer() {
    @Test
    fun `V21 migrates legacy idempotency uniqueness to the new principal and credential key`() {
        val schema = randomSchema()

        createSchema(schema)
        migrateSchema(schema)

        assertMigratedIdempotencyConstraints(schema)
    }

    @Test
    fun `V21 upgrades legacy partner-write rows into public write tables with backfilled identity fields`() {
        val fixture = LegacyPartnerWriteFixture()

        createSchema(fixture.schema)
        migrateSchema(fixture.schema, "19")
        withSchemaConnection(fixture.schema) { connection ->
            seedLegacyPartnerWriteRows(connection, fixture)
        }

        migrateSchema(fixture.schema)
        withSchemaConnection(fixture.schema) { connection ->
            assertMigratedIdempotencyRecords(connection, fixture)
            assertMigratedAuditEntry(connection, fixture)
        }
    }

    private fun assertMigratedIdempotencyConstraints(schema: String) {
        withSchemaConnection(schema) { connection ->
            val uniqueConstraints = findUniqueConstraints(connection)
            assertEquals(1, uniqueConstraints.size)
            assertEquals(
                "uq_public_write_idempotency_records_identity_key",
                uniqueConstraints.single().first,
            )
            assertEquals(
                "principal_type,credential_id,request_method,request_path,idempotency_key",
                uniqueConstraints.single().second,
            )

            val indexNames = findIdempotencyIndexNames(connection)
            assertFalse(indexNames.contains("idx_idempotency_records_grant_id"))
            assertFalse(
                indexNames.contains(
                    "idempotency_records_grant_id_request_method_request_path_id_key",
                ),
            )
            assertTrue(indexNames.contains("idx_public_write_idempotency_records_identity"))
        }
    }

    private fun findUniqueConstraints(connection: Connection): List<Pair<String, String>> =
        connection.prepareStatement(
            """
            SELECT c.conname, string_agg(a.attname, ',' ORDER BY u.ordinality) AS columns
            FROM pg_constraint c
            JOIN pg_class t ON t.oid = c.conrelid
            JOIN pg_namespace n ON n.oid = t.relnamespace
            JOIN unnest(c.conkey) WITH ORDINALITY AS u(attnum, ordinality) ON true
            JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = u.attnum
            WHERE n.nspname = ?
              AND t.relname = 'public_write_idempotency_records'
              AND c.contype = 'u'
            GROUP BY c.conname
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, connection.schema)
            statement.executeQuery().use { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(resultSet.getString("conname") to resultSet.getString("columns"))
                    }
                }
            }
        }

    private fun findIdempotencyIndexNames(connection: Connection): Set<String> =
        connection.prepareStatement(
            """
            SELECT indexname
            FROM pg_indexes
            WHERE schemaname = ?
              AND tablename = 'public_write_idempotency_records'
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, connection.schema)
            statement.executeQuery().use { resultSet ->
                buildSet {
                    while (resultSet.next()) {
                        add(resultSet.getString("indexname"))
                    }
                }
            }
        }

    private fun seedLegacyPartnerWriteRows(
        connection: Connection,
        fixture: LegacyPartnerWriteFixture,
    ) {
        insertLegacyUser(connection, fixture)
        insertLegacyPartnerApp(connection, fixture)
        insertLegacyGrant(connection, fixture)
        insertLegacyIdempotencyRecords(connection, fixture)
        insertLegacyAuditEntry(connection, fixture)
    }

    private fun insertLegacyUser(
        connection: Connection,
        fixture: LegacyPartnerWriteFixture,
    ) {
        connection.prepareStatement(
            """
            INSERT INTO users (id, email, password_hash, display_name)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, fixture.userId)
            statement.setString(2, "migration-${fixture.userId}@example.com")
            statement.setString(3, "hashed")
            statement.setString(4, "Migration User")
            statement.executeUpdate()
        }
    }

    private fun insertLegacyPartnerApp(
        connection: Connection,
        fixture: LegacyPartnerWriteFixture,
    ) {
        connection.prepareStatement(
            """
            INSERT INTO partner_apps (
                id,
                name,
                description,
                redirect_uri,
                client_id,
                client_secret_hash,
                scopes
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, fixture.appId)
            statement.setString(2, "Migration Partner")
            statement.setString(3, "Legacy partner-write migration fixture")
            statement.setString(4, "https://example.com/callback")
            statement.setString(5, "client-${fixture.appId}")
            statement.setString(6, "secret-hash")
            statement.setString(7, "exercises:write")
            statement.executeUpdate()
        }
    }

    private fun insertLegacyGrant(
        connection: Connection,
        fixture: LegacyPartnerWriteFixture,
    ) {
        connection.prepareStatement(
            """
            INSERT INTO app_grants (
                id,
                app_id,
                user_id,
                granted_scopes,
                access_token_hash
            )
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, fixture.grantId)
            statement.setObject(2, fixture.appId)
            statement.setObject(3, fixture.userId)
            statement.setString(4, "exercises:write")
            statement.setString(5, "token-${fixture.grantId}")
            statement.executeUpdate()
        }
    }

    private fun insertLegacyIdempotencyRecords(
        connection: Connection,
        fixture: LegacyPartnerWriteFixture,
    ) {
        connection.prepareStatement(
            """
            INSERT INTO idempotency_records (
                id,
                grant_id,
                request_method,
                request_path,
                idempotency_key,
                request_fingerprint,
                response_status,
                response_body
            )
            VALUES
                (?, ?, 'POST', '/api/public/exercises', 'legacy-completed', '', 201, '{"id":"exercise-1"}'),
                (?, ?, 'POST', '/api/public/exercises', 'legacy-pending', '', -1, '__pending__')
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, fixture.completedRecordId)
            statement.setObject(2, fixture.grantId)
            statement.setObject(3, fixture.pendingRecordId)
            statement.setObject(4, fixture.grantId)
            statement.executeUpdate()
        }
    }

    private fun insertLegacyAuditEntry(
        connection: Connection,
        fixture: LegacyPartnerWriteFixture,
    ) {
        connection.prepareStatement(
            """
            INSERT INTO partner_write_audit (
                id,
                grant_id,
                app_id,
                user_id,
                request_method,
                request_path,
                idempotency_key,
                response_status,
                granted_scopes
            )
            VALUES (?, ?, ?, ?, 'POST', '/api/public/exercises', 'legacy-completed', 201, '')
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, fixture.auditId)
            statement.setObject(2, fixture.grantId)
            statement.setObject(3, fixture.appId)
            statement.setObject(4, fixture.userId)
            statement.executeUpdate()
        }
    }

    private fun assertMigratedIdempotencyRecords(
        connection: Connection,
        fixture: LegacyPartnerWriteFixture,
    ) {
        val records =
            connection.prepareStatement(
                """
                SELECT id, principal_type, credential_id, app_id, grant_id, user_id, request_fingerprint,
                    response_status
                FROM public_write_idempotency_records
                WHERE id IN (?, ?)
                ORDER BY idempotency_key
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, fixture.completedRecordId)
                statement.setObject(2, fixture.pendingRecordId)
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            add(
                                resultSet.getObject("id", UUID::class.java) to
                                    listOf(
                                        resultSet.getString("principal_type"),
                                        resultSet.getObject("credential_id", UUID::class.java).toString(),
                                        resultSet.getObject("app_id", UUID::class.java)?.toString(),
                                        resultSet.getObject("grant_id", UUID::class.java)?.toString(),
                                        resultSet.getObject("user_id", UUID::class.java)?.toString(),
                                        resultSet.getString("request_fingerprint"),
                                        resultSet.getInt("response_status").toString(),
                                    ),
                            )
                        }
                    }.toMap()
                }
            }

        assertEquals(
            listOf(
                "PARTNER_APP",
                fixture.grantId.toString(),
                fixture.appId.toString(),
                fixture.grantId.toString(),
                fixture.userId.toString(),
                "__legacy_no_fingerprint__",
                "201",
            ),
            records[fixture.completedRecordId],
        )
        assertEquals(
            listOf(
                "PARTNER_APP",
                fixture.grantId.toString(),
                fixture.appId.toString(),
                fixture.grantId.toString(),
                fixture.userId.toString(),
                "",
                "-1",
            ),
            records[fixture.pendingRecordId],
        )
    }

    private fun assertMigratedAuditEntry(
        connection: Connection,
        fixture: LegacyPartnerWriteFixture,
    ) {
        connection.prepareStatement(
            """
            SELECT principal_type, credential_id, app_id, grant_id, user_id, granted_scopes
            FROM public_write_audit
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, fixture.auditId)
            statement.executeQuery().use { resultSet ->
                assertTrue(resultSet.next())
                assertEquals("PARTNER_APP", resultSet.getString("principal_type"))
                assertEquals(fixture.grantId, resultSet.getObject("credential_id", UUID::class.java))
                assertEquals(fixture.appId, resultSet.getObject("app_id", UUID::class.java))
                assertEquals(fixture.grantId, resultSet.getObject("grant_id", UUID::class.java))
                assertEquals(fixture.userId, resultSet.getObject("user_id", UUID::class.java))
                assertEquals("", resultSet.getString("granted_scopes"))
            }
        }
    }

    private fun createSchema(schema: String) {
        DriverManager.getConnection(
            postgres.jdbcUrl,
            postgres.username,
            postgres.password,
        ).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("""CREATE SCHEMA "$schema"""")
            }
        }
    }

    private fun migrateSchema(
        schema: String,
        targetVersion: String? = null,
    ) {
        val configuration =
            Flyway
                .configure()
                .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                .defaultSchema(schema)
                .schemas(schema)
                .locations("classpath:db/migration")
        if (targetVersion != null) {
            configuration.target(targetVersion)
        }
        configuration.load().migrate()
    }

    private fun <T> withSchemaConnection(
        schema: String,
        block: (Connection) -> T,
    ): T =
        DriverManager.getConnection(
            postgres.jdbcUrl,
            postgres.username,
            postgres.password,
        ).use { connection ->
            connection.schema = schema
            block(connection)
        }
}

private data class LegacyPartnerWriteFixture(
    val schema: String = randomSchema(),
    val userId: UUID = UUID.randomUUID(),
    val appId: UUID = UUID.randomUUID(),
    val grantId: UUID = UUID.randomUUID(),
    val completedRecordId: UUID = UUID.randomUUID(),
    val pendingRecordId: UUID = UUID.randomUUID(),
    val auditId: UUID = UUID.randomUUID(),
)

private fun randomSchema(): String = "migration_${UUID.randomUUID().toString().replace("-", "")}"
