# Architecture specification

- Architecture vision and goal: `VISION.md`
- Module inventory and responsibilities: `MODULE_CATALOG.md`

## Dependency direction

```text
app.<assembly>  ->  module.<domain>  ->  api.<domain>  ->  common
                         |                  |
                         +-> starter.* ------+
```

- `api.*` must not depend on Spring, Jakarta, JPA, or any starter.
- `common` must not depend on any framework.
- `module.*` must not depend on another `module.*`.
- `app.*` assemblies must not depend on each other.

## Tenancy and data ownership

- Every business table has `tenant_id`, 36-character UUID primary key, 16-character business code, audit columns, `version`, and `deleted_flag`.
- Hibernate `@TenantId` handles JPQL/Criteria/derived queries. Native SQL is not covered by it and is forbidden by default.
- PostgreSQL RLS is the second layer and must be enabled in production profiles. The application database role must not own tables and must not have BYPASSRLS.
- Cross-domain reads go through `api.*` ports, never through joins.

## Packaging

- `mvn -pl xs-app-monolith -am package` builds the monolith jar.
- `mvn -pl xs-app-user -am package` builds the user-service jar.
- Both use the same modules and only differ in assembly and configuration.

## Specification workflow

1. Write or update `docs/spec/<domain>/*.spec.md` in Given/When/Then form.
2. Update the OpenAPI yaml under `docs/spec/openapi/` when the HTTP contract changes.
3. Record architectural decisions under `docs/adr/`.
4. Implement code and tests.
5. Run `bash scripts/ci/verify.sh` locally; CI runs the same P0 gates on every pull request and push to `main`.
6. See `docs/spec/CI_GATES.md` for the full gate matrix, P1 observations and waiver rules.
