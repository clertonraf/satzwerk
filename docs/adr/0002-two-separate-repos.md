# Frontend and backend live in separate repositories

The React PWA and the Spring Boot API are maintained in two separate Git repositories rather than a monorepo. This matches the team's existing workflow and keeps deployment pipelines, versioning, and issue tracking cleanly separated per service. The Docker Compose file that wires them together at deployment time lives in the backend repo.

## Considered Options

- **Monorepo** — simpler cross-cutting changes, one CI/CD pipeline, shared types. Rejected: the team prefers per-service repos and the two codebases share no source files.
- **Two separate repos** — chosen. Each repo has independent CI, versioning, and ownership.
