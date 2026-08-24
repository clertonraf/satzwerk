# Public Write Policy Generalization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let public write routes accept both personal API tokens and partner-app grants through one shared policy path with shared idempotency and audit behavior.

**Architecture:** Carry persisted PAT identity through auth resolution, then convert both PAT and partner-app requests into one shared public-write principal. Generalize the current partner-only public-write policy, audit models, and idempotency storage to neutral public-write names, and rewire all public write routers to that shared path. Prove the change with focused unit tests, partner regression coverage, and new PAT-backed integration tests for one body-based route and one command-style route.

**Tech Stack:** Kotlin, Spring WebFlux, Spring Security, R2DBC, Flyway, JUnit 5, Testcontainers

---

## Files

| Action | Path | Responsibility |
|--------|------|----------------|
| Modify | `backend/src/main/kotlin/com/satzwerk/config/JwtAuthenticationWebFilter.kt` | Carry the resolved `PersonalApiToken` object into the auth context |
| Modify | `backend/src/main/kotlin/com/satzwerk/common/RequestContext.kt` | Add PAT credential identity to request principals |
| Modify | `backend/src/test/kotlin/com/satzwerk/common/RequestContextTest.kt` | Prove PAT token-id resolution and existing partner/JWT behavior |
| Rename | `backend/src/main/kotlin/com/satzwerk/publicapi/PartnerWritePrincipalValidationService.kt` → `backend/src/main/kotlin/com/satzwerk/publicapi/PublicWritePrincipalValidationService.kt` | Replace partner-only principal validation with shared public-write principal resolution |
| Rename | `backend/src/test/kotlin/com/satzwerk/publicapi/PartnerWritePrincipalValidationServiceTest.kt` → `backend/src/test/kotlin/com/satzwerk/publicapi/PublicWritePrincipalValidationServiceTest.kt` | Cover PAT and partner public-write principal resolution |
| Create | `backend/src/main/kotlin/com/satzwerk/publicapi/PublicWritePrincipal.kt` | Shared public-write principal model and principal-type enum |
| Rename | `backend/src/main/kotlin/com/satzwerk/publicapi/PartnerWritePolicyService.kt` → `backend/src/main/kotlin/com/satzwerk/publicapi/PublicWritePolicyService.kt` | Shared public-write idempotency/audit service, repositories, and fingerprint codec |
| Rename | `backend/src/test/kotlin/com/satzwerk/publicapi/PartnerWritePolicyServiceTest.kt` → `backend/src/test/kotlin/com/satzwerk/publicapi/PublicWritePolicyServiceTest.kt` | Unit tests for shared idempotency and audit behavior across PAT and partner principals |
| Create | `backend/src/main/resources/db/migration/V21__generalize_public_write_policy.sql` | Rename partner-only tables, widen schema, backfill partner rows |
| Modify | `backend/src/main/kotlin/com/satzwerk/workouts/PublicExerciseRouter.kt` | Swap to shared public-write validation/policy types |
| Modify | `backend/src/main/kotlin/com/satzwerk/workouts/PublicWorkoutPlanRouter.kt` | Swap to shared public-write validation/policy types |
| Modify | `backend/src/main/kotlin/com/satzwerk/sessions/PublicSessionMutationRouter.kt` | Swap to shared public-write validation/policy types |
| Modify | `backend/src/main/kotlin/com/satzwerk/measurements/PublicMeasurementRouter.kt` | Swap to shared public-write validation/policy types |
| Modify | `backend/src/main/kotlin/com/satzwerk/medications/PublicMedicationRouter.kt` | Swap to shared public-write validation/policy types |
| Modify | `backend/src/test/kotlin/com/satzwerk/workouts/PublicExerciseIntegrationTest.kt` | Keep partner regression coverage, add PAT body-write coverage |
| Modify | `backend/src/test/kotlin/com/satzwerk/workouts/PublicWorkoutPlanIntegrationTest.kt` | Keep partner regression coverage, add PAT command-style coverage |
| Modify | `backend/src/test/kotlin/com/satzwerk/sessions/PublicSessionWriteIntegrationTest.kt` | Update imports/repository assertions after policy generalization |
| Modify | `backend/src/test/kotlin/com/satzwerk/measurements/PublicMeasurementIntegrationTest.kt` | Update imports/repository assertions after policy generalization |
| Modify | `backend/src/test/kotlin/com/satzwerk/medications/PublicMedicationIntegrationTest.kt` | Update imports/repository assertions after policy generalization |

---

## Task 1: Carry persisted PAT identity into `RequestContext`

**Files:**
- Modify: `backend/src/main/kotlin/com/satzwerk/config/JwtAuthenticationWebFilter.kt`
- Modify: `backend/src/main/kotlin/com/satzwerk/common/RequestContext.kt`
- Modify: `backend/src/test/kotlin/com/satzwerk/common/RequestContextTest.kt`

