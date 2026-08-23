package com.satzwerk.publicapi

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.satzwerk.common.ConflictException
import com.satzwerk.common.PartnerAppRequestPrincipal
import com.satzwerk.partners.PartnerPrincipal
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.ServerRequest
import java.util.UUID

class PartnerWritePolicyServiceTest {
    @Test
    fun `execute finalizes a claimed idempotency record before returning`(): Unit =
        runBlocking {
            val claimedRecord = pendingRecord(id = UUID.randomUUID())
            val idempotencyRecordRepository =
                mock<IdempotencyRecordRepository> {
                    onBlocking { claim(any(), any(), any(), any(), any()) } doReturn claimedRecord
                    onBlocking { save(any()) } doAnswer { invocation ->
                        invocation.arguments[0] as IdempotencyRecord
                    }
                }
            val partnerWriteAuditRepository =
                mock<PartnerWriteAuditRepository> {
                    onBlocking { save(any()) } doAnswer { invocation ->
                        invocation.arguments[0] as PartnerWriteAuditEntry
                    }
                }
            val service =
                PartnerWritePolicyService(
                    idempotencyRecordRepository = idempotencyRecordRepository,
                    partnerWriteAuditRepository = partnerWriteAuditRepository,
                    objectMapper = jacksonObjectMapper(),
                )

            val response =
                service.execute(
                    partnerPrincipal(),
                    request(),
                    HttpStatus.CREATED,
                    ExampleRequest(name = "Bench Press"),
                ) {
                    ExampleResponse(name = "Bench Press")
                }

            assertEquals(HttpStatus.CREATED, response.statusCode())
            val savedRecord = argumentCaptor<IdempotencyRecord>()
            val savedAudit = argumentCaptor<PartnerWriteAuditEntry>()
            verify(idempotencyRecordRepository).save(savedRecord.capture())
            verify(partnerWriteAuditRepository).save(savedAudit.capture())
            assertEquals(HttpStatus.CREATED.value(), savedRecord.firstValue.responseStatus)
            assertEquals("""{"name":"Bench Press"}""", savedRecord.firstValue.responseBody)
            assertEquals("""{"name":"Bench Press"}""", savedRecord.firstValue.requestFingerprint)
            assertEquals("exercises:write", savedAudit.firstValue.grantedScopes)
        }

    @Test
    fun `execute replays a completed record when another request already claimed the key`(): Unit =
        runBlocking {
            val completedRecord =
                pendingRecord(id = UUID.randomUUID()).copy(
                    responseStatus = HttpStatus.CREATED.value(),
                    responseBody = """{"name":"Bench Press"}""",
                )
            val idempotencyRecordRepository =
                mock<IdempotencyRecordRepository> {
                    onBlocking { claim(any(), any(), any(), any(), any()) } doReturn null
                    onBlocking {
                        findByGrantIdAndRequestMethodAndRequestPathAndIdempotencyKey(
                            any(),
                            any(),
                            any(),
                            any(),
                        )
                    } doReturn completedRecord
                }
            val partnerWriteAuditRepository =
                mock<PartnerWriteAuditRepository> {
                    onBlocking { save(any()) } doAnswer { invocation ->
                        invocation.arguments[0] as PartnerWriteAuditEntry
                    }
                }
            val service =
                PartnerWritePolicyService(
                    idempotencyRecordRepository = idempotencyRecordRepository,
                    partnerWriteAuditRepository = partnerWriteAuditRepository,
                    objectMapper = jacksonObjectMapper(),
                )

            var executed = false
            val response =
                service.execute(
                    partnerPrincipal(),
                    request(),
                    HttpStatus.CREATED,
                    ExampleRequest(name = "Bench Press"),
                ) {
                    executed = true
                    ExampleResponse(name = "Should not run")
                }

            assertEquals(HttpStatus.CREATED, response.statusCode())
            assertFalse(executed)
            verify(idempotencyRecordRepository, never()).save(any())
        }

    @Test
    fun `execute rejects a different payload for an existing idempotency key`(): Unit =
        runBlocking {
            val completedRecord =
                pendingRecord(id = UUID.randomUUID()).copy(
                    requestFingerprint = """{"name":"Bench Press"}""",
                    responseStatus = HttpStatus.CREATED.value(),
                    responseBody = """{"name":"Bench Press"}""",
                )
            val idempotencyRecordRepository =
                mock<IdempotencyRecordRepository> {
                    onBlocking { claim(any(), any(), any(), any(), any()) } doReturn null
                    onBlocking {
                        findByGrantIdAndRequestMethodAndRequestPathAndIdempotencyKey(
                            any(),
                            any(),
                            any(),
                            any(),
                        )
                    } doReturn completedRecord
                }
            val partnerWriteAuditRepository = mock<PartnerWriteAuditRepository>()
            val service =
                PartnerWritePolicyService(
                    idempotencyRecordRepository = idempotencyRecordRepository,
                    partnerWriteAuditRepository = partnerWriteAuditRepository,
                    objectMapper = jacksonObjectMapper(),
                )

            val error =
                org.junit.jupiter.api.assertThrows<ConflictException> {
                    runBlocking {
                        service.execute(
                            partnerPrincipal(),
                            request(),
                            HttpStatus.CREATED,
                            ExampleRequest(name = "Changed Bench"),
                        ) {
                            ExampleResponse(name = "Should not run")
                        }
                    }
                }

            assertEquals("Idempotency-Key already used with a different payload", error.message)
            verify(partnerWriteAuditRepository, never()).save(any())
        }

    private fun request(): ServerRequest {
        val headers =
            mock<ServerRequest.Headers> {
                on { firstHeader("Idempotency-Key") } doReturn IDEMPOTENCY_KEY
            }
        return mock {
            on { headers() } doReturn headers
            on { method() } doReturn HttpMethod.POST
            on { path() } doReturn "/api/public/exercises"
        }
    }

    private fun partnerPrincipal() =
        PartnerAppRequestPrincipal(
            userId = USER_ID,
            appId = APP_ID,
            grantId = GRANT_ID,
            scopes = setOf("exercises:write"),
            partnerPrincipal =
                PartnerPrincipal(
                    userId = USER_ID.toString(),
                    appId = APP_ID.toString(),
                    grantId = GRANT_ID.toString(),
                    grantedScopes = "exercises:write",
                ),
        )

    private fun pendingRecord(id: UUID) =
        IdempotencyRecord(
            id = id,
            grantId = GRANT_ID,
            requestMethod = HttpMethod.POST.name(),
            requestPath = "/api/public/exercises",
            idempotencyKey = IDEMPOTENCY_KEY,
            requestFingerprint = """{"name":"Bench Press"}""",
            responseStatus = -1,
            responseBody = "__pending__",
        )

    private data class ExampleRequest(val name: String)

    private data class ExampleResponse(val name: String)

    companion object {
        private val USER_ID: UUID = UUID.randomUUID()
        private val APP_ID: UUID = UUID.randomUUID()
        private val GRANT_ID: UUID = UUID.randomUUID()
        private const val IDEMPOTENCY_KEY = "idem-key"
    }
}
