# [BE+FE] User Authentication

**Type:** AFK
**Blocked by:** #01 Backend Bootstrap, #02 Frontend Bootstrap

## What to build

End-to-end authentication: user registration, login, and access-token refresh. A user can create an account, log in, and have their session maintained via JWT + rotating refresh tokens. Unauthenticated users are redirected to the login page.

Password reset is **excluded from MVP** (see ADR).

### Backend

- `POST /api/auth/register` — create user (email, password, displayName); password hashed with BCrypt
- `POST /api/auth/login` — returns `{ accessToken, refreshToken }`
- `POST /api/auth/refresh` — rotates refresh token, returns new pair
- Spring Security filter chain: validate JWT on every request; reject with 401 on invalid/expired token
- `users` and `refresh_tokens` tables added via Flyway migration
- Jakarta Validation on all request bodies

### Frontend

- `/register` and `/login` pages (React Hook Form + Zod validation)
- On login success: store `accessToken` in memory (Zustand), `refreshToken` in `httpOnly` cookie or `localStorage` (choose consistently)
- Axios (or `fetch`) interceptor that transparently calls `/auth/refresh` on 401 and retries the original request
- `ProtectedRoute` wrapper that redirects unauthenticated users to `/login`
- Logout clears tokens and redirects

## Acceptance criteria

- [ ] A new user can register with email + password + display name
- [ ] Registering with a duplicate email returns a 409 with a clear error message
- [ ] A registered user can log in and receive a valid JWT pair
- [ ] Accessing a protected route without a token redirects to `/login`
- [ ] Access token expiry triggers a silent refresh; the user is not logged out
- [ ] Logging out invalidates the refresh token server-side
- [ ] Passwords are never stored or logged in plaintext
- [ ] All inputs validated; invalid payloads return 400 with field-level errors