### Steps

- [ ] **Step 1: Add the failing RequestContext test for PAT token identity**

In `backend/src/test/kotlin/com/satzwerk/common/RequestContextTest.kt`, replace the current PAT-scope-only assertion with a PAT object-backed assertion:

```kotlin
@Test
fun `principal resolves personal api token authentication token id and scopes`() {
    val userId = UUID.randomUUID()
    val tokenId = UUID.randomUUID()
    val pat =
        PersonalApiToken(
            id = tokenId,
            userId = userId,
            name = "Automation",
            tokenHash = "hash",
            scopesRaw = "analytics:read,exercises:write",
        )
    val authentication =
        UsernamePasswordAuthenticationToken(
            userId.toString(),
            pat,
            listOf(
                SimpleGrantedAuthority("analytics:read"),
                SimpleGrantedAuthority("exercises:write"),
            ),
        )
    `when`(request.principal()).thenReturn(Mono.just(authentication))

    val principal = runBlocking { ctx.principal() } as PersonalApiTokenRequestPrincipal

    assertEquals(RequestPrincipalKind.PERSONAL_API_TOKEN, principal.kind)
    assertEquals(userId, principal.userId)
    assertEquals(tokenId, principal.tokenId)
    assertEquals(setOf("analytics:read", "exercises:write"), principal.scopes)
}
```

- [ ] **Step 2: Run the focused test and confirm it fails**

```bash
cd backend && ./gradlew test --tests "com.satzwerk.common.RequestContextTest" --no-daemon
```

Expected: FAIL because `PersonalApiTokenRequestPrincipal` does not yet expose `tokenId`.

- [ ] **Step 3: Update the auth filter and request principal model**

In `backend/src/main/kotlin/com/satzwerk/config/JwtAuthenticationWebFilter.kt`, change the PAT auth branch from bearer-token credentials to the resolved token object:

```kotlin
bearerToken.startsWith(PAT_PREFIX) ->
    mono { personalApiTokenService.resolve(bearerToken) }
        .flatMap { pat -> chainMono.withPatAuth(pat) }

private fun Mono<Void>.withPatAuth(
    pat: PersonalApiToken?,
): Mono<Void> {
    pat ?: return this
    val authorities = pat.scopes().map { SimpleGrantedAuthority(it) }
    val auth = UsernamePasswordAuthenticationToken(pat.userId.toString(), pat, authorities)
    return contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
}
```

In `backend/src/main/kotlin/com/satzwerk/common/RequestContext.kt`, extend the PAT principal shape and resolve PAT credentials explicitly:

```kotlin
data class PersonalApiTokenRequestPrincipal(
    override val userId: UUID,
    val tokenId: UUID,
    override val scopes: Set<String>,
) : RequestPrincipal {
    override val kind: RequestPrincipalKind = RequestPrincipalKind.PERSONAL_API_TOKEN
}

private fun resolveAuthenticationPrincipal(authentication: UsernamePasswordAuthenticationToken): RequestPrincipal {
    val userId = parseUuid(authentication.name)
    val scopes =
        authentication.authorities
            .map { it.authority }
            .filter { it != AUTHORITY_JWT_SESSION }
            .toSet()
    val partnerPrincipal = authentication.credentials as? PartnerPrincipal
    val personalApiToken = authentication.credentials as? PersonalApiToken

    return when {
        partnerPrincipal != null ->
            PartnerAppRequestPrincipal(
                userId = userId,
                appId = parseUuid(partnerPrincipal.appId),
                grantId = parseUuid(partnerPrincipal.grantId),
                scopes = scopes,
                partnerPrincipal = partnerPrincipal,
            )

        personalApiToken != null ->
            PersonalApiTokenRequestPrincipal(
                userId = userId,
                tokenId = requireNotNull(personalApiToken.id),
                scopes = scopes,
            )

        authentication.authorities.any { it.authority == AUTHORITY_JWT_SESSION } ->
            JwtSessionRequestPrincipal(userId)

        else -> throw UnauthorizedException()
    }
}
```

- [ ] **Step 4: Run the focused test again**

