-- Reserved platform tenant. Cannot be disabled or cancelled by application rules.
-- Admin identities for this tenant are owned by P2/M0-04; this seed only creates the scope.

INSERT INTO platform.tenant (
    id, code, name, kind, status, legal_name,
    created_at, created_by, updated_at, updated_by, deleted_at, version
) VALUES (
    '01900000-0000-7000-8000-000000000001',
    'platform',
    'NexusOne Platform',
    'PLATFORM',
    'ENABLED',
    'NexusOne Platform',
    timestamptz '2026-01-01 00:00:00+00',
    '01900000-0000-7000-8000-000000000001',
    timestamptz '2026-01-01 00:00:00+00',
    '01900000-0000-7000-8000-000000000001',
    NULL,
    1
) ON CONFLICT (id) DO NOTHING;

INSERT INTO platform.tenant_config (
    tenant_id, display_name, locale, timezone, date_format, currency,
    fiscal_year_start_month, logo_file_id, favicon_file_id, updated_at, updated_by, version
) VALUES (
    '01900000-0000-7000-8000-000000000001',
    'NexusOne Platform',
    'zh-CN',
    'Asia/Shanghai',
    'yyyy-MM-dd',
    'CNY',
    1,
    NULL,
    NULL,
    timestamptz '2026-01-01 00:00:00+00',
    '01900000-0000-7000-8000-000000000001',
    1
) ON CONFLICT (tenant_id) DO NOTHING;

INSERT INTO platform.entitlement_projection (
    tenant_id, source, pack_code, pack_version, sku_code, subscription_id,
    projected_at, payload, updated_at, version
) VALUES (
    '01900000-0000-7000-8000-000000000001',
    'DEFAULT_PACK',
    'nexus.default.m0',
    '0.1.0',
    NULL,
    NULL,
    timestamptz '2026-01-01 00:00:00+00',
    '{
      "schema": "p4.runtime-entitlement.v1",
      "entitlements": {
        "apps": {"enabled": true, "quota": 5},
        "custom_ledgers": {"enabled": true, "quota": 10}
      }
    }'::jsonb,
    timestamptz '2026-01-01 00:00:00+00',
    1
) ON CONFLICT (tenant_id) DO NOTHING;

INSERT INTO platform.quota (
    tenant_id, metric, limit_value, used_value, warn_percent, over_limit_policy, updated_at, version
) VALUES
    ('01900000-0000-7000-8000-000000000001', 'APP', 5, 0, 80, 'REJECT', timestamptz '2026-01-01 00:00:00+00', 1),
    ('01900000-0000-7000-8000-000000000001', 'CUSTOM_LEDGER', 10, 0, 80, 'REJECT', timestamptz '2026-01-01 00:00:00+00', 1)
ON CONFLICT (tenant_id, metric) DO NOTHING;
