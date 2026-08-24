# Public Write Policy for Personal Tokens and Partner Grants — Design

**Date:** 2026-08-23  
**Status:** Draft

## Problem

Public write routes currently depend on a partner-only policy path:

- routers require `PartnerWritePrincipalValidationService`
- the shared write policy accepts only `PartnerAppRequestPrincipal`
- idempotency ownership is keyed by `grant_id`
- audit storage and model names are partner-specific

That shape works for partner-app grants, but it blocks the same public write routes from supporting personal API tokens
through one shared policy entry point.

## Goal

Let one public write policy path support both **PersonalApiToken** and partner-app grant principals, while preserving the
existing public-write behavior for partner grants.

The finished slice must let a public route enforce:

- public scope authorization
- idempotency ownership
- audit capture

consistently, regardless of whether the caller authenticates with a personal API token or a partner grant.

## Assumptions Made (Autonomous)

- Public write routes keep using the existing `handlePublicScope(...)` outer gate for scope enforcement.
- Business-domain services do not need new ownership rules; the public-write layer should keep delegating the resolved
  `userId` into the same service methods that first-party and current partner routes already use.
- Personal API token audit identity should be the persisted token ID, not the raw bearer token or token hash.
- Partner-app writes must keep app/grant traceability after the storage rename, even though the primary abstraction
  becomes credential-agnostic.
- This slice may rename partner-specific persistence and Kotlin types now, because the chosen direction is to pay down
  the naming debt instead of widening the old abstraction.

## Approach

Replace the partner-only write path with a neutral public-write policy that accepts a shared public-write principal and
stores credential-aware idempotency and audit metadata.

In practice, that means:

1. keep public scope checks where they already live
2. resolve one shared public-write principal from `RequestContext`
3. execute one shared idempotency + audit flow for both credential types
4. preserve partner-grant metadata where it exists
5. add personal-token identity where partner-only storage currently has no equivalent

## Design

### 1. Shared public-write principal

Add a credential-aware principal model for public writes instead of accepting only `PartnerAppRequestPrincipal`.

The shared principal must expose:

- `principalType` — `PERSONAL_API_TOKEN` or `PARTNER_APP`
- `userId`
- `scopes`
- `credentialId` — token ID for a personal token, grant ID for a partner grant
- `appId` when the principal is a partner-app grant
- `grantId` when the principal is a partner-app grant

`RequestContext` already distinguishes request principal kinds. This slice should extend the principal resolution path so
the personal-token branch carries token identity, not just `userId` and scopes.

Result:

- public write routers can resolve one shared principal shape
- the policy layer stops knowing whether the route began as a PAT or a partner grant except where audit metadata needs
  that distinction

### 2. Router and validation boundary

Public write routers should keep this overall shape:

1. `handlePublicScope(...)`
2. parse/validate request body or path data
3. resolve a shared public-write principal
4. call the shared public-write policy
5. delegate domain work through the resolved `userId`

The current `PartnerWritePrincipalValidationService` should be replaced by a neutral validation/resolution service for
public writes.

Validation rules by principal type:

- **Partner-app grant**
  - keep the current active-grant revalidation against `X-App-Token`
  - require the active grant to match the resolved user/app/grant identity exactly
- **Personal API token**
  - require a resolved PAT principal from the auth filter
  - do not re-hash or persist the raw token in the write policy
  - use the token's persisted ID as the credential identity for idempotency and audit

This keeps partner-grant revocation behavior intact, and it lets PAT-backed writes enter the same route/policy flow
without partner-specific preconditions.

### 3. Shared policy service

Rename the partner-only policy service and its supporting models/repositories to neutral public-write names.

The shared policy service keeps the current responsibilities:

- require `Idempotency-Key`
- claim or replay an idempotency record
- detect payload mismatch for the same key
- finalize the stored response after a successful domain write
- delete the pending record when the domain write fails
- record an audit row for both first execution and replay

What changes is the metadata shape. The policy should operate on public-write metadata that includes:

- principal type
- user ID
- credential ID
- granted scopes
- request method
- request path
- idempotency key
- request fingerprint
- optional app/grant metadata

The block passed into the policy should still receive only the resolved `userId`. That keeps the public-write layer
responsible for auth/idempotency/audit and keeps domain services focused on domain rules.

### 4. Persistence changes

Do the storage rename in this slice so the schema matches the new abstraction.

#### Idempotency table

Rename the current partner-shaped idempotency table to a neutral public-write name and widen the ownership columns from
grant-only to credential-aware fields.

The resulting stored data must support:

