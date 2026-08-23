# ADR-0005: Public integration API uses scoped principals and command-style writes

## Status

Accepted

## Context

Satzwerk is adding a public integration API in phases:

- personal automation tokens for user-owned automations
- partner apps that act for a consenting user
- public read surfaces for workout and health-tracking data
- public write surfaces for **Exercise**, **WorkoutPlan**, **WorkoutSession**, **SetLog**, **BodyMeasurement**,
  **Medication**, and **MedicationLog**

Those phases need one policy source of truth before implementation starts. Without it, each slice would invent its own
scope names, consent model, audit rules, idempotency behavior, and conflict handling. That would make revocation
unreliable and would put core domain invariants at risk:

- exactly one active **WorkoutPlan** per user
- exactly one open **WorkoutSession** per user
- **Exercise** records are per-user, never shared globally
- all workout weights are stored and transmitted in kg

## Decision

### 1. Principal model

The public API supports two principal types:

1. **Personal automation token** — created and revoked directly by a Satzwerk user for their own automation.
2. **Partner app grant** — a partner app acts only after a Satzwerk user grants explicit consent to the app's declared
   scopes.

Both principal types use the same scope vocabulary. Every accepted request resolves to exactly one Satzwerk user. No
public principal may access data for any other user.

### 2. Scope model

Scopes are resource-based and split into read and write capabilities.

| Resource family | Read scope | Write scope | Covers |
| --- | --- | --- | --- |
| Exercises | `exercises:read` | `exercises:write` | **Exercise** |
| Plans | `plans:read` | `plans:write` | **WorkoutPlan**, **WorkoutGroup**, **WorkoutExercise**, activation |
| Sessions | `sessions:read` | `sessions:write` | **WorkoutSession**, **SetLog** |
| Analytics | `analytics:read` | n/a | **Heatmap**, summary metrics, personal-record style analytics |
| Measurements | `measurements:read` | `measurements:write` | **BodyMeasurement** |
| Medications | `medications:read` | `medications:write` | **Medication**, **MedicationLog**, adherence analytics |

Rules:

- A request must have the exact scope needed for the route it calls.
- `analytics:read` does not imply access to raw workout data. It only covers analytics endpoints.
- Write scopes do not imply read scopes. If a client needs both, it must be granted both.
- Partner apps may request only declared scopes. Users grant or deny those scopes explicitly.
- Personal automation tokens may be created only with scopes chosen by the user at token creation time.

### 3. Consent and ownership boundaries

- Satzwerk remains the source of truth for user identity, app identity, scope grants, revocation state, and ownership.
- Partner apps are always constrained to the consenting user's records.
- Public writes must reuse the same ownership checks the first-party product already uses. Public access never creates a
  bypass around service-layer ownership validation.
- Revoking a personal token or a partner-app grant blocks future requests immediately.
- Partner credentials are bound to both the app and the consenting user. A credential for one app-user pair must not be
  reusable for another pair.

### 4. Source-of-truth and conflict rules for writes

Satzwerk accepts partner writes only as validated domain commands. It does not expose raw table-sync semantics.

General rules:

- Satzwerk remains the source of truth for invariant enforcement, authorization, timestamps used for audit, and primary
  identifiers.
- External clients may be the source of truth for user-authored content they create through the public API, but only
  through the allowed commands below.
- Public write endpoints must fail with explicit `4xx` errors when a request would violate an invariant, ownership rule,
  validation rule, or concurrency rule.

Conflict policy:

- Public writes use explicit command semantics, not blind row replacement.
- Replaying the same command with the same idempotency key returns the original successful response.
- Reusing the same idempotency key with a different payload fails with `409 Conflict`.
- For commands that target mutable user-authored resources without a separate version field (**Exercise**,
  **WorkoutPlan**, **WorkoutGroup**, **WorkoutExercise**, **Medication**, **MedicationLog**, **BodyMeasurement**),
  Satzwerk currently applies last-write-wins semantics after re-evaluating authorization, validation, and invariant
  rules on each request.