```bash
cd backend && ./gradlew test --tests "com.satzwerk.common.RequestContextTest" --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add \
  backend/src/main/kotlin/com/satzwerk/config/JwtAuthenticationWebFilter.kt \
  backend/src/main/kotlin/com/satzwerk/common/RequestContext.kt \
  backend/src/test/kotlin/com/satzwerk/common/RequestContextTest.kt
git commit -m "feat(auth): carry PAT identity into request principals

Use the resolved PersonalApiToken as PAT authentication credentials so
RequestContext can expose the persisted token ID alongside scopes and user ID.

Co-authored-by: Copilot App <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 2: Introduce a shared public-write principal resolver

**Files:**
- Create: `backend/src/main/kotlin/com/satzwerk/publicapi/PublicWritePrincipal.kt`
- Create: `backend/src/main/kotlin/com/satzwerk/publicapi/PublicWritePrincipalValidationService.kt`
- Modify: `backend/src/main/kotlin/com/satzwerk/publicapi/PartnerWritePrincipalValidationService.kt`
- Rename: `backend/src/test/kotlin/com/satzwerk/publicapi/PartnerWritePrincipalValidationServiceTest.kt` → `backend/src/test/kotlin/com/satzwerk/publicapi/PublicWritePrincipalValidationServiceTest.kt`

### Steps

- [ ] **Step 1: Rename the validation test and add PAT coverage first**

Rename the test file to `backend/src/test/kotlin/com/satzwerk/publicapi/PublicWritePrincipalValidationServiceTest.kt`, then update it to cover PAT success, partner success, and JWT rejection:

```kotlin
class PublicWritePrincipalValidationServiceTest {
    @Test
    fun `requireValidPrincipal returns PAT principal without partner revalidation`(): Unit =
        runBlocking {
            val tokenId = UUID.randomUUID()
            val request = requestForPat(tokenId)
            val validatedService = PublicWritePrincipalValidationService(mock())

            val actual = validatedService.requireValidPrincipal(RequestContext(request))

            assertEquals(
                PublicWritePrincipal(
                    principalType = PublicWritePrincipalType.PERSONAL_API_TOKEN,
                    userId = USER_ID,
                    credentialId = tokenId,
                    scopes = setOf("exercises:write"),
                ),
                actual,
            )
        }

    @Test
    fun `requireValidPrincipal revalidates matching partner grant`(): Unit = runBlocking {
        val request = requestForPartner()
        val activeGrant = AppGrant(
            id = GRANT_ID,
            appId = APP_ID,
            userId = USER_ID,
            grantedScopes = "exercises:write",
            accessTokenHash = "hash",
        )
        val validatedService =
            PublicWritePrincipalValidationService(
                mock { onBlocking { resolveActiveGrant(APP_TOKEN) } doReturn activeGrant },
            )

        val actual = validatedService.requireValidPrincipal(RequestContext(request))

        assertEquals(PublicWritePrincipalType.PARTNER_APP, actual.principalType)
        assertEquals(GRANT_ID, actual.credentialId)
        assertEquals(APP_ID, actual.appId)
        assertEquals(GRANT_ID, actual.grantId)
    }
}
```

- [ ] **Step 2: Run the focused validation test and confirm it fails**

```bash
cd backend && ./gradlew test --tests "com.satzwerk.publicapi.PublicWritePrincipalValidationServiceTest" --no-daemon
```

Expected: FAIL because `PublicWritePrincipal`, `PublicWritePrincipalType`, and `PublicWritePrincipalValidationService` do not exist yet.

- [ ] **Step 3: Create the shared public-write principal and resolver**

Create `backend/src/main/kotlin/com/satzwerk/publicapi/PublicWritePrincipal.kt`:

```kotlin
package com.satzwerk.publicapi

import java.util.UUID

enum class PublicWritePrincipalType {
    PERSONAL_API_TOKEN,
    PARTNER_APP,
}

