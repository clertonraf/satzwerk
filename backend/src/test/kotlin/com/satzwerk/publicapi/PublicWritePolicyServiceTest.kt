package com.satzwerk.publicapi

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.satzwerk.common.ConflictException
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

class PublicWritePolicyServiceTest {
    @Test
    fun `execute claims an idempotency record with the request fingerprint before returning for a partner app`(): Unit =
        runBlocking {
            val requestCodec = PublicWriteRequestFingerprintCodec.body(ExampleRequest(name = "Bench Press"))
            val claimedRecord = pendingRecord(id = UUID.randomUUID())
            val idempotencyRecordRepository =
                mock<PublicWriteIdempotencyRecordRepository> {
                    onBlocking { claim(any()) } doReturn claimedRecord
                    onBlocking { save(any()) } doAnswer { invocation ->
                        invocation.arguments[0] as PublicWriteIdempotencyRecord
                    }
                }
            val publicWriteAuditRepository =
                mock<PublicWriteAuditRepository> {
                    onBlocking { save(any()) } doAnswer { invocation ->
                        invocation.arguments[0] as PublicWriteAuditEntry
                    }
                }
            val service = policyService(idempotencyRecordRepository, publicWriteAuditRepository)

            val response =
                executeCreatedResponse(service, partnerPrincipal(), requestCodec) {
                    ExampleResponse(name = "Bench Press")
                }

            assertEquals(HttpStatus.CREATED, response.statusCode())
            val savedRecord = argumentCaptor<PublicWriteIdempotencyRecord>()
            val savedAudit = argumentCaptor<PublicWriteAuditEntry>()
            verify(idempotencyRecordRepository).claim(expectedClaim(partnerPrincipal()))
            verify(idempotencyRecordRepository).save(savedRecord.capture())
            verify(publicWriteAuditRepository).save(savedAudit.capture())
            assertEquals(HttpStatus.CREATED.value(), savedRecord.lastValue.responseStatus)
            assertEquals("""{"name":"Bench Press"}""", savedRecord.lastValue.responseBody)
            assertEquals("""{"name":"Bench Press"}""", savedRecord.lastValue.requestFingerprint)
            assertEquals(PublicWritePrincipalType.PARTNER_APP, savedRecord.lastValue.principalType)
            assertEquals(GRANT_ID, savedRecord.lastValue.credentialId)
            assertEquals(APP_ID, savedRecord.lastValue.appId)
            assertEquals(GRANT_ID, savedRecord.lastValue.grantId)
            assertEquals(USER_ID, savedRecord.lastValue.userId)
            assertEquals(PublicWritePrincipalType.PARTNER_APP, savedAudit.firstValue.principalType)
            assertEquals(GRANT_ID, savedAudit.firstValue.credentialId)
            assertEquals(APP_ID, savedAudit.firstValue.appId)
            assertEquals(GRANT_ID, savedAudit.firstValue.grantId)
            assertEquals("exercises:write", savedAudit.firstValue.grantedScopes)
        }

    @Test
    fun `execute finalizes a claimed idempotency record before returning for a personal api token`(): Unit =
        runBlocking {
            val requestCodec = PublicWriteRequestFingerprintCodec.body(ExampleRequest(name = "Bench Press"))
            val claimedRecord =
                pendingRecord(
                    id = UUID.randomUUID(),
                    principalType = PublicWritePrincipalType.PERSONAL_API_TOKEN,
                    credentialId = TOKEN_ID,
                )
            val idempotencyRecordRepository =
                mock<PublicWriteIdempotencyRecordRepository> {
                    onBlocking { claim(any()) } doReturn claimedRecord
                    onBlocking { save(any()) } doAnswer { invocation ->
                        invocation.arguments[0] as PublicWriteIdempotencyRecord
                    }
                }
            val publicWriteAuditRepository =
                mock<PublicWriteAuditRepository> {
                    onBlocking { save(any()) } doAnswer { invocation ->
                        invocation.arguments[0] as PublicWriteAuditEntry
                    }
                }
            val service = policyService(idempotencyRecordRepository, publicWriteAuditRepository)

            val response =
                executeCreatedResponse(service, personalApiTokenPrincipal(), requestCodec) {
                    ExampleResponse(name = "Bench Press")
                }

            assertEquals(HttpStatus.CREATED, response.statusCode())
            val savedRecord = argumentCaptor<PublicWriteIdempotencyRecord>()
            val savedAudit = argumentCaptor<PublicWriteAuditEntry>()
            verify(idempotencyRecordRepository).claim(expectedClaim(personalApiTokenPrincipal()))
            verify(idempotencyRecordRepository).save(savedRecord.capture())
            verify(publicWriteAuditRepository).save(savedAudit.capture())
            assertEquals(PublicWritePrincipalType.PERSONAL_API_TOKEN, savedRecord.lastValue.principalType)
            assertEquals(TOKEN_ID, savedRecord.lastValue.credentialId)
            assertEquals(null, savedRecord.lastValue.appId)
            assertEquals(null, savedRecord.lastValue.grantId)
            assertEquals(USER_ID, savedRecord.lastValue.userId)
            assertEquals(PublicWritePrincipalType.PERSONAL_API_TOKEN, savedAudit.firstValue.principalType)
            assertEquals(TOKEN_ID, savedAudit.firstValue.credentialId)
            assertEquals(null, savedAudit.firstValue.appId)
            assertEquals(null, savedAudit.firstValue.grantId)
            assertEquals("exercises:write", savedAudit.firstValue.grantedScopes)
        }

