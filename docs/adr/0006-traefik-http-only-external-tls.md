# ADR-0006: Traefik stays HTTP-only; TLS termination is external

## Status

Accepted

## Context

Issue #285 keeps Traefik in this Docker Compose stack so it can load-balance HTTP
requests across multiple `backend` replicas. An older HTTPS/ACME setup was removed,
but some documentation and sample environment variables still implied that this repo
provisions public TLS on its own.

That implication is wrong for the current deployment model. This repository's
Compose stack is for local development and self-hosted deployments where HTTPS, if
needed, can be terminated by infrastructure in front of the stack.

## Decision

- Traefik in this repository remains **HTTP-only**.
- Traefik is used only for routing and load-balancing HTTP traffic across backend
  replicas in the Compose stack.
- TLS termination is deferred and is **not** reintroduced as part of issue #290.
- Production deployments that need HTTPS must place an external TLS-terminating
  reverse proxy or managed load balancer in front of this stack.
- Sample environment/config documentation in this repo must not imply built-in ACME
  or certificate management.

## Consequences

- The sample environment file no longer documents unused HTTPS-related variables.
- README guidance must describe this stack as HTTP-only and require external TLS for
  public production use.
- If Satzwerk later gains first-class in-repo TLS termination again, that work
  should land in a separate issue and ADR instead of reviving stale configuration.
