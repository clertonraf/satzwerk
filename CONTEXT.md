# Satzwerk

A self-hosted, multi-user gym workout tracker. Users build workout plans, log exercise sets during sessions, and visualise training history as a GitHub-style contribution heatmap.

## Language

**WorkoutPlan**:
A named collection of WorkoutGroups owned by a user. Exactly one plan may be active at a time; activating a plan deactivates all others.
_Avoid_: Program, routine, schedule

**WorkoutGroup**:
A named training day within a WorkoutPlan (e.g., "Treino A", "Push Day"). Ordered by `orderIndex`. A plan has one or more groups.
_Avoid_: Split, day, block

**WorkoutExercise**:
A prescribed exercise within a WorkoutGroup — defines target sets (integer), target reps (integer), an optional advanced technique, and a `toFailure` flag. When `toFailure` is true, the reps value is meaningless and the set is performed until muscular failure. Not a record of performance.
_Avoid_: Exercise entry, planned exercise

**Exercise**:
A reusable movement definition owned by a user: name, muscle group, optional description, optional video URL, optional equipment. Exercises are per-user — there is no global shared catalog.
_Avoid_: Movement, lift

**AdvancedTechnique**:
An optional intensity modifier on a WorkoutExercise. Enum: `SST`, `REST_PAUSE`, `GVT`, `FST_7`, `GIRONDA`.
_Avoid_: Technique, method, protocol

**WorkoutSession**:
A single training event: started by a user against a WorkoutGroup, completed when all sets are logged and the user finishes. Exactly one session may be open at a time; starting a new session requires resuming or discarding the existing one.
_Avoid_: Workout commit, training log, workout entry

**SetLog**:
A single performed set within a WorkoutSession: records `exerciseId`, `setNumber`, `weight` (always in kg), and `reps` performed. The atomic unit of training performance data.
_Avoid_: ExerciseLog, rep log, set entry

**Heatmap**:
A GitHub-style contribution grid showing training activity over time. Each day's intensity is derived from total set count logged that day, bucketed into tiers.
_Avoid_: Contribution graph, activity chart

**WorkoutSource**:
An enum on WorkoutPlan describing how the plan was created: `MANUAL` (built in-app) or `IMPORTED` (created by uploading a spreadsheet via satzwerk-parser).

**PlanImport**:
The act of uploading an xlsx spreadsheet to create a WorkoutPlan. The file is parsed externally by satzwerk-parser; the resulting exercises, groups, and plan are created atomically. The plan name is derived from the filename. The imported plan starts inactive.
_Avoid_: Plan upload, spreadsheet sync

**BodyMeasurement**:
A snapshot of body circumferences and weight for a user on a given date. At most one entry per user per day; saving on an existing date upserts using partial merge (null fields in the request preserve existing values). Circumferences in cm (2 decimal places); weight in kg. All measurement fields are nullable.
_Avoid_: body stats, body log, measurements entry

## Relationships

- A **WorkoutPlan** contains one or more **WorkoutGroups**
- A **WorkoutGroup** contains one or more **WorkoutExercises**
- A **WorkoutExercise** references exactly one **Exercise**
- A **WorkoutSession** is started against exactly one **WorkoutGroup**
- A **WorkoutSession** contains zero or more **SetLogs**
- A **SetLog** references exactly one **Exercise** (denormalised from the WorkoutExercise for direct performance tracking)
- An **Exercise** is owned by exactly one **User**
- A **User** has at most one active **WorkoutPlan** at any time

## Example dialogue

> **Dev:** "When the user taps 'Push Workout', do we create a WorkoutSession straight away?"
> **Domain expert:** "Yes — but only if there's no open WorkoutSession. If one exists, we prompt: resume or discard?"

> **Dev:** "Should the Heatmap query WorkoutSessions or SetLogs?"
> **Domain expert:** "SetLogs — intensity is set count, not session count."

## Flagged ambiguities

- "ExerciseLog" appeared in the original plan to mean a single performed set — resolved: the correct term is **SetLog**.
- "code" was a field on WorkoutGroup (A/B/C label) — resolved: dropped. `title` alone identifies a group.
- "global exercise catalog" was mentioned in the plan — resolved: exercises are **per-user only**, no shared catalog.
- "commit" was used in UX copy (WorkoutCommitCard, "push workout") — resolved: the domain entity is **WorkoutSession**. "Commit" is UX metaphor only, not a domain term.
