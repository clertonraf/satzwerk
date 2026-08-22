# Public Read API — Integration Guide

Satzwerk exposes a stable public read API under `/api/public/` for external clients. All endpoints accept
**personal automation tokens** (PAT) via `Authorization: Bearer <token>` and **partner app tokens** via
`X-App-Token: <token>`. Every response is limited to the consenting user's data.

---

## Authentication

### Personal automation token (PAT)

Create a token from the Satzwerk UI (Settings → API Tokens) or via the API:

```bash
# 1. Obtain a JWT by logging in
JWT=$(curl -s -X POST https://your-satzwerk/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"..."}' | jq -r .accessToken)

# 2. Create a PAT with the required scopes
TOKEN=$(curl -s -X POST https://your-satzwerk/api/tokens \
  -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d '{"name":"my-script","scopes":["exercises:read","plans:read","sessions:read","analytics:read"]}' \
  | jq -r .token)
```

Use it on any public endpoint:

```bash
curl -H "Authorization: Bearer $TOKEN" https://your-satzwerk/api/public/exercises
```

### Partner app token

After a user grants your app access (see the partner app consent flow), use the opaque `accessToken` from the grant:

```bash
curl -H "X-App-Token: <accessToken>" https://your-satzwerk/api/public/exercises
```

---

## Scope table

| Endpoint family | Required scope |
|---|---|
| `/api/public/exercises/**` | `exercises:read` |
| `/api/public/plans/**` | `plans:read` |
| `/api/public/sessions/**` | `sessions:read` |
| `/api/public/analytics/**` | `analytics:read` |

---

## Endpoint reference

### Exercises

#### `GET /api/public/exercises`

Returns all exercises belonging to the consenting user. Optionally filter by muscle group.

```bash
# List all exercises
curl -H "Authorization: Bearer $TOKEN" \
  https://your-satzwerk/api/public/exercises

# Filter by muscle group
curl -H "Authorization: Bearer $TOKEN" \
  "https://your-satzwerk/api/public/exercises?muscleGroup=CHEST"
```

**Response 200:**
```json
[
  {
    "id": "a1b2c3d4-...",
    "name": "Bench Press",
    "muscleGroup": "CHEST",
    "description": null,
    "videoUrl": null,
    "equipment": null,
    "createdAt": "2025-01-01T10:00:00Z",
    "updatedAt": "2025-01-01T10:00:00Z"
  }
]
```

#### `GET /api/public/exercises/{id}`

Returns a single exercise by ID. Returns 404 if the exercise does not belong to the consenting user.

```bash
curl -H "Authorization: Bearer $TOKEN" \
  https://your-satzwerk/api/public/exercises/a1b2c3d4-...
```

---

### Plans

#### `GET /api/public/plans`

Returns all WorkoutPlans belonging to the consenting user.

```bash
curl -H "Authorization: Bearer $TOKEN" \
  https://your-satzwerk/api/public/plans
```

**Response 200:**
```json
[
  {
    "id": "b2c3d4e5-...",
    "name": "Push Pull Legs",
    "source": "manual",
    "isActive": true,
    "createdAt": "2025-01-01T10:00:00Z",
    "updatedAt": "2025-01-01T10:00:00Z"
  }
]
```

#### `GET /api/public/plans/{planId}`

Returns a WorkoutPlan with full detail: WorkoutGroups and WorkoutExercises nested inside.

```bash
curl -H "Authorization: Bearer $TOKEN" \
  https://your-satzwerk/api/public/plans/b2c3d4e5-...
```

**Response 200:**
```json
{
  "id": "b2c3d4e5-...",
  "name": "Push Pull Legs",
  "source": "manual",
  "isActive": true,
  "groups": [
    {
      "id": "c3d4e5f6-...",
      "title": "Push Day",
      "orderIndex": 0,
      "exercises": [
        {
          "id": "d4e5f6a7-...",
          "exerciseId": "a1b2c3d4-...",
          "exerciseName": "Bench Press",
          "sets": 4,
          "reps": 8,
          "toFailure": false,
          "advancedTechnique": null,
          "orderIndex": 0
        }
      ]
    }
  ],
  "createdAt": "2025-01-01T10:00:00Z",
  "updatedAt": "2025-01-01T10:00:00Z"
}
```

---

### Sessions

#### `GET /api/public/sessions/history`

Returns completed WorkoutSessions for the consenting user (most recent first). SetLogs are not included in the
list — fetch individual sessions to retrieve them.

```bash
curl -H "Authorization: Bearer $TOKEN" \
  https://your-satzwerk/api/public/sessions/history
```

**Response 200:**
```json
[
  {
    "id": "e5f6a7b8-...",
    "workoutGroupId": "c3d4e5f6-...",
    "workoutGroupTitle": "Push Day",
    "startedAt": "2025-06-01T09:00:00Z",
    "completedAt": "2025-06-01T10:15:00Z",
    "notes": "Good session",
    "setLogs": [],
    "setCount": 12,
    "exerciseCount": 3
  }
]
```

#### `GET /api/public/sessions/{id}`

Returns a completed WorkoutSession with all SetLogs. Returns 404 if the session does not belong to the consenting user.

