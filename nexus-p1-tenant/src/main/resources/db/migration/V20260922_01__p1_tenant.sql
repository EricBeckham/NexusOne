-- P1 platform-static schema. Flyway only; P3 dynamic ledger tables are out of scope.
-- All tables in this file are scope=platform (see classpath:p1-table-registry.json).
-- M0-02 table_registry should import that catalog; this module does not own isolation internals.

CREATE SCHEMA IF NOT EXISTS p1;

CREATE TABLE p1.tenant (
    id              uuid PRIMARY KEY,
    code            varchar(24) NOT NULL,
    name            varchar(200) NOT NULL,
    kind            varchar(16) NOT NULL,
    status          varchar(16) NOT NULL,
    legal_name      varchar(200),
    created_at      timestamptz NOT NULL,
    created_by      uuid NOT NULL,
    updated_at      timestamptz NOT NULL,
    updated_by      uuid NOT NULL,
    deleted_at      timestamptz,
    version         bigint NOT NULL DEFAULT 1,
    CONSTRAINT p1_tenant_kind CHECK (kind IN ('PLATFORM', 'BUSINESS')),
    CONSTRAINT p1_tenant_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT p1_tenant_code_format CHECK (code ~ '^[a-z][a-z0-9_]{0,23}$'),
    CONSTRAINT p1_tenant_platform_code CHECK (
        (kind = 'PLATFORM' AND code = 'platform')
        OR (kind = 'BUSINESS' AND code <> 'platform')
    )
);

CREATE UNIQUE INDEX p1_tenant_code_uq ON p1.tenant (code);
CREATE UNIQUE INDEX p1_tenant_one_platform ON p1.tenant (kind) WHERE kind = 'PLATFORM' AND deleted_at IS NULL;

CREATE TABLE p1.tenant_config (
    tenant_id                 uuid PRIMARY KEY REFERENCES p1.tenant (id),
    display_name              varchar(200) NOT NULL,
    locale                    varchar(16) NOT NULL,
    timezone                  varchar(64) NOT NULL,
    date_format               varchar(32) NOT NULL,
    currency                  varchar(3) NOT NULL,
    fiscal_year_start_month   smallint NOT NULL,
    logo_file_id              uuid,
    favicon_file_id           uuid,
    updated_at                timestamptz NOT NULL,
    updated_by                uuid NOT NULL,
    version                   bigint NOT NULL DEFAULT 1,
    CONSTRAINT p1_tenant_config_fy CHECK (fiscal_year_start_month BETWEEN 1 AND 12),
    CONSTRAINT p1_tenant_config_currency CHECK (currency ~ '^[A-Z]{3}$')
);

-- Runtime entitlement projection. Same row shape P4 will later overwrite (source=SUBSCRIPTION).
-- P1 does not store commercial Product/SKU definitions.
CREATE TABLE p1.entitlement_projection (
    tenant_id         uuid PRIMARY KEY REFERENCES p1.tenant (id),
    source            varchar(32) NOT NULL,
    pack_code         varchar(64) NOT NULL,
    pack_version      varchar(32) NOT NULL,
    sku_code          varchar(64),
    subscription_id   uuid,
    projected_at      timestamptz NOT NULL,
    payload           jsonb NOT NULL,
    updated_at        timestamptz NOT NULL,
    version           bigint NOT NULL DEFAULT 1,
    CONSTRAINT p1_entitlement_source CHECK (source IN ('DEFAULT_PACK', 'SUBSCRIPTION')),
    CONSTRAINT p1_entitlement_sub CHECK (
        (source = 'DEFAULT_PACK' AND subscription_id IS NULL AND sku_code IS NULL)
        OR (source = 'SUBSCRIPTION' AND subscription_id IS NOT NULL)
    )
);

CREATE TABLE p1.quota (
    tenant_id           uuid NOT NULL REFERENCES p1.tenant (id),
    metric              varchar(32) NOT NULL,
    limit_value         integer NOT NULL,
    used_value          integer NOT NULL DEFAULT 0,
    warn_percent        smallint NOT NULL,
    over_limit_policy   varchar(16) NOT NULL,
    updated_at          timestamptz NOT NULL,
    version             bigint NOT NULL DEFAULT 1,
    PRIMARY KEY (tenant_id, metric),
    CONSTRAINT p1_quota_metric CHECK (metric IN ('APP', 'CUSTOM_LEDGER')),
    CONSTRAINT p1_quota_limit CHECK (limit_value >= 0),
    CONSTRAINT p1_quota_used CHECK (used_value >= 0),
    CONSTRAINT p1_quota_warn CHECK (warn_percent BETWEEN 1 AND 100),
    CONSTRAINT p1_quota_policy CHECK (over_limit_policy IN ('REJECT'))
);

CREATE TABLE p1.quota_idempotency (
    tenant_id         uuid NOT NULL,
    metric            varchar(32) NOT NULL,
    idempotency_key   varchar(128) NOT NULL,
    amount            integer NOT NULL,
    outcome           varchar(16) NOT NULL,
    used_after        integer,
    limit_value       integer,
    created_at        timestamptz NOT NULL,
    PRIMARY KEY (tenant_id, metric, idempotency_key),
    CONSTRAINT p1_quota_idemp_outcome CHECK (outcome IN ('ACCEPTED', 'REJECTED')),
    CONSTRAINT p1_quota_idemp_fk FOREIGN KEY (tenant_id, metric) REFERENCES p1.quota (tenant_id, metric)
);

CREATE TABLE p1.admin_init_request (
    id                    uuid PRIMARY KEY,
    tenant_id             uuid NOT NULL REFERENCES p1.tenant (id),
    admin_display_name    varchar(200) NOT NULL,
    admin_email           varchar(320) NOT NULL,
    status                varchar(16) NOT NULL,
    message               text,
    actor_subject_id      uuid NOT NULL,
    trace_id              varchar(64) NOT NULL,
    created_at            timestamptz NOT NULL,
    CONSTRAINT p1_admin_init_status CHECK (status IN ('ACCEPTED', 'COMPLETED', 'FAILED'))
);

CREATE INDEX p1_admin_init_tenant_idx ON p1.admin_init_request (tenant_id, created_at DESC);

-- Local staging store until M0-11 owns append-only hash-chained audit.
-- No UPDATE/DELETE from application code. Hash chain is out of scope for M0-03.
CREATE TABLE p1.audit_event (
    event_id            uuid PRIMARY KEY,
    event_type          varchar(64) NOT NULL,
    event_version       integer NOT NULL,
    occurred_at         timestamptz NOT NULL,
    tenant_id           uuid NOT NULL,
    actor_subject_id    uuid,
    actor_tenant_id     uuid,
    platform_context    boolean NOT NULL,
    reason              varchar(200),
    trace_id            varchar(64) NOT NULL,
    correlation_id      varchar(64),
    causation_id        varchar(64),
    outcome             varchar(16) NOT NULL,
    resource_type       varchar(64) NOT NULL,
    resource_id         varchar(64),
    details             jsonb NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT p1_audit_outcome CHECK (outcome IN ('ACCEPTED', 'REJECTED'))
);

CREATE INDEX p1_audit_tenant_idx ON p1.audit_event (tenant_id, occurred_at DESC);
CREATE INDEX p1_audit_type_idx ON p1.audit_event (event_type, occurred_at DESC);

REVOKE UPDATE, DELETE ON p1.audit_event FROM PUBLIC;