- `principal_type`
- `credential_id`
- `user_id`
- `request_method`
- `request_path`
- `idempotency_key`
- `request_fingerprint`
- `response_status`
- `response_body`
- `app_id` when applicable
- `grant_id` when applicable
- `created_at`

The uniqueness rule should become:

`(principal_type, credential_id, request_method, request_path, idempotency_key)`

That preserves the current route-scoped replay behavior, but it removes the assumption that the acting credential is
always a partner grant.

#### Audit table

Rename the current audit table to a neutral public-write name and store:

- `principal_type`
- `user_id`
- `credential_id`
- `granted_scopes`
- `request_method`
- `request_path`
- `idempotency_key`
- `response_status`
- `app_id` when applicable
- `grant_id` when applicable
- `created_at`

The audit table does not need the raw bearer token, token hash, or serialized request body.

### 5. Migration strategy

Use one Flyway migration to:

1. rename the partner-specific tables to neutral public-write names
2. add the new credential-aware columns
3. backfill existing rows as `PARTNER_APP`
4. preserve current partner metadata in nullable `app_id` and `grant_id` columns
5. replace the old uniqueness/index definitions with credential-aware ones

Backfill rules:

- `principal_type = 'PARTNER_APP'` for all existing rows
- `credential_id = grant_id` for existing rows
- `user_id`, `app_id`, and `grant_id` are populated from the existing partner-grant relationship

For idempotency rows that currently do not store `user_id` or `app_id`, the migration can populate them by joining the
existing grant reference. The final schema should not require later request-time joins just to answer "who acted?"

### 6. Error handling and behavior that must not change

The credential-generalization work should not change the observable public-write contract except to allow PAT-backed
writes through the same path.

Preserve these behaviors:

- missing `Idempotency-Key` → `400 Bad Request`
- missing required public scope → `403 Forbidden`
- invalid or revoked credential → `401 Unauthorized`
- reused idempotency key with a different payload → `409 Conflict`
- in-flight duplicate request that never finalizes within the current polling window → `409 Conflict`
- domain conflicts and validation errors still map through the existing route-specific error handling

Partner-grant public writes must keep the same route contracts, success codes, replay semantics, and audit-on-replay
behavior they have today.

### 7. Testing strategy

#### Unit tests

Add focused tests for:

- shared public-write principal resolution for PAT and partner-app branches
- PAT credential identity capture
- partner-grant revalidation still rejecting mismatched or revoked grants
- idempotency uniqueness using principal type + credential ID
- audit metadata capture for both PAT and partner-app writes
- replay behavior still recording audit entries for both credential types

#### Integration tests

Keep the existing partner-grant integration tests as regression coverage.

Add PAT-backed public-write integration coverage on representative write routes, with at least:

- one body-based write route such as public **Exercise** create/update
- one command-style route such as public **WorkoutPlan** activation or public **WorkoutSession** completion

Each PAT integration test should prove:

- the route accepts a PAT bearer token
- the same public scope names authorize the request
- idempotent replay returns the original response
- audit and idempotency rows are stored under the PAT credential identity, not partner-grant fields

The goal is not to duplicate every partner integration test for PATs in this slice. The goal is to prove the shared
policy path once for each write shape while keeping existing partner coverage intact.

## Out of Scope

- changing public scope names
- changing business-domain validation in Exercise, WorkoutPlan, WorkoutSession, SetLog, BodyMeasurement, Medication, or
  MedicationLog services
- adding new public routes
- adding read-side PAT behavior beyond what already exists
- storing raw PAT values or token hashes in public-write audit storage

## Risks and Mitigations

- **Migration complexity:** renaming and widening live audit/idempotency tables is more invasive than a pure code change.  
  **Mitigation:** keep the migration single-purpose, backfill in place, and preserve partner metadata instead of
  reshaping it away.
- **Principal-resolution drift:** PAT and partner principals could diverge if routers keep bespoke validation logic.  
  **Mitigation:** move routers to one neutral validation/resolution service before updating route call sites.
- **Identity leakage:** a naïve PAT implementation could store raw bearer material in audit or idempotency records.  
  **Mitigation:** carry only the persisted PAT token ID into the public-write principal and storage layer.
- **Regression risk on partner writes:** renaming the abstraction could break the current grant path.  
  **Mitigation:** preserve partner-grant integration coverage and keep the write policy semantics unchanged.

## Success Criteria

This design succeeds when:

- a public write route authorizes both PAT and partner-app callers through one shared policy entry point
- idempotency ownership is credential-aware instead of grant-only
- audit records capture principal type, user, credential identity, scopes, route, method, response status, and
  idempotency key
- partner-app writes keep their current behavior and traceability
- PAT-backed public writes are covered by the same shared policy path, not by a second write-policy implementation
