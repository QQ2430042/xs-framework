-- st_tenant is control-plane global data, not tenant-owned (ADR 0006).
-- It is intentionally NOT covered by a tenant RLS policy: the management console
-- must list and administer all tenants. Access is gated by authz at the API layer.
-- Tenant-owned tables (for example st_user) keep their RLS policies in their own migrations.
ALTER TABLE st_tenant ENABLE ROW LEVEL SECURITY;
ALTER TABLE st_tenant FORCE ROW LEVEL SECURITY;

-- Console/admin role sees the whole registry; tenant application roles see nothing here.
-- The role is created idempotently so a fresh database migrates cleanly. Granting it to
-- the console service login is a deployment step, to be verified under W04 on real PG19.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'xs_console') THEN
    CREATE ROLE xs_console NOLOGIN;
  END IF;
END
$$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname = current_schema() AND tablename = 'st_tenant' AND policyname = 'pl_st_tenant_console') THEN
    CREATE POLICY pl_st_tenant_console ON st_tenant
      FOR ALL TO xs_console
      USING (true)
      WITH CHECK (true);
  END IF;
END
$$;