data class PublicWritePrincipal(
    val principalType: PublicWritePrincipalType,
    val userId: UUID,
    val credentialId: UUID,
    val scopes: Set<String>,
    val appId: UUID? = null,
    val grantId: UUID? = null,
)
```

Create the shared resolver in `backend/src/main/kotlin/com/satzwerk/publicapi/PublicWritePrincipalValidationService.kt`:

```kotlin
@Service
class PublicWritePrincipalValidationService(
    private val partnerAppService: PartnerAppService,
) {
    suspend fun requireValidPrincipal(ctx: RequestContext): PublicWritePrincipal =
        when (val principal = ctx.principal()) {
            is PersonalApiTokenRequestPrincipal ->
                PublicWritePrincipal(
                    principalType = PublicWritePrincipalType.PERSONAL_API_TOKEN,
                    userId = principal.userId,
                    credentialId = principal.tokenId,
                    scopes = principal.scopes,
                )

            is PartnerAppRequestPrincipal ->
                requireValidPartnerPrincipal(ctx, principal)

            else -> throw UnauthorizedException()
        }

    private suspend fun requireValidPartnerPrincipal(
        ctx: RequestContext,
        principal: PartnerAppRequestPrincipal,
    ): PublicWritePrincipal {
        val appToken = ctx.header(APP_TOKEN_HEADER)?.trim().orEmpty()
        if (appToken.isBlank()) throw UnauthorizedException()

        val activeGrant = partnerAppService.resolveActiveGrant(appToken) ?: throw UnauthorizedException()
        val activeGrantId = activeGrant.id ?: throw UnauthorizedException()
        if (
            activeGrantId != principal.grantId ||
            activeGrant.appId != principal.appId ||
            activeGrant.userId != principal.userId
        ) {
            throw UnauthorizedException()
        }

        return PublicWritePrincipal(
            principalType = PublicWritePrincipalType.PARTNER_APP,
            userId = principal.userId,
            credentialId = principal.grantId,
            scopes = principal.scopes,
            appId = principal.appId,
            grantId = principal.grantId,
        )
    }
}
```

Then keep `backend/src/main/kotlin/com/satzwerk/publicapi/PartnerWritePrincipalValidationService.kt` as a temporary compatibility shim until Task 4 rewires the routers. The shim should keep the old router contract alive without duplicating the grant-validation rules:

```kotlin
@Service
class PartnerWritePrincipalValidationService(
    private val publicWritePrincipalValidationService: PublicWritePrincipalValidationService,
) {
    suspend fun requireValidPrincipal(ctx: RequestContext): PartnerAppRequestPrincipal {
        val principal = publicWritePrincipalValidationService.requireValidPrincipal(ctx)
        if (principal.principalType != PublicWritePrincipalType.PARTNER_APP) {
            throw UnauthorizedException()
        }
        return ctx.requirePartnerAppPrincipal()
    }
}
```

Do **not** rewire routers in this task. Task 4 owns that change.

- [ ] **Step 4: Run the focused validation test again**

```bash
cd backend && ./gradlew test --tests "com.satzwerk.publicapi.PublicWritePrincipalValidationServiceTest" --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add \
  backend/src/main/kotlin/com/satzwerk/publicapi/PublicWritePrincipal.kt \
  backend/src/main/kotlin/com/satzwerk/publicapi/PartnerWritePrincipalValidationService.kt \
  backend/src/main/kotlin/com/satzwerk/publicapi/PublicWritePrincipalValidationService.kt \
  backend/src/test/kotlin/com/satzwerk/publicapi/PublicWritePrincipalValidationServiceTest.kt
git commit -m "refactor(publicapi): add shared public write principal resolution

Resolve PAT and partner-app requests into one PublicWritePrincipal so
public routes can share one write-policy entry point.

Co-authored-by: Copilot App <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 3: Generalize the public-write policy service and schema

**Files:**
- Create: `backend/src/main/kotlin/com/satzwerk/publicapi/PublicWritePolicyService.kt`
- Modify: `backend/src/main/kotlin/com/satzwerk/publicapi/PartnerWritePolicyService.kt`
- Rename: `backend/src/test/kotlin/com/satzwerk/publicapi/PartnerWritePolicyServiceTest.kt` → `backend/src/test/kotlin/com/satzwerk/publicapi/PublicWritePolicyServiceTest.kt`
- Create: `backend/src/main/resources/db/migration/V21__generalize_public_write_policy.sql`

### Steps

- [ ] **Step 1: Rename the policy test and add PAT-path expectations first**

Rename the test file to `backend/src/test/kotlin/com/satzwerk/publicapi/PublicWritePolicyServiceTest.kt`, then replace the partner-only helper with a shared principal helper:

```kotlin
private fun publicWritePrincipal(
    principalType: PublicWritePrincipalType = PublicWritePrincipalType.PARTNER_APP,
    credentialId: UUID = GRANT_ID,
    scopes: Set<String> = setOf("exercises:write"),
) = PublicWritePrincipal(
    principalType = principalType,
    userId = USER_ID,
    credentialId = credentialId,
    scopes = scopes,
    appId = if (principalType == PublicWritePrincipalType.PARTNER_APP) APP_ID else null,
    grantId = if (principalType == PublicWritePrincipalType.PARTNER_APP) GRANT_ID else null,
)
```

Add a PAT-specific assertion:

```kotlin
@Test
fun `execute stores PAT principal metadata in idempotency and audit rows`(): Unit = runBlocking {
    val tokenId = UUID.randomUUID()
    val requestCodec = PublicWriteRequestFingerprintCodec.body(ExampleRequest(name = "Bench Press"))
    val claimedRecord = pendingRecord(id = UUID.randomUUID())
    val idempotencyRecordRepository =
        mock<PublicWriteIdempotencyRecordRepository> {
            onBlocking { claim(any(), any(), any(), any(), any(), any(), any(), any(), any()) } doReturn claimedRecord
            onBlocking { save(any()) } doAnswer { invocation -> invocation.arguments[0] as PublicWriteIdempotencyRecord }
        }
    val auditRepository =
        mock<PublicWriteAuditRepository> {
            onBlocking { save(any()) } doAnswer { invocation -> invocation.arguments[0] as PublicWriteAuditEntry }
        }
    val service = PublicWritePolicyService(idempotencyRecordRepository, auditRepository, jacksonObjectMapper())

    service.execute(
        publicWritePrincipal(
            principalType = PublicWritePrincipalType.PERSONAL_API_TOKEN,
            credentialId = tokenId,
        ),
        request(),
        HttpStatus.CREATED,
        requestCodec,
    ) {
        ExampleResponse(name = "Bench Press")
    }

    val savedRecord = argumentCaptor<PublicWriteIdempotencyRecord>()
    val savedAudit = argumentCaptor<PublicWriteAuditEntry>()
    verify(idempotencyRecordRepository).save(savedRecord.capture())
    verify(auditRepository).save(savedAudit.capture())
    assertEquals(PublicWritePrincipalType.PERSONAL_API_TOKEN, savedRecord.firstValue.principalType)
    assertEquals(tokenId, savedRecord.firstValue.credentialId)
    assertEquals(null, savedAudit.firstValue.grantId)
}
```

