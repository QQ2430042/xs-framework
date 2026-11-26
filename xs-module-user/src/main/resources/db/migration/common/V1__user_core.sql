CREATE TABLE st_user (
    user_id      VARCHAR(36)  NOT NULL PRIMARY KEY,
    tenant_id    VARCHAR(36)  NOT NULL,
    user_code    VARCHAR(16)  NOT NULL,
    username     VARCHAR(64)  NOT NULL,
    display_name VARCHAR(128),
    version      BIGINT       NOT NULL DEFAULT 0,
    created_by   VARCHAR(36),
    created_at   TIMESTAMP,
    updated_by   VARCHAR(36),
    updated_at   TIMESTAMP,
    deleted_by   VARCHAR(36),
    deleted_at   TIMESTAMP,
    deleted_flag BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX uk_st_user_code ON st_user (tenant_id, user_code);
