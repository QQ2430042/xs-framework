# UC-001 Create user

Version: 1.0.0

## Actor

Administrator of the current tenant.

## Preconditions

- The caller is authenticated and the tenant is resolved from a signed JWT claim.
- The tenant context is present in `TenantContext`.

## Given / When / Then

```gherkin
Scenario: Create a user in the current tenant
  Given tenant "tenant-1" is active
  When  POST /api/v1/users is called with username "zhangsan" and displayName "张三"
  Then  the response code is "0"
  And   a st_user row exists with tenant_id = "tenant-1"
  And   user_code matches ^UC[0-9A-F]{14}$
  And   deleted_flag = false
  And   version = 0

Scenario: Tenant context is missing
  Given no tenant context is active
  When  POST /api/v1/users is called
  Then  the response code is "TENANT_REQUIRED"
  And   no row is written

Scenario: Business code collision within the same tenant
  Given a user with code "UC00000000000001" exists in tenant "tenant-1"
  When  another user with the same code is inserted
  Then  the unique index (tenant_id, user_code) rejects the write
```

## Out of scope

- Password, roles, and data scope are separate specifications.
- Cross-tenant user visibility is forbidden and covered by the RLS isolation test.