```bash
curl -H "Authorization: Bearer $TOKEN" \
  https://your-satzwerk/api/public/sessions/e5f6a7b8-...
```

**Response 200:**
```json
{
  "id": "e5f6a7b8-...",
  "workoutGroupId": "c3d4e5f6-...",
  "workoutGroupTitle": "Push Day",
  "startedAt": "2025-06-01T09:00:00Z",
  "completedAt": "2025-06-01T10:15:00Z",
  "notes": "Good session",
  "setLogs": [
    {
      "id": "f6a7b8c9-...",
      "exerciseId": "a1b2c3d4-...",
      "setNumber": 1,
      "weight": 80.0,
      "reps": 8,
      "loggedAt": "2025-06-01T09:05:00Z"
    }
  ],
  "setCount": 12,
  "exerciseCount": 3
}
```

> **Note:** All weights are in **kg**.

---

### Analytics

#### `GET /api/public/analytics/heatmap`

Returns a daily set-count heatmap for the given date range.

| Query param | Default | Format |
|---|---|---|
| `from` | 3 months ago | `yyyy-MM-dd` |
| `to` | today | `yyyy-MM-dd` |

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "https://your-satzwerk/api/public/analytics/heatmap?from=2025-01-01&to=2025-01-07"
```

**Response 200:**
```json
[
  { "date": "2025-01-01", "count": 0, "intensity": 0 },
  { "date": "2025-01-02", "count": 12, "intensity": 3 },
  { "date": "2025-01-03", "count": 0, "intensity": 0 }
]
```

`intensity` is 0–10 (0 = rest day, 10 = maximum activity tier).

#### `GET /api/public/analytics/streak`

Returns current and longest training streaks in days.

```bash
curl -H "Authorization: Bearer $TOKEN" \
  https://your-satzwerk/api/public/analytics/streak
```

**Response 200:**
```json
{ "currentStreak": 5, "longestStreak": 14 }
```

#### `GET /api/public/analytics/summary`

Returns a dashboard summary of training activity.

```bash
curl -H "Authorization: Bearer $TOKEN" \
  https://your-satzwerk/api/public/analytics/summary
```

**Response 200:**
```json
{
  "currentStreak": 5,
  "longestStreak": 14,
  "sessionsThisMonth": 12,
  "setsThisWeek": 48,
  "totalSessions": 87,
  "prsThisMonth": 3,
  "activePlanDays": null,
  "avgSessionDurationMinutes": 62
}
```

#### `GET /api/public/analytics/weekly-trend`

Returns per-week set and session counts.

| Query param | Default | Min | Max |
|---|---|---|---|
| `weeks` | 8 | 1 | 52 |

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "https://your-satzwerk/api/public/analytics/weekly-trend?weeks=4"
```

**Response 200:**
```json
[
  { "week": "2025-W18", "setCount": 45, "sessionCount": 4 },
  { "week": "2025-W19", "setCount": 60, "sessionCount": 5 }
]
```

#### `GET /api/public/analytics/personal-records`

Returns recent personal records (heaviest weight × reps per exercise).

| Query param | Default | Min | Max |
|---|---|---|---|
| `limit` | 5 | 1 | 20 |

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "https://your-satzwerk/api/public/analytics/personal-records?limit=10"
```

**Response 200:**
```json
[
  {
    "exerciseId": "a1b2c3d4-...",
    "exerciseName": "Bench Press",
    "weightKg": 102.5,
    "reps": 3,
    "achievedAt": "2025-05-20T09:30:00Z"
  }
]
```

---

## Error responses

| Status | Meaning |
|---|---|
| `401 Unauthorized` | No credential, invalid token, or revoked token |
| `403 Forbidden` | Token valid but missing required scope — `error` field names the scope |
| `404 Not Found` | Resource not found or belongs to a different user |
| `400 Bad Request` | Invalid query parameter (e.g. malformed date) |

**Example 403 body:**
```json
{ "error": "Required scope: exercises:read", "message": "Required scope: exercises:read" }
```

---

## Validation checklist for external clients

Run these checks to confirm a new integration is working correctly:

```bash
BASE=https://your-satzwerk
TOKEN=<your-pat-or-partner-token>

# 1. Valid access — expect 200
curl -sf -H "Authorization: Bearer $TOKEN" $BASE/api/public/exercises && echo "✓ exercises"
curl -sf -H "Authorization: Bearer $TOKEN" $BASE/api/public/plans && echo "✓ plans"
curl -sf -H "Authorization: Bearer $TOKEN" $BASE/api/public/sessions/history && echo "✓ sessions"
curl -sf -H "Authorization: Bearer $TOKEN" $BASE/api/public/analytics/streak && echo "✓ analytics"

# 2. Missing credential — expect 401
curl -o /dev/null -w "%{http_code}" $BASE/api/public/exercises  # should print 401

# 3. Wrong scope — expect 403 (token must have exercises:read only)
WRONG_TOKEN=<pat-with-only-exercises:read>
curl -o /dev/null -w "%{http_code}" \
  -H "Authorization: Bearer $WRONG_TOKEN" $BASE/api/public/sessions/history  # should print 403

# 4. Cross-user isolation — both calls must return only your own data
curl -sf -H "Authorization: Bearer $TOKEN" $BASE/api/public/exercises | jq length
```
