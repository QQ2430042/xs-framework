# ADR 0003: Events and remote adapters

Status: accepted (HTTP/JSON selected as the only remote adapter path; gRPC rejected)

## Context

When a domain is split into its own service, in-process calls between modules must become remote calls without changing business code. Domain events must survive the split and carry tenant identity.

## Decision

- Business code only knows `xs-api-*` ports. In local mode (default), Spring injects the domain's own service.
- In remote mode (`xs.adapters.user.mode=remote`), a `RemoteUserPort` in `xs-adapter-user-http` implements the same port using a Spring HTTP Interface Client (`@HttpExchange` + `RestClient`) over HTTP/JSON. Callers are unchanged; the local `UserService` steps aside via `@ConditionalOnProperty`.
- The adapter propagates the tenant via `X-Tenant-Id` on every outgoing request using `TenantHeaderInterceptor`, so RLS on the upstream service sees the correct tenant.
- gRPC is deliberately not introduced. The local benchmark (`docs/guides/PROTOCOL_BENCHMARK.md`, 10 samples at concurrency 4/16, 5 at 64) produced a gRPC/HTTP throughput ratio of 0.78–0.82 and P99 ratios below 1, so gRPC brought neither throughput nor tail-latency gains on the target workload. Revisiting it requires a new ADR with evidence from the Java/Spring stack.
- Every domain event implements `DomainEvent` and carries `eventId`, `tenantId`, and `occurredAt`.
- An outbox table per domain will be added before asynchronous events are enabled in production.
- Native SQL and JDBC report code must explicitly carry `tenant_id`; they are never covered by Hibernate `@TenantId`.

## Consequences

The outbox writer is still not implemented and must be covered by the six-path tenant isolation test (REST, JPQL, native query, JdbcClient, batch job, cache) before production use. The HTTP remote adapter for `UserPort` **is** implemented and verified: bean-selection tests prove the local/remote switch, and a two-process demo proved that a POST on the remote-mode app lands in the upstream service's database with the caller's tenant, while a different tenant gets 404.

Choosing HTTP/JSON avoids a `.proto` contract, generated stubs, and a second contract-gate pipeline. The existing authored/runtime OpenAPI and G05–G07 drift gates remain the single source of truth for remote contracts. The adapter's `RemoteResponse` mirrors the `ApiResponse` wire shape locally rather than depending on `xs-starter-web`.
