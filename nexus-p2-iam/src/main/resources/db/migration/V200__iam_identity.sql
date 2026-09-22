-- M0-04 P2 identity / organization master data (Flyway window V200–V299).
-- Tables live in schema iam (M0-01 inter-module contract).
-- Tenant-scoped tables always have tenant_id NOT NULL.
-- iam.iam_global_account is platform-scoped (one Keycloak user per natural person).
-- Identity does not call Keycloak internal tables; Keycloak uses a separate database.

CREATE SCHEMA IF NOT EXISTS iam;

CREATE TABLE iam.iam_global_account (
    id                  UUID PRIMARY KEY,
    keycloak_user_id    VARCHAR(128) NOT NULL,
    email               VARCHAR(320) NOT NULL,
    phone               VARCHAR(32),
    username            VARCHAR(128) NOT NULL,
    display_name        VARCHAR(256) NOT NULL,
    status              VARCHAR(32) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    deleted_at          TIMESTAMPTZ,
    version             INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_iam_global_account_keycloak UNIQUE (keycloak_user_id),
    CONSTRAINT uk_iam_global_account_email UNIQUE (email)
);

CREATE TABLE iam.iam_tenant_membership (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    account_id          UUID NOT NULL REFERENCES iam.iam_global_account (id),
    status              VARCHAR(32) NOT NULL,
    joined_at           TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    deleted_at          TIMESTAMPTZ,
    version             INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_iam_membership_tenant_account UNIQUE (tenant_id, account_id)
);

CREATE INDEX idx_iam_membership_account ON iam.iam_tenant_membership (account_id);
CREATE INDEX idx_iam_membership_tenant ON iam.iam_tenant_membership (tenant_id);

CREATE TABLE iam.iam_person (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    account_id          UUID NOT NULL REFERENCES iam.iam_global_account (id),
    display_name        VARCHAR(256) NOT NULL,
    employee_no         VARCHAR(64),
    email               VARCHAR(320),
    phone               VARCHAR(32),
    hired_on            DATE,
    status              VARCHAR(32) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    deleted_at          TIMESTAMPTZ,
    version             INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_iam_person_tenant_account UNIQUE (tenant_id, account_id)
);

CREATE UNIQUE INDEX uk_iam_person_employee_no
    ON iam.iam_person (tenant_id, employee_no)
    WHERE employee_no IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX idx_iam_person_tenant ON iam.iam_person (tenant_id);

CREATE TABLE iam.iam_department (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    parent_id           UUID REFERENCES iam.iam_department (id),
    code                VARCHAR(64) NOT NULL,
    name                VARCHAR(256) NOT NULL,
    path                VARCHAR(2048) NOT NULL,
    sort_order          INTEGER NOT NULL DEFAULT 0,
    leader_person_id    UUID,
    status              VARCHAR(32) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    deleted_at          TIMESTAMPTZ,
    version             INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_iam_department_code UNIQUE (tenant_id, code)
);

CREATE INDEX idx_iam_department_tenant ON iam.iam_department (tenant_id);
CREATE INDEX idx_iam_department_parent ON iam.iam_department (tenant_id, parent_id);

CREATE TABLE iam.iam_position (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    code                VARCHAR(64) NOT NULL,
    name                VARCHAR(256) NOT NULL,
    category            VARCHAR(64),
    duties              VARCHAR(1024),
    status              VARCHAR(32) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    deleted_at          TIMESTAMPTZ,
    version             INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_iam_position_code UNIQUE (tenant_id, code)
);

CREATE INDEX idx_iam_position_tenant ON iam.iam_position (tenant_id);

CREATE TABLE iam.iam_employment (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    person_id           UUID NOT NULL REFERENCES iam.iam_person (id),
    department_id       UUID NOT NULL REFERENCES iam.iam_department (id),
    position_id         UUID NOT NULL REFERENCES iam.iam_position (id),
    kind                VARCHAR(32) NOT NULL,
    status              VARCHAR(32) NOT NULL,
    valid_from          DATE NOT NULL,
    valid_to            DATE,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    deleted_at          TIMESTAMPTZ,
    version             INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_iam_employment_tenant ON iam.iam_employment (tenant_id);
CREATE INDEX idx_iam_employment_person ON iam.iam_employment (tenant_id, person_id);

CREATE TABLE iam.iam_issued_context (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    account_id          UUID NOT NULL REFERENCES iam.iam_global_account (id),
    person_id           UUID NOT NULL REFERENCES iam.iam_person (id),
    idp_subject         VARCHAR(128) NOT NULL,
    issued_at           TIMESTAMPTZ NOT NULL,
    expires_at          TIMESTAMPTZ NOT NULL,
    revoked_at          TIMESTAMPTZ
);

CREATE INDEX idx_iam_issued_context_account ON iam.iam_issued_context (account_id);
CREATE INDEX idx_iam_issued_context_tenant ON iam.iam_issued_context (tenant_id);
