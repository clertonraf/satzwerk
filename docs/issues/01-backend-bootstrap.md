# [BE] Backend Bootstrap

**Type:** AFK
**Blocked by:** None — can start immediately

## What to build

Scaffold the Spring Boot WebFlux backend from scratch. The result is a running, deployable Spring Boot application with all infrastructure wired up but no business logic yet — a clean base for every subsequent backend slice.

- Gradle project with Kotlin DSL (`build.gradle.kts`)
- Spring Boot WebFlux + Spring Security stub (permits all, to be locked down in #03)
- Spring Data R2DBC connected to PostgreSQL
- Flyway for schema migrations (initial baseline migration only)
- JWT config loaded from environment variables (secret, expiry) — no endpoints yet
- Spring Boot Actuator health endpoint (`/actuator/health`)
- `application.yml` with profiles: `local`, `prod`
- ktlint + detekt configured
- JUnit 5 + Testcontainers wired for integration tests against a real PostgreSQL container

Package structure follows the modular layout from the architecture plan:

```
src/main/kotlin/
 ├── config/
 ├── auth/
 ├── users/
 ├── workouts/
 ├── sessions/
 ├── analytics/
 ├── common/
 └── infrastructure/
```

## Acceptance criteria

- [ ] `./gradlew bootRun` starts the app and connects to a local PostgreSQL instance
- [ ] `GET /actuator/health` returns `{"status":"UP"}`
- [ ] Flyway applies the baseline migration on startup without errors
- [ ] `./gradlew test` passes with at least one Testcontainers smoke test verifying DB connectivity
- [ ] ktlint and detekt pass with zero violations
- [ ] No business logic, no auth endpoints — infrastructure only
