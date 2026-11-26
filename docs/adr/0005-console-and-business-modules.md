# ADR 0005: One module family for the management console and business services

Status: accepted

## Context

The framework has two goals: deliver a tenant-aware microservice management console, and provide a reusable base for business microservices. Building the console as a privileged layer over business services would create a second codebase and make the monolith/microservice switch expensive. The existing `xs-api-*` / `xs-module-*` / `xs-app-*` pattern already supports one style of domain; the question is whether the console uses the same pattern.

## Decision

1. The console is a set of domains (`tenant`, `user`, `auth`, `authorization`, `audit`, `config`) built with the same `api-*` + `module-*` template as business domains.
2. The console is not a privileged layer. Business modules depend only on console `api-*` ports, never on console module implementations. Cross-domain calls use ports in both directions.
3. The same modules are assembled into either `xs-app-monolith` (all console + all business modules in one process) or standalone services (`xs-app-console`, `xs-app-<domain>`), with local vs remote adapters behind the same ports.
4. Tenant context is set from a verified JWT claim in production; the development `X-Tenant-Id` header is disabled in production profiles. Every domain event carries `tenant_id`.
5. Database ownership stays per domain in both modes. RLS is mandatory in production profiles.
6. `docs/spec/VISION.md` is the target-state description; this ADR records the structural decision. Current implementation status is explicitly listed there so the base is not mistaken for a finished console.

## Consequences

- Adding a console capability and adding a business capability follow the same procedure documented in `MODULE_CATALOG.md`; no special console-only extension mechanism is needed.
- Console ports such as tenant lookup and permission checks become remote calls in microservice mode, so they must be designed as coarse-grained reads from the start.
- The console cannot rely on cross-domain JPA joins or shared transactions with business modules; this is already forbidden by the ArchUnit gates.
- Until `auth`/`authorization` domains exist, production profiles remain non-usable; the current header-based tenant filter is development-only.
- The first real business domain must be delivered end to end to prove the base is genuinely reusable before scaling out console domains.