- For commands that would violate singleton invariants under concurrency (for example, activating a **WorkoutPlan** or
  starting a **WorkoutSession**), Satzwerk must enforce the invariant atomically at the database boundary and surface a
  `409 Conflict` when a concurrent writer wins first.

Resource-specific rules:

- **Exercise** — partner apps may create and update only the consenting user's **Exercise** records. There is no shared
  exercise catalog.
- **WorkoutPlan** — partner apps may create and update plan structures. Any public activation command must preserve the
  rule that activating one **WorkoutPlan** deactivates all others for that user. The single-active-plan rule must be
  enforced atomically (not just by read-then-write logic), and conflicting concurrent activations must fail with
  `409 Conflict`.
- **WorkoutSession** — partner apps may start, mutate, and complete **WorkoutSession** records only through constrained
  workflows. Starting a new one must fail if the user already has an open **WorkoutSession**.
- **SetLog** — partner apps may append or update **SetLog** records only inside an owned **WorkoutSession**. They must
  not create gaps, duplicate `setNumber` values for the same exercise in the same session, or break session ownership.
  The referenced **Exercise** must also belong to the acting user and, for session-bound writes, must be one of the
  **WorkoutExercise** records in the session's **WorkoutGroup**. Reusing the first-party service layer is acceptable
  only if these checks still run explicitly for public writes.
- **BodyMeasurement** — public writes preserve the current upsert-by-`measurementDate` behavior. When a measurement for
  the same date already exists, null fields in the request preserve the existing values instead of clearing them.
- **Medication** — partner apps may create and update **Medication** records subject to the existing per-user
  case-insensitive name uniqueness rule and dosage-unit validation.
- **MedicationLog** — partner apps may create **MedicationLog** records only for owned **Medication** records. Domain
  fields such as `takenAt` remain validated exactly as they are for first-party writes.

### 5. Units, timestamps, and external identifiers

- All workout weights in public requests and responses use kg only.
- **BodyMeasurement** weight remains kg; circumference fields remain cm.
- Where the current domain already allows historical timestamps (`loggedAt`-style data, `takenAt`, `measurementDate`),
  public writes may supply those business timestamps subject to the same validation rules as first-party writes.
- Audit timestamps are always Satzwerk-generated.
- When public write slices need a client-stable reference, the implementation may add an app-scoped external reference,
  but it must be unique per `(app, user, resource-type)` and must not replace Satzwerk primary IDs internally.

### 6. Idempotency policy

- Every externally callable create, update, complete, or import-style public write must accept an `Idempotency-Key`
  header.
- Idempotency is scoped to `(principal, method, route, key)`.
- Replaying the same request with the same idempotency key returns the original successful response instead of creating a
  duplicate side effect.
- Reusing the same idempotency key with a different payload must fail with `409 Conflict`.
- Safe read requests do not require idempotency keys.
- Revoked or unauthorized requests do not mint a successful idempotent record.

### 7. Audit and revocation requirements

Every public write must emit an audit record that captures:

- principal type (`personal_token` or `partner_app`)
- acting user ID
- acting app ID when applicable
- token or credential ID
- granted scopes used for authorization
- route, HTTP method, and response status
- target resource type and target resource ID when known
- idempotency key when present
- request timestamp

Required revocation behavior:

- Personal-token revocation is immediate.
- Partner-grant revocation is immediate.
- Audit records remain retained after revocation.
- Revocation events themselves are audited.

### 8. Endpoint design rule

Public API slices should prefer dedicated public routes or adapters over exposing frontend-internal assumptions directly.
They may reuse existing services and repositories internally, but the public contract must remain stable for external
clients.

## Consequences

- #204 and #205 can now implement the shared token, consent, and revocation model against a fixed scope vocabulary.
- #206, #207, #208, and #209 must reuse this scope and audit model instead of inventing per-slice variations.
- Public write routes will need explicit idempotency handling from the start.
- Shared auth infrastructure will likely grow beyond the current JWT-only filter path, so downstream work should extract
  reusable public-principal resolution instead of duplicating checks per router.
