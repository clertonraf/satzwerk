package com.satzwerk.publicapi

import com.satzwerk.PostgresTestContainer
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.util.UUID

class PublicWritePolicyMigrationTest : PostgresTestContainer() {
    @Test
    fun `V20 migrates legacy idempotency uniqueness to the new principal and credential key`() {
        val schema = "migration_${UUID.randomUUID().toString().replace("-", "")}"

        DriverManager.getConnection(
            postgres.jdbcUrl,
            postgres.username,
            postgres.password,
        ).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("""CREATE SCHEMA "$schema"""")
            }
        }

        Flyway
            .configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .defaultSchema(schema)
            .schemas(schema)
            .locations("classpath:db/migration")
            .load()
            .migrate()

        DriverManager.getConnection(
            postgres.jdbcUrl,
            postgres.username,
            postgres.password,
        ).use { connection ->
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
                statement.setString(1, schema)

                statement.executeQuery().use { resultSet ->
                    val uniqueConstraints =
                        buildList {
                            while (resultSet.next()) {
                                add(resultSet.getString("conname") to resultSet.getString("columns"))
                            }
                        }

                    assertEquals(1, uniqueConstraints.size)
                    assertEquals(
                        "uq_public_write_idempotency_records_identity_key",
                        uniqueConstraints.single().first,
                    )
                    assertEquals(
                        "principal_type,credential_id,request_method,request_path,idempotency_key",
                        uniqueConstraints.single().second,
                    )
                }
            }

            connection.prepareStatement(
                """
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = ?
                  AND tablename = 'public_write_idempotency_records'
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, schema)

                statement.executeQuery().use { resultSet ->
                    val indexNames =
                        buildSet {
                            while (resultSet.next()) {
                                add(resultSet.getString("indexname"))
                            }
                        }

                    assertFalse(indexNames.contains("idx_idempotency_records_grant_id"))
                    assertFalse(
                        indexNames.contains(
                            "idempotency_records_grant_id_request_method_request_path_id_key",
                        ),
                    )
                    assertTrue(indexNames.contains("idx_public_write_idempotency_records_identity"))
                }
            }
        }
    }

    @Test
    fun `V20 upgrades legacy partner-write rows into public write tables with backfilled identity fields`() {
        val schema = "migration_${UUID.randomUUID().toString().replace("-", "")}"
        val userId = UUID.randomUUID()
        val appId = UUID.randomUUID()
        val grantId = UUID.randomUUID()
        val completedRecordId = UUID.randomUUID()
        val pendingRecordId = UUID.randomUUID()
        val auditId = UUID.randomUUID()

        createSchema(schema)
        migrateSchema(schema, "19")

        DriverManager.getConnection(
            postgres.jdbcUrl,
            postgres.username,
            postgres.password,
        ).use { connection ->
            connection.schema = schema
            connection.prepareStatement(
                """
                    INSERT INTO users (id, email, password_hash, display_name)
                    VALUES (?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, userId)
                statement.setString(2, "migration-${userId}@example.com")
                statement.setString(3, "hashed")
                statement.setString(4, "Migration User")
                statement.executeUpdate()
            }

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
                statement.setObject(1, appId)
                statement.setString(2, "Migration Partner")
                statement.setString(3, "Legacy partner-write migration fixture")
                statement.setString(4, "https://example.com/callback")
                statement.setString(5, "client-$appId")
                statement.setString(6, "secret-hash")
                statement.setString(7, "exercises:write")
                statement.executeUpdate()
            }

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
                statement.setObject(1, grantId)
                statement.setObject(2, appId)
                statement.setObject(3, userId)
                statement.setString(4, "exercises:write")
                statement.setString(5, "token-$grantId")
                statement.executeUpdate()
            }

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
                statement.setObject(1, completedRecordId)
                statement.setObject(2, grantId)
                statement.setObject(3, pendingRecordId)
                statement.setObject(4, grantId)
                statement.executeUpdate()
            }

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
                statement.setObject(1, auditId)
                statement.setObject(2, grantId)
                statement.setObject(3, appId)
                statement.setObject(4, userId)
                statement.executeUpdate()
            }
        }

        migrateSchema(schema)

        DriverManager.getConnection(
            postgres.jdbcUrl,
            postgres.username,
            postgres.password,
        ).use { connection ->
            connection.schema = schema
            connection.prepareStatement(
                """
                    SELECT id, principal_type, credential_id, request_fingerprint, response_status
                    FROM public_write_idempotency_records
                    WHERE id IN (?, ?)
                    ORDER BY idempotency_key
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, completedRecordId)
                statement.setObject(2, pendingRecordId)

                statement.executeQuery().use { resultSet ->
                    val records =
                        buildList {
                            while (resultSet.next()) {
                                add(
                                    resultSet.getObject("id", UUID::class.java) to
                                        listOf(
                                            resultSet.getString("principal_type"),
                                            resultSet.getObject("credential_id", UUID::class.java).toString(),
                                            resultSet.getString("request_fingerprint"),
                                            resultSet.getInt("response_status").toString(),
                                        ),
                                )
                            }
                        }.toMap()

                    assertEquals(
                        listOf("PARTNER_APP", grantId.toString(), "", "201"),
                        records[completedRecordId],
                    )
                    assertEquals(
                        listOf("PARTNER_APP", grantId.toString(), "", "-1"),
                        records[pendingRecordId],
                    )
                }
            }

            connection.prepareStatement(
                """
                    SELECT principal_type, credential_id, app_id, grant_id, user_id, granted_scopes
                    FROM public_write_audit
                    WHERE id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, auditId)

                statement.executeQuery().use { resultSet ->
                    assertTrue(resultSet.next())
                    assertEquals("PARTNER_APP", resultSet.getString("principal_type"))
                    assertEquals(grantId, resultSet.getObject("credential_id", UUID::class.java))
                    assertEquals(appId, resultSet.getObject("app_id", UUID::class.java))
                    assertEquals(grantId, resultSet.getObject("grant_id", UUID::class.java))
                    assertEquals(userId, resultSet.getObject("user_id", UUID::class.java))
                    assertEquals("", resultSet.getString("granted_scopes"))
                }
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
}
