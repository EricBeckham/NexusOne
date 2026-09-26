-- P2 remaining: tenant self-registration applications + market activation applications.
-- outOfOrder remains off; next global version after V705.

CREATE TABLE platform.tenant_registration_application (
    id                   uuid PRIMARY KEY,
    org_name             varchar(200) NOT NULL,
    admin_display_name   varchar(200) NOT NULL,
    admin_email          varchar(320) NOT NULL,
    contact_note         text,
    status               varchar(16) NOT NULL,
    review_note          text,
    reviewed_at          timestamptz,
    reviewed_by          uuid,
    resulting_tenant_id  uuid REFERENCES platform.tenant (id),
    created_at           timestamptz NOT NULL,
    CONSTRAINT p1_tenant_reg_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX p1_tenant_reg_status_idx
    ON platform.tenant_registration_application (status, created_at DESC);

CREATE TABLE market.activation_application (
    id                          uuid PRIMARY KEY,
    tenant_id                   uuid NOT NULL REFERENCES platform.tenant (id),
    sku_code                    varchar(80) NOT NULL REFERENCES market.sku (code),
    version_id                  uuid NOT NULL REFERENCES market.app_version (id),
    status                      varchar(16) NOT NULL,
    review_note                 text,
    reviewed_at                 timestamptz,
    reviewed_by                 uuid,
    resulting_subscription_id   uuid,
    resulting_installation_id   uuid,
    created_at                  timestamptz NOT NULL,
    created_by                  uuid NOT NULL,
    CONSTRAINT p4_activation_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX p4_activation_status_idx
    ON market.activation_application (status, created_at DESC);

CREATE INDEX p4_activation_tenant_idx
    ON market.activation_application (tenant_id, created_at DESC);

CREATE UNIQUE INDEX p4_activation_pending_uq
    ON market.activation_application (tenant_id, sku_code)
    WHERE status = 'PENDING';