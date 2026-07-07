package com.satzwerk

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer

/**
 * Singleton-container base class for integration tests.
 *
 * The PostgreSQL container starts once on first class load and stays up for the entire test run,
 * avoiding per-class container churn. Uses Testcontainers' singleton-container pattern
 * (`.apply { start() }`) so the container is guaranteed to be running before
 * `@DynamicPropertySource` fires during Spring context initialisation.
 */
abstract class PostgresTestContainer {
    companion object {
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine").apply { start() }

        private fun r2dbcUrl() =
            "r2dbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/${postgres.databaseName}"

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.r2dbc.url", ::r2dbcUrl)
            registry.add("spring.r2dbc.username", postgres::getUsername)
            registry.add("spring.r2dbc.password", postgres::getPassword)
            registry.add("spring.flyway.url", postgres::getJdbcUrl)
            registry.add("spring.flyway.user", postgres::getUsername)
            registry.add("spring.flyway.password", postgres::getPassword)
        }
    }

    /** R2DBC URL of the shared PostgreSQL container for use in custom connection builders. */
    protected val postgresR2dbcUrl: String get() = r2dbcUrl()
}