- [ ] **Step 2: Run the focused policy test and confirm it fails**

```bash
cd backend && ./gradlew test --tests "com.satzwerk.publicapi.PublicWritePolicyServiceTest" --no-daemon
```

Expected: FAIL because the public-write policy types, repositories, and migration-backed schema do not exist yet.

- [ ] **Step 3: Add the shared policy service and the credential-aware schema**

Create `backend/src/main/kotlin/com/satzwerk/publicapi/PublicWritePolicyService.kt`, then make these structural changes:

```kotlin
private data class PublicWriteRequestMetadata(
    val principalType: PublicWritePrincipalType,
    val credentialId: UUID,
    val appId: UUID?,
    val grantId: UUID?,
    val userId: UUID,
    val grantedScopes: String,
    val requestMethod: String,
    val requestPath: String,
    val idempotencyKey: String,
    val requestFingerprint: String,
)

@Table("public_write_idempotency_records")
data class PublicWriteIdempotencyRecord(
    @Id val id: UUID? = null,
    @Column("principal_type") val principalType: PublicWritePrincipalType,
    @Column("credential_id") val credentialId: UUID,
    @Column("user_id") val userId: UUID,
    @Column("app_id") val appId: UUID? = null,
    @Column("grant_id") val grantId: UUID? = null,
    @Column("request_method") val requestMethod: String,
    @Column("request_path") val requestPath: String,
    @Column("idempotency_key") val idempotencyKey: String,
    @Column("request_fingerprint") val requestFingerprint: String,
    @Column("response_status") val responseStatus: Int,
    @Column("response_body") val responseBody: String,
    @Column("created_at") val createdAt: Instant = Instant.now(),
)
```

Update the idempotency claim query to key on principal type + credential identity:

```sql
ON CONFLICT (principal_type, credential_id, request_method, request_path, idempotency_key) DO NOTHING
```

And normalize scopes once inside the service:

```kotlin
private fun Set<String>.toGrantedScopes(): String = toList().sorted().joinToString(" ")
```

Create `backend/src/main/resources/db/migration/V21__generalize_public_write_policy.sql`:

```sql
ALTER TABLE idempotency_records RENAME TO public_write_idempotency_records;
ALTER TABLE partner_write_audit RENAME TO public_write_audit;

ALTER TABLE public_write_idempotency_records RENAME COLUMN grant_id TO credential_id;
ALTER TABLE public_write_idempotency_records
    ADD COLUMN principal_type TEXT,
    ADD COLUMN user_id UUID,
    ADD COLUMN app_id UUID REFERENCES partner_apps(id) ON DELETE CASCADE,
    ADD COLUMN grant_id UUID REFERENCES app_grants(id) ON DELETE CASCADE;

UPDATE public_write_idempotency_records records
SET principal_type = 'PARTNER_APP',
    user_id = grants.user_id,
    app_id = grants.app_id,
    grant_id = records.credential_id
FROM app_grants grants
WHERE grants.id = records.credential_id;

ALTER TABLE public_write_idempotency_records
    ALTER COLUMN principal_type SET NOT NULL,
    ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE public_write_audit RENAME COLUMN grant_id TO credential_id;
ALTER TABLE public_write_audit
    ADD COLUMN principal_type TEXT,
    ADD COLUMN grant_id UUID REFERENCES app_grants(id) ON DELETE CASCADE;

UPDATE public_write_audit
SET principal_type = 'PARTNER_APP',
    grant_id = credential_id;

ALTER TABLE public_write_audit
    ALTER COLUMN principal_type SET NOT NULL;

ALTER TABLE public_write_idempotency_records
    DROP CONSTRAINT idempotency_records_grant_id_request_method_request_path_idempotency_key_key;
ALTER TABLE public_write_idempotency_records
    ADD CONSTRAINT public_write_idempotency_records_principal_key_unique
    UNIQUE (principal_type, credential_id, request_method, request_path, idempotency_key);
```

When you write the real migration, keep the existing request fingerprint and granted-scope columns, recreate the indexes under the new table names, and do **not** leave the old partner-only unique/index names behind.

Then keep `backend/src/main/kotlin/com/satzwerk/publicapi/PartnerWritePolicyService.kt` as a temporary compatibility layer until Task 4 rewires routers and Task 5 rewires the remaining tests. The compatibility layer should avoid duplicating the new logic:

