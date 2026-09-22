-- M0-05 P2 permission static tables (Flyway window V300–V399).
-- Live in schema iam: P2 is the permission policy source of truth.
-- Tenant-scoped tables always have tenant_id UUID NOT NULL.
-- Policy version is the authoritative snapshot clock; Redis only accelerates invalidation.

CREATE SCHEMA IF NOT EXISTS iam;

CREATE TABLE iam.perm_policy_version (
    tenant_id           UUID PRIMARY KEY,
    policy_version      BIGINT NOT NULL CHECK (policy_version >= 1),
    updated_at          TIMESTAMPTZ NOT NULL
);

CREATE TABLE iam.perm_role (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    code                VARCHAR(64) NOT NULL,
    name                VARCHAR(256) NOT NULL,
    system_role         BOOLEAN NOT NULL DEFAULT FALSE,
    status              VARCHAR(32) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    deleted_at          TIMESTAMPTZ,
    version             INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_perm_role_code UNIQUE (tenant_id, code)
);

CREATE INDEX idx_perm_role_tenant ON iam.perm_role (tenant_id);

CREATE TABLE iam.perm_role_parent (
    tenant_id           UUID NOT NULL,
    child_role_id       UUID NOT NULL REFERENCES iam.perm_role (id),
    parent_role_id      UUID NOT NULL REFERENCES iam.perm_role (id),
    PRIMARY KEY (tenant_id, child_role_id, parent_role_id)
);

CREATE TABLE iam.perm_assignment (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    principal_type      VARCHAR(16) NOT NULL,
    principal_id        VARCHAR(64) NOT NULL,
    role_id             UUID NOT NULL REFERENCES iam.perm_role (id),
    created_at          TIMESTAMPTZ NOT NULL,
    deleted_at          TIMESTAMPTZ
);

CREATE INDEX idx_perm_assignment_tenant ON iam.perm_assignment (tenant_id);
CREATE INDEX idx_perm_assignment_principal ON iam.perm_assignment (tenant_id, principal_type, principal_id);

CREATE TABLE iam.perm_grant (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    effect              VARCHAR(8) NOT NULL,
    principal_type      VARCHAR(16) NOT NULL,
    principal_id        VARCHAR(64) NOT NULL,
    resource            VARCHAR(128) NOT NULL,
    action              VARCHAR(64) NOT NULL,
    scope_kind          VARCHAR(32) NOT NULL,
    specified_depts     TEXT,
    created_at          TIMESTAMPTZ NOT NULL,
    deleted_at          TIMESTAMPTZ,
    CONSTRAINT ck_perm_grant_effect CHECK (effect IN ('GRANT', 'DENY'))
);

CREATE INDEX idx_perm_grant_tenant ON iam.perm_grant (tenant_id);

CREATE TABLE iam.perm_field_policy (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    grant_id            UUID NOT NULL REFERENCES iam.perm_grant (id),
    field_name          VARCHAR(64) NOT NULL,
    visible             BOOLEAN NOT NULL,
    editable            BOOLEAN NOT NULL,
    mask                VARCHAR(16) NOT NULL,
    second_authorization BOOLEAN NOT NULL
);

CREATE INDEX idx_perm_field_tenant ON iam.perm_field_policy (tenant_id);

CREATE TABLE iam.perm_record_share (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    resource            VARCHAR(128) NOT NULL,
    record_id           VARCHAR(64) NOT NULL,
    principal_type      VARCHAR(16) NOT NULL,
    principal_id        VARCHAR(64) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    expires_at          TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ
);

CREATE INDEX idx_perm_share_tenant ON iam.perm_record_share (tenant_id);
