# Heldwerk — RPG Workout Tracker: Design Spec

**Date:** 2026-07-07  
**Status:** Approved for implementation planning  
**Repo:** https://github.com/clertonraf/heldwerk  
**Project board:** https://github.com/users/clertonraf/projects/3

---

## 1. Project Overview

Heldwerk is a greenfield RPG workout tracker — not a fork of Satzwerk. Users log gym sessions and their activity builds an RPG character: earning XP, levelling up, unlocking skills, and evolving a class that reflects their actual training style.

**Core loop:** log sets → earn XP → level up → unlock skills → character class reflects how you train.

The app is self-hosted and multi-user. It is inspired by Satzwerk's domain model but designed from scratch with the RPG system as a first-class citizen.

---

## 2. Tech Stack

| Layer | Choice | Rationale |
|---|---|---|
| Backend language | Kotlin | Strong typing, expressive domain modelling |
| Backend framework | Spring MVC (not WebFlux) | Simpler than reactive; sufficient for this scale |
| ORM | JPA (not R2DBC) | No coroutine/test edge-cases; simpler mapping |
| Database | PostgreSQL | Battle-tested, same as Satzwerk |
| Migrations | Flyway | Versioned SQL migrations |
| Frontend | React + TypeScript + Vite | Type-safe, fast DX |
| State / data fetching | Zustand + TanStack Query | Auth store + server cache |
| Build tool (backend) | Gradle (Kotlin DSL) | |
| Containerisation | Docker Compose | App + DB + optional parser sidecar |

---

## 3. Domain Language

| Term | Definition | Avoid |
|---|---|---|
| **WorkoutPlan** | Named collection of WorkoutGroups, owned by a user. Exactly one active at a time. | Program, routine |
| **WorkoutGroup** | Named training day within a WorkoutPlan (e.g. "Push Day"). Ordered by `orderIndex`. | Split, day |
| **WorkoutExercise** | Prescribed exercise in a WorkoutGroup — target sets, target reps, optional AdvancedTechnique, `toFailure` flag. Not a performance record. | Planned exercise |
| **Exercise** | Reusable movement owned by a user: name, muscle group, `exerciseType`, optional description/video/equipment. Per-user only — no global catalog. | Movement, lift |
| **ExerciseType** | Enum on Exercise: `STRENGTH`, `POWER`, `CARDIO`, `MOBILITY`. Determines which character stat grows when sets are logged. | Category, tag |
| **AdvancedTechnique** | Optional intensity modifier on WorkoutExercise. Enum: `SST`, `REST_PAUSE`, `GVT`, `FST_7`, `GIRONDA`. | Technique, method |
| **WorkoutSession** | Single training event started against a WorkoutGroup. One open session per user at a time. | Workout commit, training log |
| **SetLog** | Single performed set: `exerciseId`, `setNumber`, `weight` (always kg), `reps`, `is_pr`. Atomic unit of training data. | ExerciseLog, rep log |
| **Heatmap** | GitHub-style contribution grid, daily intensity from set count. | Contribution graph |
| **PlanImport** | Uploading an xlsx spreadsheet to create a WorkoutPlan via the importer plugin. | Plan upload |
| **CharacterProfile** | A user's RPG character: level, per-stat XP totals, computed class, character name. One per user, created on registration. | Avatar, profile |
| **XpEvent** | Immutable record of a single XP award: source type, amount, stat affected, reference entity ID, timestamp. | XP log entry |
| **CharacterClass** | Title derived from dominant stat(s). Never user-set — always computed. | Class, role |
| **Skill** | Ability unlocked by reaching a training milestone. Shows as badge on character sheet and grants a passive XP multiplier or flat bonus. | Achievement, badge |

---

## 4. Character System

### 4.1 Stats

| Stat | Code | Grows from ExerciseType |
|---|---|---|
| Strength | STR | `STRENGTH` |
| Power | PWR | `POWER` |
| Endurance | END | `CARDIO` |
| Agility | AGI | `MOBILITY` |

Sets logged for exercises without an `exerciseType` award flat XP only (`total_xp` increments; no stat column changes).

### 4.2 Character Classes (hybrid rule)

Class is computed from dominant stat(s). A **hybrid class** triggers when the top-2 stats are within 20% of each other (i.e. `second >= first * 0.8`).

| Dominant stat(s) | Class |
|---|---|
| STR | Warrior |
| PWR | Berserker |
| END | Ranger |
| AGI | Monk |
| STR + PWR | Gladiator |
| STR + END | Juggernaut |
| STR + AGI | Duelist |
| PWR + END | Titan |
| PWR + AGI | Assassin |
| END + AGI | Scout |
| None / all zero | Apprentice |

### 4.3 Level Curve