```kotlin
typealias PartnerWriteRequestFingerprintCodec = PublicWriteRequestFingerprintCodec
typealias IdempotencyRecord = PublicWriteIdempotencyRecord
typealias PartnerWriteAuditEntry = PublicWriteAuditEntry

@Service
class PartnerWritePolicyService(
    private val publicWritePolicyService: PublicWritePolicyService,
) {
    suspend fun <T : Any> execute(
        partnerPrincipal: PartnerAppRequestPrincipal,
        request: ServerRequest,
        successStatus: HttpStatus,
        requestFingerprintCodec: PartnerWriteRequestFingerprintCodec,
        block: suspend (UUID) -> T,
    ): ServerResponse =
        publicWritePolicyService.execute(
            PublicWritePrincipal(
                principalType = PublicWritePrincipalType.PARTNER_APP,
                userId = partnerPrincipal.userId,
                credentialId = partnerPrincipal.grantId,
                scopes = partnerPrincipal.scopes,
                appId = partnerPrincipal.appId,
                grantId = partnerPrincipal.grantId,
            ),
            request,
            successStatus,
            requestFingerprintCodec,
            block,
        )
}
```

Do **not** rewire routers in this task. Task 4 owns that change, and Task 5 owns the remaining integration-test import/assertion rewiring.

- [ ] **Step 4: Run the focused policy test and compile gate**

```bash
cd backend && ./gradlew test --tests "com.satzwerk.publicapi.PublicWritePolicyServiceTest" compileTestKotlin --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add \
  backend/src/main/kotlin/com/satzwerk/publicapi/PartnerWritePolicyService.kt \
  backend/src/main/kotlin/com/satzwerk/publicapi/PublicWritePolicyService.kt \
  backend/src/test/kotlin/com/satzwerk/publicapi/PublicWritePolicyServiceTest.kt \
  backend/src/main/resources/db/migration/V21__generalize_public_write_policy.sql
git commit -m "refactor(publicapi): generalize public write policy storage

Rename the partner-only public write policy and schema to neutral public-write
names, and key idempotency/audit data by principal type plus credential identity.

Co-authored-by: Copilot App <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 4: Rewire all public-write routers to the shared policy entry point

**Files:**
- Modify: `backend/src/main/kotlin/com/satzwerk/workouts/PublicExerciseRouter.kt`
- Modify: `backend/src/main/kotlin/com/satzwerk/workouts/PublicWorkoutPlanRouter.kt`
- Modify: `backend/src/main/kotlin/com/satzwerk/sessions/PublicSessionMutationRouter.kt`
- Modify: `backend/src/main/kotlin/com/satzwerk/measurements/PublicMeasurementRouter.kt`
- Modify: `backend/src/main/kotlin/com/satzwerk/medications/PublicMedicationRouter.kt`

### Steps

- [ ] **Step 1: Run a representative public-write integration test and confirm wiring is broken**

```bash
cd backend && ./gradlew test --tests "com.satzwerk.workouts.PublicExerciseIntegrationTest" --no-daemon
```

Expected: FAIL to compile or start because routers still inject the partner-only validation/policy types.

- [ ] **Step 2: Replace partner-only router dependencies with shared public-write types**

Apply the same change pattern to every public-write router. For example, in `backend/src/main/kotlin/com/satzwerk/workouts/PublicExerciseRouter.kt`:

```kotlin
import com.satzwerk.publicapi.PublicWritePolicyService
import com.satzwerk.publicapi.PublicWritePrincipalValidationService
import com.satzwerk.publicapi.PublicWriteRequestFingerprintCodec

@Configuration
class PublicExerciseRouter {
    @Bean
    fun publicExerciseRoutes(
        exerciseService: ExerciseService,
        publicWritePolicyService: PublicWritePolicyService,
        publicWritePrincipalValidationService: PublicWritePrincipalValidationService,
        validator: Validator,
    ) = coRouter {
        "/api/public/exercises".nest {
            POST("") { request ->
                handlePublicScope(request, PublicScope.EXERCISES_WRITE, extra = publicExerciseWriteErrors) { ctx ->
                    val publicWritePrincipal = publicWritePrincipalValidationService.requireValidPrincipal(ctx)
                    val body = ctx.body<CreateExerciseRequest>()
                    validateOrBadRequest(validator, body) {
                        publicWritePolicyService.execute(
                            publicWritePrincipal,
                            request,
                            HttpStatus.CREATED,
                            PublicWriteRequestFingerprintCodec.body(body),
                        ) { userId ->
                            exerciseService.create(userId, body)
                        }
                    }
                }
            }
        }
    }
}
```

Repeat the same rename/wiring pattern in:

- `PublicWorkoutPlanRouter.kt`
- `PublicSessionMutationRouter.kt`
- `PublicMeasurementRouter.kt`
- `PublicMedicationRouter.kt`

Keep the route-specific domain behavior unchanged, including the `WorkoutGroup not found` masking in `PublicSessionMutationRouter.kt` and the stateless `"activate-workout-plan"` fingerprint in `PublicWorkoutPlanRouter.kt`.

- [ ] **Step 3: Run representative partner regression tests**

```bash
cd backend && ./gradlew test \
  --tests "com.satzwerk.workouts.PublicExerciseIntegrationTest" \
  --tests "com.satzwerk.workouts.PublicWorkoutPlanIntegrationTest" \
  --tests "com.satzwerk.sessions.PublicSessionWriteIntegrationTest" \
  --no-daemon
