package com.satzwerk.publicapi

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.satzwerk.common.ConflictException
import com.satzwerk.common.UnauthorizedException
import com.satzwerk.partners.AppGrant
import com.satzwerk.partners.PartnerAppService
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.web.reactive.function.server.ServerRequest
import reactor.core.publisher.Mono
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
            val partnerAppService =
                mock<PartnerAppService> {
                    onBlocking { resolveActiveGrant(APP_TOKEN) } doReturn activeGrant()
                }
            val service =
                PartnerWritePolicyService(
                    idempotencyRecordRepository = idempotencyRecordRepository,
                    partnerWriteAuditRepository = partnerWriteAuditRepository,
                    objectMapper = jacksonObjectMapper(),
                    partnerAppService = partnerAppService,
                )

            val response =
                service.execute(request(), HttpStatus.CREATED, ExampleRequest(name = "Bench Press")) {
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
            val partnerAppService =
                mock<PartnerAppService> {
                    onBlocking { resolveActiveGrant(APP_TOKEN) } doReturn activeGrant()
                }
            val service =
                PartnerWritePolicyService(
                    idempotencyRecordRepository = idempotencyRecordRepository,
                    partnerWriteAuditRepository = partnerWriteAuditRepository,
                    objectMapper = jacksonObjectMapper(),
                    partnerAppService = partnerAppService,
                )

            var executed = false
            val response =
                service.execute(request(), HttpStatus.CREATED, ExampleRequest(name = "Bench Press")) {
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
            val partnerAppService =
                mock<PartnerAppService> {
                    onBlocking { resolveActiveGrant(APP_TOKEN) } doReturn activeGrant()
                }
            val service =
                PartnerWritePolicyService(
                    idempotencyRecordRepository = idempotencyRecordRepository,
                    partnerWriteAuditRepository = partnerWriteAuditRepository,
                    objectMapper = jacksonObjectMapper(),
                    partnerAppService = partnerAppService,
                )

            val error =
                org.junit.jupiter.api.assertThrows<ConflictException> {
                    runBlocking {
                        service.execute(request(), HttpStatus.CREATED, ExampleRequest(name = "Changed Bench")) {
                            ExampleResponse(name = "Should not run")
                        }
                    }
                }

            assertEquals("Idempotency-Key already used with a different payload", error.message)
            verify(partnerWriteAuditRepository, never()).save(any())
        }

    @Test
    fun `execute rejects an app token whose resolved grant belongs to another user`(): Unit =
        runBlocking {
            val claimedRecord = pendingRecord(id = UUID.randomUUID())
            val idempotencyRecordRepository =
                mock<IdempotencyRecordRepository> {
                    onBlocking { claim(any(), any(), any(), any(), any()) } doReturn claimedRecord
                }
            val partnerWriteAuditRepository = mock<PartnerWriteAuditRepository>()
            val partnerAppService =
                mock<PartnerAppService> {
                    onBlocking { resolveActiveGrant(APP_TOKEN) } doReturn activeGrant(userId = UUID.randomUUID())
                }
            val service =
                PartnerWritePolicyService(
                    idempotencyRecordRepository = idempotencyRecordRepository,
                    partnerWriteAuditRepository = partnerWriteAuditRepository,
                    objectMapper = jacksonObjectMapper(),
                    partnerAppService = partnerAppService,
                )

            org.junit.jupiter.api.assertThrows<UnauthorizedException> {
                runBlocking {
                    service.execute(request(), HttpStatus.CREATED, ExampleRequest(name = "Bench Press")) {
                        ExampleResponse(name = "Should not run")
                    }
                }
            }

            verify(idempotencyRecordRepository, never()).save(any())
            verify(partnerWriteAuditRepository, never()).save(any())
        }

    private fun request(): ServerRequest {
        val headers =
            mock<ServerRequest.Headers> {
                on { firstHeader("Idempotency-Key") } doReturn IDEMPOTENCY_KEY
                on { firstHeader("X-App-Token") } doReturn APP_TOKEN
            }
        val principal =
            UsernamePasswordAuthenticationToken(
                USER_ID.toString(),
                PartnerPrincipal(
                    userId = USER_ID.toString(),
                    appId = APP_ID.toString(),
                    grantId = GRANT_ID.toString(),
                    grantedScopes = "exercises:write",
                ),
                emptyList(),
            )

        return mock {
            on { headers() } doReturn headers
            on { principal() } doReturn Mono.just(principal)
            on { method() } doReturn HttpMethod.POST
            on { path() } doReturn "/api/public/exercises"
        }
    }

    private fun activeGrant(userId: UUID = USER_ID) =
        AppGrant(
            id = GRANT_ID,
            appId = APP_ID,
            userId = userId,
            grantedScopes = "exercises:write",
            accessTokenHash = "hashed-token",
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
        private const val APP_TOKEN = "app-token"
        private const val IDEMPOTENCY_KEY = "idem-key"
    }
}
