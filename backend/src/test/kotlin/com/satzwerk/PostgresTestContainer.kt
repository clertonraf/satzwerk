package com.satzwerk

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer

/** Singleton-container base class: the PostgreSQL container starts once on first class load
 *  and stays up for the entire test run, avoiding per-class container churn. */
abstract class PostgresTestContainer {
    /** R2DBC URL of the shared PostgreSQL container for use in custom connection builders. */
    protected val postgresR2dbcUrl: String
        get() = "r2dbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/${postgres.databaseName}"

    companion object {
        // start() is called eagerly so the container is running before @DynamicPropertySource fires.
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine").apply { start() }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.r2dbc.url") {
                "r2dbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/${postgres.databaseName}"
            }
            registry.add("spring.r2dbc.username", postgres::getUsername)
            registry.add("spring.r2dbc.password", postgres::getPassword)
            registry.add("spring.flyway.url", postgres::getJdbcUrl)
            registry.add("spring.flyway.user", postgres::getUsername)
            registry.add("spring.flyway.password", postgres::getPassword)
        }
    }
}