```

Expected: PASS for the existing partner-grant path on all three suites.

- [ ] **Step 4: Commit**

```bash
git add \
  backend/src/main/kotlin/com/satzwerk/workouts/PublicExerciseRouter.kt \
  backend/src/main/kotlin/com/satzwerk/workouts/PublicWorkoutPlanRouter.kt \
  backend/src/main/kotlin/com/satzwerk/sessions/PublicSessionMutationRouter.kt \
  backend/src/main/kotlin/com/satzwerk/measurements/PublicMeasurementRouter.kt \
  backend/src/main/kotlin/com/satzwerk/medications/PublicMedicationRouter.kt
git commit -m "refactor(publicapi): route public writes through shared policy

Swap all public-write routers from partner-only validation and policy services
to the shared public-write entry point.

Co-authored-by: Copilot App <223556219+Copilot@users.noreply.github.com>"
```

---

## Task 5: Add PAT-backed public-write coverage and update regression assertions

**Files:**
- Modify: `backend/src/test/kotlin/com/satzwerk/workouts/PublicExerciseIntegrationTest.kt`
- Modify: `backend/src/test/kotlin/com/satzwerk/workouts/PublicWorkoutPlanIntegrationTest.kt`
- Modify: `backend/src/test/kotlin/com/satzwerk/sessions/PublicSessionWriteIntegrationTest.kt`
- Modify: `backend/src/test/kotlin/com/satzwerk/measurements/PublicMeasurementIntegrationTest.kt`
- Modify: `backend/src/test/kotlin/com/satzwerk/medications/PublicMedicationIntegrationTest.kt`

### Steps

- [ ] **Step 1: Add failing PAT tests for one body-based route and one command-style route**

In `backend/src/test/kotlin/com/satzwerk/workouts/PublicExerciseIntegrationTest.kt`, add a PAT-backed body-write test:

```kotlin
@Test
fun `personal api token with exercises write scope can create an Exercise and records PAT audit metadata`() {
    val jwt = registerAndLogin()
    val createdToken = createPersonalToken(jwt, listOf(PublicScope.EXERCISES_WRITE))
    val idempotencyKey = UUID.randomUUID().toString()

    client
        .post()
        .uri("/api/public/exercises")
        .header("Authorization", "Bearer ${createdToken.token}")
        .header("Idempotency-Key", idempotencyKey)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(mapOf("name" to "PAT Bench Press", "muscleGroup" to "CHEST"))
        .exchange()
        .expectStatus().isCreated

    runBlocking {
        val records =
            idempotencyRecordRepository
                .findAllByCredentialIdAndPrincipalType(createdToken.id, PublicWritePrincipalType.PERSONAL_API_TOKEN)
                .toList()
        val audits =
            publicWriteAuditRepository
                .findAllByCredentialIdAndPrincipalType(createdToken.id, PublicWritePrincipalType.PERSONAL_API_TOKEN)
                .toList()
        assertEquals(1, records.size)
        assertEquals(1, audits.size)
        assertEquals(null, audits.first().grantId)
    }
}
```

In `backend/src/test/kotlin/com/satzwerk/workouts/PublicWorkoutPlanIntegrationTest.kt`, add a PAT-backed command-style replay test:

```kotlin
@Test
fun `personal api token can replay WorkoutPlan activation through shared public write policy`() {
    val jwt = registerAndLogin()
    val token = createPersonalToken(jwt, listOf(PublicScope.PLANS_WRITE))
    val plan = createPlan(jwt, "PAT Replay Plan")
    val idempotencyKey = UUID.randomUUID().toString()

    val first = activatePublicPlanWithPat(token.token, plan, idempotencyKey)
    val replayed = activatePublicPlanWithPat(token.token, plan, idempotencyKey)

    assertEquals(first, replayed)
    runBlocking {
        val records =
            idempotencyRecordRepository
                .findAllByCredentialIdAndPrincipalType(token.id, PublicWritePrincipalType.PERSONAL_API_TOKEN)
                .toList()
        assertEquals("""{"command":"activate-workout-plan"}""", records.first().requestFingerprint)
    }
}
```

- [ ] **Step 2: Run the two new tests and confirm they fail**

```bash
cd backend && ./gradlew test \
  --tests "com.satzwerk.workouts.PublicExerciseIntegrationTest" \
  --tests "com.satzwerk.workouts.PublicWorkoutPlanIntegrationTest" \
  --no-daemon