```
XP required for level N = N² × 500
```

Lv2 = 2,000 · Lv5 = 12,500 · Lv10 = 50,000 · Lv20 = 200,000. Pure formula — no lookup table.

### 4.4 XP Award Table

| Source type | `source_type` value | Base XP | Stat affected |
|---|---|---|---|
| SetLog logged | `SET_LOG` | 10 | From exerciseType (flat if null) |
| WorkoutSession completed | `SESSION_COMPLETE` | 50 | Flat |
| Session matches active plan | `PLAN_ADHERENCE` | 25 | Flat |
| SetLog with `is_pr = true` | `PERSONAL_RECORD` | 30 | From exerciseType (flat if null) |
| ≥3 training days this week (on session complete) | `STREAK` | 20 | Flat |
| Logged reps ≥ target reps on WorkoutExercise | `TARGET_REPS` | 10 | From exerciseType (flat if null) |

Skill multipliers are applied to the base amount before inserting the XpEvent.

### 4.5 Skill Catalog (MVP — 10 skills, static definition in Kotlin)

| `skill_id` | Name | Unlock condition | Passive bonus |
|---|---|---|---|
| `iron_grip` | Iron Grip | 5 PRs on STRENGTH exercises | +15% XP on STRENGTH sets |
| `leg_day_veteran` | Leg Day Veteran | 5 PRs on legs muscle group | +15% XP on leg sets |
| `cardio_machine` | Cardio Machine | 100 CARDIO sets logged | +20% XP on END sets |
| `consistent` | Consistent | 7-day training streak | +10% XP on session complete |
| `pr_hunter` | PR Hunter | 10 total PRs (any exercise) | +10 flat XP per future PR |
| `centurion` | Centurion | 100 WorkoutSessions completed | +25 flat XP per session complete |
| `power_seeker` | Power Seeker | 5 PRs on POWER exercises | +15% XP on POWER sets |
| `balanced_athlete` | Balanced Athlete | All 4 stats ≥ 500 XP each | +10% XP on all sources |
| `planner` | Planner | 30 sessions following active plan | +20 flat XP per plan adherence bonus |
| `elite` | Elite | Reach level 20 | +5% XP on all sources |

Skills are defined as a Kotlin `enum class` or `sealed class` in the `character` package. No DB table for definitions — only `user_skills` tracks which skills a user has unlocked.

---

## 5. Database Schema (complete)

```sql
-- V1: baseline auth
CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  username TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE refresh_tokens (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token TEXT UNIQUE NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- V2: exercises
CREATE TABLE exercises (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  muscle_group TEXT NOT NULL,
  exercise_type TEXT, -- STRENGTH | POWER | CARDIO | MOBILITY | NULL
  description TEXT,
  video_url TEXT,
  equipment TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- V3: workout plans
CREATE TABLE workout_plans (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT FALSE,
  workout_source TEXT NOT NULL DEFAULT 'MANUAL', -- MANUAL | IMPORTED
  activated_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE workout_groups (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workout_plan_id UUID NOT NULL REFERENCES workout_plans(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  order_index INTEGER NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE workout_exercises (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workout_group_id UUID NOT NULL REFERENCES workout_groups(id) ON DELETE CASCADE,
  exercise_id UUID NOT NULL REFERENCES exercises(id),
  target_sets INTEGER NOT NULL,
  target_reps INTEGER NOT NULL,
  to_failure BOOLEAN NOT NULL DEFAULT FALSE,
  advanced_technique TEXT, -- SST | REST_PAUSE | GVT | FST_7 | GIRONDA | NULL
  order_index INTEGER NOT NULL
);

-- V4: sessions
CREATE TABLE workout_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  workout_group_id UUID NOT NULL REFERENCES workout_groups(id),
  started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  completed_at TIMESTAMPTZ,
  notes TEXT,
  CONSTRAINT one_open_session_per_user UNIQUE (user_id, completed_at) DEFERRABLE
);

CREATE TABLE set_logs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workout_session_id UUID NOT NULL REFERENCES workout_sessions(id) ON DELETE CASCADE,
  exercise_id UUID NOT NULL REFERENCES exercises(id),
  set_number INTEGER NOT NULL,
  weight NUMERIC(6,2) NOT NULL, -- always kg
  reps INTEGER NOT NULL,
  is_pr BOOLEAN NOT NULL DEFAULT FALSE,
  logged_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- V5: character system
CREATE TABLE character_profiles (
  user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  character_name TEXT NOT NULL DEFAULT 'Unnamed Hero',
  level INTEGER NOT NULL DEFAULT 1,
  xp_str INTEGER NOT NULL DEFAULT 0,
  xp_pwr INTEGER NOT NULL DEFAULT 0,
  xp_end INTEGER NOT NULL DEFAULT 0,
  xp_agi INTEGER NOT NULL DEFAULT 0,
  total_xp INTEGER NOT NULL DEFAULT 0,
  character_class TEXT NOT NULL DEFAULT 'Apprentice',
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE xp_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  source_type TEXT NOT NULL,
  xp_amount INTEGER NOT NULL,
  stat_type TEXT, -- STR | PWR | END | AGI | NULL (flat)
  reference_id UUID,  -- set_log.id or workout_session.id
  awarded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE user_skills (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  skill_id TEXT NOT NULL,
  unlocked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, skill_id)
);
```