    @Test
    fun `execute replays a completed record when another request already claimed the key`(): Unit =
        runBlocking {
            val requestCodec = PublicWriteRequestFingerprintCodec.body(ExampleRequest(name = "Bench Press"))
            val completedRecord =
                pendingRecord(id = UUID.randomUUID()).copy(
                    responseStatus = HttpStatus.CREATED.value(),
                    responseBody = """{"name":"Bench Press"}""",
                )
            val idempotencyRecordRepository =
                mock<PublicWriteIdempotencyRecordRepository> {
                    onBlocking { claim(any()) } doReturn null
                    onBlocking {
                        findByPrincipalTypeAndCredentialIdAndRequestMethodAndRequestPathAndIdempotencyKey(
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                        )
                    } doReturn completedRecord
                }
            val publicWriteAuditRepository =
                mock<PublicWriteAuditRepository> {
                    onBlocking { save(any()) } doAnswer { invocation ->
                        invocation.arguments[0] as PublicWriteAuditEntry
                    }
                }
            val service =
                PublicWritePolicyService(
                    idempotencyRecordRepository = idempotencyRecordRepository,
                    publicWriteAuditService = PublicWriteAuditService(publicWriteAuditRepository),
                    objectMapper = jacksonObjectMapper(),
                )

            var executed = false
            val response =
                service.execute(
                    partnerPrincipal(),
                    request(),
                    HttpStatus.CREATED,
                    requestCodec,
                ) {
                    executed = true
                    ExampleResponse(name = "Should not run")
                }

            assertEquals(HttpStatus.CREATED, response.statusCode())
            assertFalse(executed)
            verify(idempotencyRecordRepository, never()).save(any())
        }

