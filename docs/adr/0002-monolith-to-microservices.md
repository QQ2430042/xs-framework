# ADR 0002: Monolith now, microservices without rewriting

Status: accepted

## Context

The base project must be packageable as a single monolith or as independent domain services. Cross-module JPA joins, shared transactions, or direct impl imports would make later splitting expensive.

## Decision

1. `xs-api-*` modules are pure Java contracts (records and interfaces) with no Spring, JPA, or web dependency.
2. `xs-module-*` implementations communicate only through `xs-api-*`. They never import another module's impl, entity, or repository.
3. Each assembly (`xs-app-monolith`, `xs-app-user`, future `xs-app-<domain>`) is a Spring Boot application that scans only the modules it needs.
4. Each domain owns its tables and Flyway scripts under `db/migration/common`; PostgreSQL RLS scripts live under `db/migration/pg` and are only enabled in PG profiles.
5. Domain events carry `tenant_id`. Async/in-process publishing in the monolith will later be replaced by a remote adapter implementing the same port.
6. ArchUnit gates in `xs-arch-tests` fail the build if boundaries are violated.

## Consequences

Splitting a service means adding an `xs-app-<domain>` module plus a remote adapter that implements the affected `xs-api-*` ports with HTTP Interface Clients or gRPC. Business code does not change.
