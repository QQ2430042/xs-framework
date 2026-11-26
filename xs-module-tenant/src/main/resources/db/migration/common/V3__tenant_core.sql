CREATE TABLE st_tenant (
    tenant_id    VARCHAR(36)  NOT NULL PRIMARY KEY,
    tenant_code  VARCHAR(16)  NOT NULL,
    tenant_name  VARCHAR(128) NOT NULL,
    description  VARCHAR(255),
    status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    version      BIGINT       NOT NULL DEFAULT 0,
    created_by   VARCHAR(36),
    created_at   TIMESTAMP,
    updated_by   VARCHAR(36),
    updated_at   TIMESTAMP,
    deleted_by   VARCHAR(36),
    deleted_at   TIMESTAMP,
    deleted_flag BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX uk_st_tenant_code ON st_tenant (tenant_code);
