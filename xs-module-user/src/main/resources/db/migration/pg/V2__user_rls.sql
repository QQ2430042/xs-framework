DROP INDEX IF EXISTS uk_st_user_code;
CREATE UNIQUE INDEX uk_st_user_code ON st_user (tenant_id, user_code) WHERE deleted_flag = FALSE;

ALTER TABLE st_user ENABLE ROW LEVEL SECURITY;
ALTER TABLE st_user FORCE ROW LEVEL SECURITY;

CREATE POLICY pl_st_user_tenant ON st_user
    USING (tenant_id = current_setting('app.tenant_id', true)::varchar(36))
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::varchar(36));
