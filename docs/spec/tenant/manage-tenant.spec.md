# TN-001 Manage tenant registry

Version: 1.0.0

## Actor

Management-console administrator (control plane), not a tenant user.

## Preconditions

- The caller is authenticated and authorized for control-plane operations.
- No tenant context is required: the tenant registry is global data (ADR 0006).

## Given / When / Then

```gherkin
Scenario: Register a new tenant
  Given the registry has no tenant with code "TN00000000000001"
  When  POST /api/v1/tenants is called with tenantName "Acme" and description "Acme Ltd"
  Then  the response code is "0"
  And   a st_tenant row exists with status "ACTIVE"
  And   tenant_code matches ^TN[0-9A-F]{14}$
  And   tenant_id is a 36-character UUID usable as X-Tenant-Id
  And   deleted_flag = false and version = 0

Scenario: List all tenants without tenant context
  Given at least one tenant is registered
  When  GET /api/v1/tenants is called without X-Tenant-Id
  Then  the response code is "0"
  And   the data array contains every non-deleted tenant

Scenario: Get a tenant by id
  Given tenant "tenant-a" is registered
  When  GET /api/v1/tenants/tenant-a is called
  Then  the response code is "0"
  And   data.tenantId = "tenant-a"

Scenario: Suspend a tenant
  Given tenant "tenant-a" is ACTIVE
  When  PATCH /api/v1/tenants/tenant-a/status is called with status "SUSPENDED"
  Then  the response code is "0"
  And   data.status = "SUSPENDED"

Scenario: Tenant code collision
  Given a tenant with code "TN00000000000001" exists
  When  the generated code collides on create
  Then  the response code is "TENANT_CODE_EXISTS"
  And   no row is written

Scenario: Tenant not found
  Given no tenant with id "missing" exists
  When  GET /api/v1/tenants/missing is called
  Then  the HTTP status is 404 and the response code is "NOT_FOUND"
```

## Out of scope

- Authentication and authorization of the administrator (auth / authorization domains).
- Tenant provisioning of databases, users or default roles.
- Business-data isolation of tenant-owned tables; that remains `@TenantId` + PG RLS on tables such as `st_user`.
