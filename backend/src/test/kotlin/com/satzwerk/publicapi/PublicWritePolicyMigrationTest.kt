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
}
