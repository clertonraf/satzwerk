# [BE+FE] Exercise Management

**Type:** AFK
**Blocked by:** #03 Authentication

## What to build

Per-user exercise catalog. Users create, edit, and delete their own `Exercise` entries. There is **no global shared catalog** — every exercise is owned by exactly one user (see CONTEXT.md).

### Backend

- `Exercise` entity: `id` (UUID), `userId`, `name`, `muscleGroup`, `description` (nullable), `videoUrl` (nullable), `equipment` (nullable)
- Flyway migration for `exercises` table
- Full CRUD:
  - `POST /api/exercises`
  - `GET /api/exercises` — returns only the authenticated user's exercises; supports optional `?muscleGroup=` filter
  - `GET /api/exercises/{id}`
  - `PATCH /api/exercises/{id}`
  - `DELETE /api/exercises/{id}`
- Ownership check on every mutation: 403 if the exercise belongs to a different user

### Frontend

- Exercise list page: grouped or filterable by muscle group
- Create / edit exercise form (React Hook Form + Zod): name (required), muscle group (required), description, video URL, equipment
- Delete with confirmation dialog
- React Query mutations with optimistic cache updates

## Acceptance criteria

- [ ] Authenticated user can create an exercise and it appears in their list
- [ ] Exercises from other users are never returned by `GET /api/exercises`
- [ ] Filtering by muscle group returns only matching exercises
- [ ] Editing an exercise updates all fields correctly
- [ ] Deleting an exercise removes it permanently; a confirmation is required
- [ ] Attempting to edit/delete another user's exercise returns 403
- [ ] All fields validated; name and muscleGroup are required
