# ADR 0004: CI gates as executable SDD enforcement

Status: accepted

## Context

The SDD workflow produces OpenAPI contracts, GWT specifications and ADRs, but documents alone drift from code. CI must prove that the three artifacts stay aligned whenever a pull request changes the base framework.

## Decision

1. One local-and-CI entry point: `scripts/ci/verify.sh`; GitHub Actions calls it from `.github/workflows/ci.yml`.
2. P0 gates block merge: JDK 25, Maven 3.9+, dependency convergence, dependency baseline, `mvn clean verify`, OpenAPI lint on both authored and runtime contracts, spec/runtime coverage and runtime contract drift.
3. P1 items are reported but do not block yet: upper-bound review, secret scan, OWASP dependency-check, PostgreSQL RLS six-path test and Docker smoke. A lightweight regex secret scan is wired into `verify.sh` in observation mode; `STRICT_SECRETS=true` promotes it to a blocker. Runtime OpenAPI lint was promoted to P0 on 2026-11-25 after the generated contract was brought up to the same ruleset.
4. `requireUpperBoundDeps` stays off because the Boot 4.2.0 BOM intentionally pins versions below what springdoc requests. Overriding them manually would violate the version-baseline rule in ADR 0001.
5. Dependency baseline changes require a new ADR plus `UPDATE_DEPENDENCY_BASELINE=true` run locally; CI refuses to update the baseline.
6. Waivers are human-controlled through branch protection and an expiring ADR, never through CI skip flags.

## Consequences

- Any contract change must update both `docs/spec/openapi/user-api.yaml` and the runtime export under `docs/api/`. Runtime metadata comes from Controller `@Operation` / `@ApiResponses` / `@Parameter` and app-level `@OpenAPIDefinition`; omitting them now fails CI.
- Any dependency change must update `xs-dependencies`, the baseline and an ADR in the same PR.
- Architecture boundary changes must update `ARCHITECTURE.md` and the ArchUnit rules together.
- The gate set is intentionally small and deterministic; heavier security/database gates can be promoted after their environments are stable.