```

Expected: FAIL because the public-write repositories/tests still assume partner-only names and helpers, and PAT-backed writes are not covered yet.

- [ ] **Step 3: Update the integration suites to the generalized repository names and PAT helpers**

Across the listed integration suites:

1. Rename injected repository types from `PartnerWriteAuditRepository` to `PublicWriteAuditRepository`.
2. Keep partner assertions working either by:
   - using retained `findAllByGrantId(...)` helpers on the generalized repositories, or
   - switching to `findAllByCredentialIdAndPrincipalType(grant.grantId, PublicWritePrincipalType.PARTNER_APP)`.
3. Add a local helper for personal token creation where needed:

```kotlin
private fun createPersonalToken(
    jwt: String,
    scopes: List<String>,
): CreatedPersonalApiTokenResponse =
    client.post().uri("/api/tokens")
        .header("Authorization", "Bearer $jwt")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(mapOf("name" to "Public Write PAT ${UUID.randomUUID()}", "scopes" to scopes))
        .exchange()
        .expectStatus().isCreated
        .expectBody(CreatedPersonalApiTokenResponse::class.java)
        .returnResult().responseBody!!
```

4. Add a PAT activation helper in `PublicWorkoutPlanIntegrationTest.kt`:

```kotlin
private fun activatePublicPlanWithPat(
    rawToken: String,
    planId: UUID,
    idempotencyKey: String,
): WorkoutPlanDetailResponse =
    client
        .post()
        .uri("/api/public/plans/$planId/activate")
        .header("Authorization", "Bearer $rawToken")
        .header("Idempotency-Key", idempotencyKey)
        .exchange()
        .expectStatus().isOk
        .returnResult<WorkoutPlanDetailResponse>()
        .responseBody
        .blockFirst()!!
```

Do **not** remove the existing partner replay assertions from Exercise, WorkoutPlan, Session, Measurement, or Medication tests. They are the regression net for the old path.

- [ ] **Step 4: Run the PAT/partner public-write validation set**

```bash
cd backend && ./gradlew test \
  --tests "com.satzwerk.workouts.PublicExerciseIntegrationTest" \
  --tests "com.satzwerk.workouts.PublicWorkoutPlanIntegrationTest" \
  --tests "com.satzwerk.sessions.PublicSessionWriteIntegrationTest" \
  --tests "com.satzwerk.measurements.PublicMeasurementIntegrationTest" \
  --tests "com.satzwerk.medications.PublicMedicationIntegrationTest" \
  --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Run the repo pre-push backend gate**

```bash
cd backend && ./gradlew ktlintCheck detekt compileTestKotlin --no-daemon
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add \
  backend/src/test/kotlin/com/satzwerk/workouts/PublicExerciseIntegrationTest.kt \
  backend/src/test/kotlin/com/satzwerk/workouts/PublicWorkoutPlanIntegrationTest.kt \
  backend/src/test/kotlin/com/satzwerk/sessions/PublicSessionWriteIntegrationTest.kt \
  backend/src/test/kotlin/com/satzwerk/measurements/PublicMeasurementIntegrationTest.kt \
  backend/src/test/kotlin/com/satzwerk/medications/PublicMedicationIntegrationTest.kt
git commit -m "test(publicapi): cover PAT-backed public writes

Keep partner-grant regression coverage and add PAT-backed public-write tests
for body-based and command-style routes through the shared write policy.

Co-authored-by: Copilot App <223556219+Copilot@users.noreply.github.com>"
```

---

## Validation Summary

Run these commands at the end, in this order:

```bash
cd backend && ./gradlew test \
  --tests "com.satzwerk.common.RequestContextTest" \
  --tests "com.satzwerk.publicapi.PublicWritePrincipalValidationServiceTest" \
  --tests "com.satzwerk.publicapi.PublicWritePolicyServiceTest" \
  --tests "com.satzwerk.workouts.PublicExerciseIntegrationTest" \
  --tests "com.satzwerk.workouts.PublicWorkoutPlanIntegrationTest" \
  --tests "com.satzwerk.sessions.PublicSessionWriteIntegrationTest" \
  --tests "com.satzwerk.measurements.PublicMeasurementIntegrationTest" \
  --tests "com.satzwerk.medications.PublicMedicationIntegrationTest" \
  --no-daemon

cd backend && ./gradlew ktlintCheck detekt compileTestKotlin --no-daemon
```

Expected final result:

- all targeted unit and integration suites pass
- `ktlintCheck`, `detekt`, and `compileTestKotlin` pass
- partner-grant public writes still pass unchanged
- PAT-backed public writes now pass through the same policy path
