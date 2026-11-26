# Architecture vision

## Goal

Deliver a **tenant-aware microservice management console** and, at the same time, a **reusable base for business microservices**. Business capabilities are built as modules. The same modules are packaged either with the console into one monolith or deployed independently as microservices. Switching between the two is an assembly decision, not a rewrite.

## Two roles, one codebase

| Role | Nature | Typical modules | Deployment |
|---|---|---|---|
| Management console | Control plane | `tenant`, `user`, `auth`, `authorization`, `audit`, `config` | Bundled into the monolith, or shipped as its own console service |
| Business service | Data plane | `order`, `product`, `billing`, ... | Bundled into the monolith, or one `xs-app-<domain>` per domain |

Both roles are built from the same module template (`api-*` + `module-*` + optional `app-*`). The console is not a privileged layer above business modules; it is a set of domains that happens to manage tenants and access. Business modules never import a console module implementation — they call its `api-*` ports, exactly as they would call any other domain.

## Packaging modes

```text
Monolith (development / small deployment)
  xs-app-monolith
    + console modules:  tenant, user, auth, authorization, audit, config
    + business modules: order, product, billing, ...
    + one shared database, one schema, st_/sv_ prefixes (RLS on tenant_id)

Microservices (scale-out)
  xs-app-console   -> console modules only
  xs-app-order     -> order module + remote adapters for console ports
  xs-app-product   -> product module + remote adapters for console ports
  ...              -> one process per domain, each owning its tables
```

Rules that make both modes possible:

1. `xs-api-*` is pure Java. No Spring, Jakarta, JPA, or web dependency. It is the seam that stays intact when a call becomes remote.
2. `xs-module-*` communicates only through `api-*`. No cross-module entity, repository, or impl import.
3. A port has two adapter flavours: a local bean inside the monolith, and a remote adapter (Spring HTTP Interface Client over HTTP/JSON) inside a standalone service. The module code is identical. gRPC is deliberately not adopted (ADR 0003).
4. Each domain owns its tables and Flyway scripts. Cross-domain reads go through ports, never through joins.
5. `tenant_id` is on every business table; Hibernate `@TenantId` plus PostgreSQL RLS enforce isolation in both modes.
6. Domain events carry `tenant_id`; in-process publishing in the monolith is replaced by an outbox-driven broker in microservices.

## Tenant context propagation

- In-process: `TenantContext` ThreadLocal, set by `TenantContextFilter` from the `X-Tenant-Id` header (development) or from a verified JWT claim (production).
- Cross-service: propagate the tenant identifier in the HTTP request header; the receiving service re-establishes `TenantContext`. gRPC is not adopted (ADR 0003).
- Async: `DomainEvent` carries `tenant_id`; consumers open their context explicitly. No implicit ThreadLocal inheritance.

The production profile disables header trust (`xs.tenant.allow-header=false`) and enables RLS (`xs.tenant.rls-enabled=true`).

## Current state vs the goal

| Capability | Status |
|---|---|
| Shared base: `common`, `api`, `starter-web`, `starter-data` | Done |
| Vertical slices: `user` and `tenant` modules + monolith/user/tenant assemblies | Done |
| Layer boundaries enforced by ArchUnit, CI gates G01-G07 | Done |
| Tenant context + Hibernate multi-tenancy + PG RLS scripts | Skeleton done; RLS not verified on a real PG19 instance |
| Console domain: `tenant` (control-plane registry) | Done: create/list/get/status-change, global registry, `GlobalEntity`, Flyway V3/V4 |
| Console domain: `auth` / `authorization` (JWT, permission points, deny by default) | Not started; production profile is not usable yet |
| Console domain: `audit` / `config` | Not started |
| Any business domain (`order`, `product`, ...) | Not started |
| Remote adapter for `UserPort` | Done: HTTP/JSON declarative client (`@HttpExchange` + `RestClient`), tenant header propagation, local/remote bean switch; other domains can follow the same template |
| Outbox + async event delivery | Not started (ADR 0003 defines the approach) |
| TraceId / MDC / observability | `ApiResponse.traceId` placeholder only |
| Console UI | Not started |

The framework is a **verified base plus one sample domain**, not yet a complete console. Treat the goal above as the target state and grow into it domain by domain.

## Order of work

1. Finish the control plane: `tenant` is done; next `auth` + `authorization`, then `audit` / `config`.
2. Add one real business domain end to end (e.g. `order`) using the same vertical-slice template, proving the base is reusable rather than console-specific.
3. Add remote adapters and outbox, then split `xs-app-console` / `xs-app-order` and verify with real PostgreSQL 19.
4. Add observability and the console UI last, once the domain contracts are stable.

Each step must keep `bash scripts/ci/verify.sh` green.

## Decided calibers

The five boundary-changing questions are now settled in `ADR 0006`:

| # | 口径 | 决定 |
|---|---|---|
| 1 | 租户身份来源 | Dev: `X-Tenant-Id` header. Prod: JWT claim issued by the internal `auth` domain; external IdP federation later |
| 2 | 控制台范围 | `tenant` / `user` / `auth` / `authorization` / `audit` / `config` only; registry, config distribution and gateway rules stay outside this repo |
| 3 | 管理台 UI | Separate frontend consuming OpenAPI; no `admin-ui` module here |
| 4 | 单体数据库拓扑 | One database, one schema, `st_` / `sv_` prefixes; no schema-per-domain |
| 5 | 首个业务域 | `order` |

Remote calls are HTTP/JSON only; gRPC was benchmarked and rejected in `ADR 0003`.

See `ADR 0005` for the console/business module record, `MODULE_CATALOG.md` for the current module inventory, and `CI_GATES.md` for the enforced gates.
