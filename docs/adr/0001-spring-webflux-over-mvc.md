# Use Spring WebFlux + R2DBC instead of Spring MVC + JPA

The previous KraftLog backend used Spring MVC + JPA (blocking stack). Satzwerk deliberately uses Spring WebFlux with coroutines and R2DBC instead. This is a learning-driven choice — the functional requirements (CRUD-heavy, low concurrency) do not demand reactive I/O, but the engineering goal is to build fluency with the reactive Kotlin/Spring stack. Future contributors should not "fix" this back to MVC; the complexity is intentional.

## Considered Options

- **Spring MVC + JPA** — simpler, battle-tested, existing team familiarity. Rejected because learning WebFlux is an explicit goal.
- **Spring WebFlux + R2DBC + Kotlin coroutines** — chosen. Adds complexity but delivers the learning outcome and keeps the door open for streaming features (live session updates, etc.) if needed later.