---

## 6. Backend Architecture

### Package structure (`src/main/kotlin/com/heldwerk/`)

```
auth/           — registration, login, JWT filter, refresh token rotation
character/      — CharacterProfile entity, XpService, SkillEngine, CharacterController
exercises/      — Exercise, WorkoutPlan, WorkoutGroup, WorkoutExercise, controllers
sessions/       — WorkoutSession, SetLog; XP award triggered here
analytics/      — Heatmap endpoint, streak query
config/         — Spring security filter chain, JWT config, Flyway, CORS
import/         — PlanImporter interface, UserDataImporter interface, plugin discovery
```

### Pattern

`Controller → Service → Repository`. Routes declared via `@RestController` + `@RequestMapping`. JPA `@Entity` classes. No R2DBC, no reactive streams.

### XP award flow

```
SetLogService.save(request)
  → compute is_pr (weight > all prior weights for this exercise by this user)
  → save SetLog
  → XpService.award(userId, SET_LOG, 10, statType, setLog.id)
  → if is_pr: XpService.award(userId, PERSONAL_RECORD, 30, statType, setLog.id)
  → if reps >= targetReps: XpService.award(userId, TARGET_REPS, 10, statType, setLog.id)

WorkoutSessionService.complete(sessionId)
  → mark session completed_at = now()
  → XpService.award(userId, SESSION_COMPLETE, 50, null, session.id)
  → if session.workoutGroupId in active plan: XpService.award(userId, PLAN_ADHERENCE, 25, null, session.id)
  → if ≥3 training days this calendar week: XpService.award(userId, STREAK, 20, null, session.id)

XpService.award(userId, sourceType, baseAmount, statType, referenceId)
  1. Load user's UserSkills → apply matching multipliers to baseAmount → finalAmount
  2. INSERT xp_events(userId, sourceType, finalAmount, statType, referenceId)
  3. UPDATE character_profiles: increment stat column (if statType != null) + total_xp
  4. Recompute level: floor(sqrt(total_xp / 500))  — or iterative using N²×500
  5. Recompute class: hybrid rule on xp_str/xp_pwr/xp_end/xp_agi
  6. SkillEngine.checkUnlocks(userId) → INSERT user_skills for newly crossed thresholds
  7. Save CharacterProfile
```

### API surface

```
# Auth
POST /api/auth/register
POST /api/auth/login
POST /api/auth/refresh
POST /api/auth/logout

# Exercises
GET/POST        /api/exercises
GET/PUT/DELETE  /api/exercises/{id}

# Workout plans
GET/POST        /api/plans
GET/PUT/DELETE  /api/plans/{id}
POST            /api/plans/{id}/activate
GET/POST        /api/plans/{id}/groups
PUT/DELETE      /api/plans/{planId}/groups/{groupId}
GET/POST        /api/plans/{planId}/groups/{groupId}/exercises
PUT/DELETE      /api/plans/{planId}/groups/{groupId}/exercises/{id}

# Sessions
POST            /api/sessions                    → start session
GET             /api/sessions/open               → current open session (404 if none)
POST            /api/sessions/{id}/complete
DELETE          /api/sessions/{id}               → discard
POST            /api/sessions/{id}/set-logs
DELETE          /api/sessions/{id}/set-logs/{setLogId}

# Character
GET             /api/character                   → CharacterProfile + skills + last 10 XpEvents
PUT             /api/character/name              → rename character
GET             /api/character/xp-events         → paginated XP history
GET             /api/character/skills            → all skills (unlocked + locked with progress %)

# Analytics
GET             /api/analytics/heatmap
GET             /api/analytics/summary

# Import (plugin — only available when importer beans are present)
POST            /api/plans/import                → xlsx plan import
POST            /api/import/user-data            → parse Satzwerk export (phase 1, no commit)
POST            /api/import/user-data/confirm    → commit with enrichment data (phase 2)
```

---

## 7. Import Plugin Architecture

The core app (`heldwerk-core`) has **zero compile-time dependency** on any specific importer.

### Contracts (defined in core)