    @Test
    fun `execute replays a completed record for a personal api token`(): Unit =
        runBlocking {
            val requestCodec = PublicWriteRequestFingerprintCodec.body(ExampleRequest(name = "Bench Press"))
            val completedRecord =
                pendingRecord(
                    id = UUID.randomUUID(),
                    principalType = PublicWritePrincipalType.PERSONAL_API_TOKEN,
                    credentialId = TOKEN_ID,
                ).copy(
                    responseStatus = HttpStatus.CREATED.value(),
                    responseBody = """{"name":"Bench Press"}""",
                )
            val idempotencyRecordRepository =
                mock<PublicWriteIdempotencyRecordRepository> {
                    onBlocking { claim(any()) } doReturn null
                    onBlocking {
                        findByPrincipalTypeAndCredentialIdAndRequestMethodAndRequestPathAndIdempotencyKey(
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                        )
                    } doReturn completedRecord
                }
            val publicWriteAuditRepository =
                mock<PublicWriteAuditRepository> {
                    onBlocking { save(any()) } doAnswer { invocation ->
                        invocation.arguments[0] as PublicWriteAuditEntry
                    }
                }
            val service =
                PublicWritePolicyService(
                    idempotencyRecordRepository = idempotencyRecordRepository,
                    publicWriteAuditService = PublicWriteAuditService(publicWriteAuditRepository),
                    objectMapper = jacksonObjectMapper(),
                )

            var executed = false
            val response =
                service.execute(
                    personalApiTokenPrincipal(),
                    request(),
                    HttpStatus.CREATED,
                    requestCodec,
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
                mock<PublicWriteIdempotencyRecordRepository> {
                    onBlocking { claim(any()) } doReturn null
                    onBlocking {
                        findByPrincipalTypeAndCredentialIdAndRequestMethodAndRequestPathAndIdempotencyKey(
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                        )
                    } doReturn completedRecord
                }
            val publicWriteAuditRepository = mock<PublicWriteAuditRepository>()
            val service =
                PublicWritePolicyService(
                    idempotencyRecordRepository = idempotencyRecordRepository,
                    publicWriteAuditService = PublicWriteAuditService(publicWriteAuditRepository),
                    objectMapper = jacksonObjectMapper(),
                )

            val error =
                org.junit.jupiter.api.assertThrows<ConflictException> {
                    runBlocking {
                        service.execute(
                            partnerPrincipal(),
                            request(),
                            HttpStatus.CREATED,
                            PublicWriteRequestFingerprintCodec.body(ExampleRequest(name = "Changed Bench")),
                        ) {
                            ExampleResponse(name = "Should not run")
                        }
                    }
                }

            assertEquals("Idempotency-Key already used with a different payload", error.message)
            verify(publicWriteAuditRepository, never()).save(any())
        }

    @Test
    fun `execute rejects replay when a completed legacy record has a sentinel fingerprint`(): Unit =
        runBlocking {
            val completedRecord =
                pendingRecord(
                    id = UUID.randomUUID(),
                    requestFingerprint = "__legacy_no_fingerprint__",
                ).copy(
                    responseStatus = HttpStatus.CREATED.value(),
                    responseBody = """{"name":"Bench Press"}""",
                )
            val idempotencyRecordRepository =
                mock<PublicWriteIdempotencyRecordRepository> {
                    onBlocking { claim(any()) } doReturn null
                    onBlocking {
                        findByPrincipalTypeAndCredentialIdAndRequestMethodAndRequestPathAndIdempotencyKey(
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                        )
                    } doReturn completedRecord
                }
            val publicWriteAuditRepository = mock<PublicWriteAuditRepository>()
            val service =
                PublicWritePolicyService(
                    idempotencyRecordRepository = idempotencyRecordRepository,
                    publicWriteAuditService = PublicWriteAuditService(publicWriteAuditRepository),
                    objectMapper = jacksonObjectMapper(),
                )

            val error =
                org.junit.jupiter.api.assertThrows<ConflictException> {
                    runBlocking {
                        service.execute(
                            partnerPrincipal(),
                            request(),
                            HttpStatus.CREATED,
                            PublicWriteRequestFingerprintCodec.body(ExampleRequest(name = "Changed Bench")),
                        ) {
                            ExampleResponse(name = "Should not run")
                        }
                    }
                }

            assertEquals("Idempotency-Key already used with a different payload", error.message)
            verify(publicWriteAuditRepository, never()).save(any())
        }

    @Test
    fun `execute replays a completed record for a stateless command`(): Unit =
        runBlocking {
            val completedRecord =
                pendingRecord(
                    id = UUID.randomUUID(),
                    requestPath = "/api/public/plans/$PLAN_ID/activate",
                    requestFingerprint = """{"command":"activate-workout-plan"}""",
                ).copy(
                    responseStatus = HttpStatus.OK.value(),
                    responseBody = """{"id":"$PLAN_ID","isActive":true}""",
                )
            val idempotencyRecordRepository =
                mock<PublicWriteIdempotencyRecordRepository> {
                    onBlocking { claim(any()) } doReturn null
                    onBlocking {
                        findByPrincipalTypeAndCredentialIdAndRequestMethodAndRequestPathAndIdempotencyKey(
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                        )
                    } doReturn completedRecord
                }
            val publicWriteAuditRepository =
                mock<PublicWriteAuditRepository> {
                    onBlocking { save(any()) } doAnswer { invocation ->
                        invocation.arguments[0] as PublicWriteAuditEntry
                    }
                }
            val service =
                PublicWritePolicyService(
                    idempotencyRecordRepository = idempotencyRecordRepository,
                    publicWriteAuditService = PublicWriteAuditService(publicWriteAuditRepository),
                    objectMapper = jacksonObjectMapper(),
                )

            var executed = false
            val response =
                service.execute(
                    partnerPrincipal(),
                    request(path = "/api/public/plans/$PLAN_ID/activate"),
                    HttpStatus.OK,
                    PublicWriteRequestFingerprintCodec.stateless("activate-workout-plan"),
                ) {
                    executed = true
                    ExampleResponse(name = "Should not run")
                }

            assertEquals(HttpStatus.OK, response.statusCode())
            assertFalse(executed)
            verify(idempotencyRecordRepository, never()).save(any())
        }

    @Test
    fun `execute rejects a different stateless command for an existing idempotency key`(): Unit =
        runBlocking {
            val completedRecord =
                pendingRecord(
                    id = UUID.randomUUID(),
                    requestPath = "/api/public/plans/$PLAN_ID/activate",
                    requestFingerprint = """{"command":"activate-workout-plan"}""",
                ).copy(
                    responseStatus = HttpStatus.OK.value(),
                    responseBody = """{"id":"$PLAN_ID","isActive":true}""",
                )
            val idempotencyRecordRepository =
                mock<PublicWriteIdempotencyRecordRepository> {
                    onBlocking { claim(any()) } doReturn null
                    onBlocking {
                        findByPrincipalTypeAndCredentialIdAndRequestMethodAndRequestPathAndIdempotencyKey(
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                        )
                    } doReturn completedRecord
                }
            val publicWriteAuditRepository = mock<PublicWriteAuditRepository>()
            val service =
                PublicWritePolicyService(
                    idempotencyRecordRepository = idempotencyRecordRepository,
                    publicWriteAuditService = PublicWriteAuditService(publicWriteAuditRepository),
                    objectMapper = jacksonObjectMapper(),
                )

            val error =
                org.junit.jupiter.api.assertThrows<ConflictException> {
                    runBlocking {
                        service.execute(
                            partnerPrincipal(),
                            request(path = "/api/public/plans/$PLAN_ID/activate"),
                            HttpStatus.OK,
                            PublicWriteRequestFingerprintCodec.stateless("complete-workout-plan"),
                        ) {
                            ExampleResponse(name = "Should not run")
                        }
                    }
                }

            assertEquals("Idempotency-Key already used with a different payload", error.message)
            verify(publicWriteAuditRepository, never()).save(any())
        }

    @Test
    fun `execute audits failed writes with the same request context and cleans up the claimed record`(): Unit =
        runBlocking {
            val requestCodec = PublicWriteRequestFingerprintCodec.body(ExampleRequest(name = "Bench Press"))
            val claimedRecord = pendingRecord(id = UUID.randomUUID())
            val idempotencyRecordRepository =
                mock<PublicWriteIdempotencyRecordRepository> {
                    onBlocking { claim(any()) } doReturn claimedRecord
                }
            val publicWriteAuditRepository =
                mock<PublicWriteAuditRepository> {
                    onBlocking { save(any()) } doAnswer { invocation ->
                        invocation.arguments[0] as PublicWriteAuditEntry
                    }
                }
            val service =
                PublicWritePolicyService(
                    idempotencyRecordRepository = idempotencyRecordRepository,
                    publicWriteAuditService = PublicWriteAuditService(publicWriteAuditRepository),
                    objectMapper = jacksonObjectMapper(),
                )

            val error =
                org.junit.jupiter.api.assertThrows<ConflictException> {
                    runBlocking {
                        service.execute(
                            partnerPrincipal(),
                            request(),
                            HttpStatus.CREATED,
                            requestCodec,
                        ) {
                            throw ConflictException("Duplicate public write")
                        }
                    }
                }

            assertEquals("Duplicate public write", error.message)
            val savedAudit = argumentCaptor<PublicWriteAuditEntry>()
            verify(publicWriteAuditRepository).save(savedAudit.capture())
            verify(idempotencyRecordRepository).deleteById(claimedRecord.id!!)
            verify(idempotencyRecordRepository, never()).save(any())
            assertEquals(PublicWritePrincipalType.PARTNER_APP, savedAudit.firstValue.principalType)
            assertEquals(GRANT_ID, savedAudit.firstValue.credentialId)
            assertEquals(APP_ID, savedAudit.firstValue.appId)
            assertEquals(GRANT_ID, savedAudit.firstValue.grantId)
            assertEquals(USER_ID, savedAudit.firstValue.userId)
            assertEquals("exercises:write", savedAudit.firstValue.grantedScopes)
            assertEquals(HttpMethod.POST.name(), savedAudit.firstValue.requestMethod)
            assertEquals("/api/public/exercises", savedAudit.firstValue.requestPath)
            assertEquals(IDEMPOTENCY_KEY, savedAudit.firstValue.idempotencyKey)
            assertEquals(HttpStatus.CONFLICT.value(), savedAudit.firstValue.responseStatus)
        }

    private fun request(path: String = "/api/public/exercises"): ServerRequest {
        val headers =
            mock<ServerRequest.Headers> {
                on { firstHeader("Idempotency-Key") } doReturn IDEMPOTENCY_KEY
            }
        return mock {
            on { headers() } doReturn headers
            on { method() } doReturn HttpMethod.POST
            on { path() } doReturn path
        }
    }

    private fun partnerPrincipal() =
        PublicWritePrincipal(
            principalType = PublicWritePrincipalType.PARTNER_APP,
            userId = USER_ID,
            credentialId = GRANT_ID,
            scopes = setOf("exercises:write"),
            appId = APP_ID,
            grantId = GRANT_ID,
        )

    private fun personalApiTokenPrincipal() =
        PublicWritePrincipal(
            principalType = PublicWritePrincipalType.PERSONAL_API_TOKEN,
            userId = USER_ID,
            credentialId = TOKEN_ID,
            scopes = setOf("exercises:write"),
        )

    private fun policyService(
        idempotencyRecordRepository: PublicWriteIdempotencyRecordRepository,
        publicWriteAuditRepository: PublicWriteAuditRepository,
    ) = PublicWritePolicyService(
        idempotencyRecordRepository = idempotencyRecordRepository,
        publicWriteAuditService = PublicWriteAuditService(publicWriteAuditRepository),
        objectMapper = jacksonObjectMapper(),
    )

    private suspend fun executeCreatedResponse(
        service: PublicWritePolicyService,
        principal: PublicWritePrincipal,
        requestCodec: PublicWriteRequestFingerprintCodec,
        block: suspend () -> ExampleResponse,
    ) = service.execute(principal, request(), HttpStatus.CREATED, requestCodec) { block() }

    private fun expectedClaim(principal: PublicWritePrincipal) =
        PublicWriteIdempotencyClaim(
            principalType = principal.principalType,
            credentialId = principal.credentialId,
            appId = principal.appId,
            grantId = principal.grantId,
            userId = principal.userId,
            requestMethod = HttpMethod.POST.name(),
            requestPath = "/api/public/exercises",
            idempotencyKey = IDEMPOTENCY_KEY,
            requestFingerprint = """{"name":"Bench Press"}""",
        )

    private fun pendingRecord(
        id: UUID,
        principalType: PublicWritePrincipalType = PublicWritePrincipalType.PARTNER_APP,
        credentialId: UUID = GRANT_ID,
        requestPath: String = "/api/public/exercises",
        requestFingerprint: String = """{"name":"Bench Press"}""",
    ) = PublicWriteIdempotencyRecord(
        id = id,
        principalType = principalType,
        credentialId = credentialId,
        appId = if (principalType == PublicWritePrincipalType.PARTNER_APP) APP_ID else null,
        grantId = if (principalType == PublicWritePrincipalType.PARTNER_APP) GRANT_ID else null,
        userId = USER_ID,
        requestMethod = HttpMethod.POST.name(),
        requestPath = requestPath,
        idempotencyKey = IDEMPOTENCY_KEY,
        requestFingerprint = requestFingerprint,
        responseStatus = -1,
        responseBody = "__pending__",
    )

    private data class ExampleRequest(val name: String)

    private data class ExampleResponse(val name: String)

    companion object {
        private val USER_ID: UUID = UUID.randomUUID()
        private val TOKEN_ID: UUID = UUID.randomUUID()
        private val APP_ID: UUID = UUID.randomUUID()
        private val GRANT_ID: UUID = UUID.randomUUID()
        private val PLAN_ID: UUID = UUID.randomUUID()
        private const val IDEMPOTENCY_KEY = "idem-key"
    }
}
