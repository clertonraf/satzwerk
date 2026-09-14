package com.satzwerk.config

import com.satzwerk.PostgresTestContainer
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.spi.ConnectionFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * Verifies that R2DBC connection pooling (issue #283) is actually active: with
 * `io.r2dbc:r2dbc-pool` on the classpath, Spring Boot auto-wraps the raw PostgreSQL
 * `ConnectionFactory` in a [ConnectionPool] whenever `spring.r2dbc.pool.enabled` is true
 * (the default once the pool dependency is present).
 */
@SpringBootTest
class R2dbcPoolingConfigTest : PostgresTestContainer() {
    @Autowired
    lateinit var connectionFactory: ConnectionFactory

    @Test
    fun `connection factory is pooled`() {
        assertThat(connectionFactory).isInstanceOf(ConnectionPool::class.java)
    }

    @Test
    fun `connection pool metrics are exposed`() {
        val pool = connectionFactory as ConnectionPool
        assertThat(pool.metrics).isPresent
    }
}