```kotlin
interface PlanImporter {
    val formatName: String
    val supportedMediaType: String
    suspend fun import(userId: UUID, bytes: ByteArray): ImportedPlan
}

interface UserDataImporter {
    val sourceName: String           // e.g. "satzwerk"
    val supportedMediaType: String
    suspend fun parse(bytes: ByteArray): ParsedUserData
}
```

### Discovery

- Spring registers import-related routes **only if** at least one `PlanImporter` or `UserDataImporter` bean is present (`@ConditionalOnBean`)
- Feature flag: `heldwerk.import.enabled: false` skips all bean registration

### Modules

```
heldwerk-core/                  — main app + plugin interfaces
heldwerk-importer-xlsx/         — xlsx plan importer (calls parser sidecar)
heldwerk-importer-satzwerk/     — Satzwerk full history migration
```

### Satzwerk migration wizard (two-phase)

**Phase 1 — Parse** (`POST /api/import/user-data`)  
Returns `ParsedUserData` JSON — nothing written to DB. Frontend receives: list of exercises (name + muscle group, no exercise_type), plans, sessions, set logs.

**Phase 2 — Enrichment wizard (frontend)**  
Two screens:
1. **Exercise type mapping** — table of imported exercises with an `exerciseType` dropdown per row. Smart default from muscle group (e.g. legs → STRENGTH). Bulk-assign by muscle group supported.
2. **XP origin** — toggle: "Start at Level 1" vs "Calculate starting level from history". If retroactive: preview shown ("~4,200 XP → approx. Level 3").

**Phase 3 — Commit** (`POST /api/import/user-data/confirm`)  
Sends enriched exercise types + retroactive flag. Backend commits atomically:
- Creates all entities
- If retroactive: replays SetLogs through XpService to generate XpEvents and build CharacterProfile
- If fresh start: creates empty CharacterProfile at Level 1

---

## 8. Frontend Architecture

### Feature structure

```
src/
  features/
    auth/                   — LoginPage, RegisterPage, auth hooks
    workouts/               — ExercisesPage, PlansPage, PlanDetailPage, WorkoutGroupPage
    sessions/               — SessionPage (start/active/complete)
    character/              — CharacterPage, CharacterHeader, StatsPanel, SkillsGrid, XpFeed
    analytics/              — DashboardPage, HeatmapWidget
    import/                 — PlanImportPage, SatzwerkImportWizard
  services/                 — all API calls; queryKeys.ts (single source of truth for cache keys)
  store/                    — auth.ts (Zustand, JWT state)
  lib/                      — db.ts (Dexie offline queue), domain builders, formatters
  components/               — shared UI components
```

### Routes

| Path | Page |
|---|---|
| `/` | Dashboard (heatmap + summary) |
| `/workouts` | Plans & exercises |
| `/session` | Active WorkoutSession |
| `/character` | Character sheet |
| `/import/plan` | xlsx plan import |
| `/import/satzwerk` | Satzwerk migration wizard |

### CharacterPage visual design

Dark gradient background (`#1a1128 → #0f1923`). Sections:

1. **Class banner (header)** — dark purple/blue gradient, gold corner ornaments, avatar emoji (class-based), character name (inline editable), class title in gold uppercase, level badge pill, XP progress bar
2. **Stats panel** — 2×2 grid of stat cards, each with stat name, large numeric value, colour-coded mini bar. Dominant stat card has highlighted border + "TOP STAT" badge. Colours: STR=red, PWR=orange, END=blue, AGI=green
3. **Skills section** — pill badges: unlocked = gold border + glow; locked = grey with "X/Y" progress text
4. **XP feed** — last 10 XpEvents: emoji icon + description + `+N XP` right-aligned, colour-coded by source type

---

## 9. Out of Scope (MVP)

- Real-time XP popups / WebSocket
- Social / leaderboards
- Mobile app
- Global exercise catalog
- Character avatar art assets / customisation
- Offline support beyond active session (Dexie queue for set logs only)

---

## 10. Decisions Log

| Decision | Choice | Rationale |
|---|---|---|
| Stack | Kotlin + Spring MVC + JPA | Drops WebFlux/R2DBC complexity; same language as Satzwerk |
| Project name | Heldwerk | *Held* = hero (German); sibling to Satzwerk |
| Character visual | Dark RPG card with colour-coded stats | Clean modern layout with RPG identity |
| Class system | Hybrid (top-2 within 20%) | Richer than single-dominant; rewards balanced training |
| Stat mapping | exerciseType (new field) + muscle_group (existing) | exerciseType → which stat grows; muscle_group → body visual |
| Import | Plugin architecture | Core app has zero compile-time dependency on any importer |
| Satzwerk migration | Two-phase wizard | Allows exercise-type enrichment + XP origin choice before commit |
| XP retroactive | User chooses at import time | User stays in control of starting level |
