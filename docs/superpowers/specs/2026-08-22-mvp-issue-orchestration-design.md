# MVP Issue Orchestration — Design

**Date:** 2026-08-22  
**Status:** Draft

## Problem

The "Satzwerk web MVP" plan has a Ready batch for third-party integration work. The batch mixes one policy prerequisite
issue (#203) with six implementation slices (#204-#209) that touch auth, consent, scopes, auditability, public API
behavior, and domain invariants such as one active **WorkoutPlan** per user and one open **WorkoutSession** per user.

Running that batch ad hoc would make it easy to skip triage discipline, miss shared risks, or open PRs before the
latest review and CI state are actually merge-ready.

## Goal

Run a repeatable orchestration workflow that triages the Ready batch, writes a durable per-issue implementation handoff,
dispatches approved implementation work in isolated child sessions, and drives each resulting PR to merge-ready state.

## Assumptions Made (Autonomous)

- Issue #203 is prerequisite context for the batch, not part of the implementation run.
- Batch-level human approval for `risky` issues was granted during design review, so `risky` issues may proceed after
  triage unless fresh ambiguity appears.
- The parent session should stay a coordination surface. Child sessions own code changes, local validation, and PR
  creation for their assigned issue.
- Native Copilot project-session worktrees are preferred over manual `git worktree add` so the harness can manage
  branch state safely.
- The current Ready implementation batch is #204-#209, so the maximum useful parallelism for implementation is six
  child sessions rather than the global cap of eight.

## Approach

Use this session as the control plane for the Ready batch:

1. Discover Ready issues from the "Satzwerk web MVP" plan.
2. Triage each implementation issue against a single rubric: completeness, duplicates, priority, and risk class.
3. Write one durable handoff plan per approved issue to `~/.copilot/session-state/handoff-issue-XXX.md`.
4. Create one isolated child session per approved issue so implementation proceeds in parallel without shared branch
   state.
5. Keep PR-driving centralized in the parent session so merge-readiness uses one consistent definition across the batch.

This keeps the risky shared decisions in one place while still letting independent issue work happen concurrently.

## Inputs and Scope

### In Scope

- Ready issues in the "Satzwerk web MVP" plan
- Triage records for #204-#209
- Per-issue implementation handoff documents
- Child-session dispatch for approved issues
- PR follow-through until merge-ready or explicitly blocked

### Out of Scope

- Rewriting issue #203 in this run
- Manual PR merging
- Opportunistic fixes unrelated to the assigned issue scope
- Changes outside the current repository or worktree/session graph

## Workflow Design

### 1. Batch Discovery

The parent session queries open issues and filters to project items where:

- plan title = `Satzwerk web MVP`
- project status = `Ready`

Then it splits the result into:

- prerequisite context (`ready-for-human` issue #203)
- implementation batch (`ready-for-agent` issues #204-#209)

If no Ready implementation issues exist, the workflow stops with:

> **No Ready issues found. Nothing to do.**

### 2. Triage

Each implementation issue gets a triage record with:

- issue number and title
- acceptance-criteria presence check
- likely-duplicate notes inside the current Ready batch
- priority (`HIGH`, `MEDIUM`, `LOW`)
- classification (`blocked`, `risky`, `clear`)
- brief rationale grounded in `CONTEXT.md`, ADRs, and repo conventions

#### Priority rubric

- **HIGH** — strong user value and manageable implementation risk
- **MEDIUM** — moderate value or uncertainty
- **LOW** — low urgency or dependency pressure

#### Classification rules

- **blocked** — incomplete specification, unresolved dependency, or ambiguity that prevents safe execution
- **risky** — shared auth, consent, token, audit, schema, or cross-cutting public API behavior
- **clear** — well-scoped change without the above hazards

Because the current batch is an external-integration program, every implementation issue is expected to be at least
`risky` unless triage proves the scope is narrower than it appears.

### 3. Planning

For every `clear` or approved `risky` issue, the parent session writes a concise handoff file at:

`~/.copilot/session-state/handoff-issue-XXX.md`

Each handoff must include:

- scope
- likely files/modules
- tests to add or update
- issue-specific risks
- validation commands
- PR acceptance checklist

The handoff is intentionally shorter than a full product spec. It exists to let an implementation child session start
with the right repo context and guardrails.

### 4. Parallel Implementation

The parent session creates one isolated child session per approved issue. Each child session:

- runs in its own worktree-backed branch named `feature/issue-XXX-<short-slug>`
- inspects relevant code paths before editing
- prefers test-first edits where practical
- follows existing backend and frontend repo conventions
- performs targeted validation for the touched area
- opens a PR only after local validation succeeds

Parallelism rules:

- maximum eight concurrent issues globally
- use six for the current batch because only six implementation issues are in scope
- do not parallelize two issues into the same child session
- if two issues converge on the same hotspot file, the parent session may sequence them instead of forcing conflict

### 5. PR Driving

The parent session tracks each PR until all of the following are true on the latest HEAD:

- required checks pass
- no unresolved review threads remain
- no outstanding requested changes remain
- branch is mergeable

If CI or review failures are unrelated to the issue scope, the parent session reports them separately instead of
expanding the implementation scope.

## Session Responsibilities

### Parent Session

- discover issues
- triage
- write handoff files
- create child sessions
- monitor PR state
- resolve orchestration blockers
- publish final completion summaries

### Child Sessions

- inspect issue-specific code
- implement changes
- run targeted validations
- create/update PRs
- report blockers, validation results, and PR URLs

## Validation Strategy

### Parent-session validation

- confirm the Ready issue set before dispatch
- confirm each handoff file exists and matches the assigned issue
- confirm each child session reports a PR URL before PR driving starts
- confirm completion summaries reference latest PR state, not stale results

### Child-session validation

Use the smallest validation set that covers the changed area. Typical commands will come from the handoff and may
include:

- `cd backend && ./gradlew test --tests "<TestClass>"`
- `cd backend && ./gradlew ktlintCheck detekt compileTestKotlin --no-daemon`
- `cd frontend && pnpm test -- <path-to-test-file>`
- `cd frontend && pnpm run lint && tsc -b --noEmit`

Children should escalate only when issue-specific validation repeatedly fails or repo-wide failures block safe progress.

## Risks and Mitigations

- **Shared auth surface overlap:** multiple issues may need the same auth or consent primitives. Mitigation: triage
  duplicates early, reuse shared abstractions, and sequence overlapping issues if they target the same hotspot.
- **Policy drift from #203:** implementation may invent rules that the policy issue should decide. Mitigation: treat
  #203 as prerequisite context and block any issue that depends on missing policy decisions.
- **False merge-ready signal:** a PR can be locally green but still have stale review threads or failing required
  checks. Mitigation: parent session owns merge-readiness after PR creation.
- **Scope creep across integration slices:** partner read/write surfaces can sprawl. Mitigation: keep each child
  session constrained to its issue acceptance criteria and report unrelated failures separately.

## Success Criteria

This orchestration design succeeds when:

- Ready implementation issues are triaged in a prioritized list
- every approved issue has a handoff file at the required session path
- implementation runs in isolated child sessions
- each completion summary includes issue number/title, PR URL, acceptance-criteria status, review items addressed,
  follow-ups, and merge-readiness status
- no issue is marked done before its PR is actually merge-ready
