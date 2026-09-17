package com.satzwerk.common

import io.r2dbc.spi.R2dbcTimeoutException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.reactive.function.server.HandlerStrategies
import org.springframework.web.reactive.function.server.ServerResponse
import java.util.concurrent.TimeoutException

class HandlerSupportTest {
    @Test
    fun `handleErrors maps R2dbcTimeoutException to service unavailable with retry after header`(): Unit =
        runBlocking {
            val response =
                handleErrors {
                    throw R2dbcTimeoutException("Connection acquisition timed out after 3000ms")
                }

            val serverResponse = writeResponse(response)

            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, serverResponse.statusCode)
            assertEquals("5", serverResponse.headers.getFirst("Retry-After"))
        }

    @Test
    fun `handleErrors maps wrapped R2dbcTimeoutException to service unavailable with retry after header`(): Unit =
        runBlocking {
            val response =
                handleErrors {
                    throw DataAccessResourceFailureException(
                        "Failed to obtain R2DBC Connection",
                        R2dbcTimeoutException("Connection acquisition timed out after 3000ms"),
                    )
                }

            val serverResponse = writeResponse(response)

            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, serverResponse.statusCode)
            assertEquals("5", serverResponse.headers.getFirst("Retry-After"))
        }

    @Test
    fun `handleErrors maps wrapped acquisition TimeoutException to 503 with retry after header`(): Unit =
        runBlocking {
            val response =
                handleErrors {
                    throw DataAccessResourceFailureException(
                        "Failed to obtain R2DBC Connection",
                        TimeoutException(
                            "Did not observe any item or terminal signal within 3000ms in " +
                                "'Connection acquisition from [org.springframework.boot.r2dbc." +
                                "OptionsCapableConnectionFactory@12345678]' " +
                                "(and no fallback has been configured)",
                        ),
                    )
                }

            val serverResponse = writeResponse(response)

            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, serverResponse.statusCode)
            assertEquals("5", serverResponse.headers.getFirst("Retry-After"))
        }

    @Test
    fun `handleErrors keeps forbidden mapping unchanged`(): Unit =
        runBlocking {
            val response =
                handleErrors {
                    throw ForbiddenException("Nope")
                }

            val serverResponse = writeResponse(response)

            assertEquals(HttpStatus.FORBIDDEN, serverResponse.statusCode)
            assertNull(serverResponse.headers.getFirst("Retry-After"))
        }

    @Test
    fun `handleErrors rethrows non-pool R2dbcTimeoutException`() {
        assertThrows<R2dbcTimeoutException> {
            runBlocking {
                handleErrors {
                    throw R2dbcTimeoutException("Statement execution timed out after 3000ms")
                }
            }
        }
    }

    @Test
    fun `handleErrors rethrows non-pool cyclic causes without overflowing stack`() {
        val first = CyclicCauseException("first")
        val second = CyclicCauseException("second")
        first.next = second
        second.next = first

        val thrown =
            assertThrows<CyclicCauseException> {
                runBlocking {
                    handleErrors {
                        throw first
                    }
                }
            }

        assertEquals(first, thrown)
    }

    private fun writeResponse(response: ServerResponse): ServerHttpResponse {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build())

        response.writeTo(exchange, RESPONSE_CONTEXT).block()

        return exchange.response
    }

    private companion object {
        private val strategies = HandlerStrategies.withDefaults()

        private val RESPONSE_CONTEXT =
            object : ServerResponse.Context {
                override fun messageWriters() = strategies.messageWriters()

                override fun viewResolvers() = strategies.viewResolvers()
            }
    }

    private class CyclicCauseException(
        message: String,
        var next: Throwable? = null,
    ) : RuntimeException(message) {
        override val cause: Throwable?
            get() = next
    }
}
