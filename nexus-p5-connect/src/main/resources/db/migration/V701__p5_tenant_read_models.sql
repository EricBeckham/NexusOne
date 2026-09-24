ALTER TABLE integration.api_client
    ADD COLUMN tenant_id uuid;
ALTER TABLE integration.api_idempotency
    ADD COLUMN tenant_id uuid;
ALTER TABLE integration.webhook_subscription
    ADD COLUMN tenant_id uuid;
ALTER TABLE integration.webhook_delivery
    ADD COLUMN tenant_id uuid;
ALTER TABLE integration.connector_run
    ADD COLUMN tenant_id uuid;

UPDATE integration.api_client
SET tenant_id = '01900000-0000-7000-8000-000000000001'
WHERE tenant_id IS NULL;
UPDATE integration.api_idempotency log
SET tenant_id = client.tenant_id
FROM integration.api_client client
WHERE log.client_id = client.id AND log.tenant_id IS NULL;
UPDATE integration.webhook_subscription
SET tenant_id = '01900000-0000-7000-8000-000000000001'
WHERE tenant_id IS NULL;
UPDATE integration.webhook_delivery delivery
SET tenant_id = subscription.tenant_id
FROM integration.webhook_subscription subscription
WHERE delivery.subscription_id = subscription.id AND delivery.tenant_id IS NULL;
UPDATE integration.connector_run
SET tenant_id = '01900000-0000-7000-8000-000000000001'
WHERE tenant_id IS NULL;

ALTER TABLE integration.api_client
    ALTER COLUMN tenant_id SET NOT NULL,
    ADD CONSTRAINT fk_api_client_tenant FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id);
ALTER TABLE integration.api_idempotency
    ALTER COLUMN tenant_id SET NOT NULL,
    ADD CONSTRAINT fk_api_idempotency_tenant FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id);
ALTER TABLE integration.webhook_subscription
    ALTER COLUMN tenant_id SET NOT NULL,
    ADD CONSTRAINT fk_webhook_subscription_tenant FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id);
ALTER TABLE integration.webhook_delivery
    ALTER COLUMN tenant_id SET NOT NULL,
    ADD CONSTRAINT fk_webhook_delivery_tenant FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id);
ALTER TABLE integration.connector_run
    ALTER COLUMN tenant_id SET NOT NULL,
    ADD CONSTRAINT fk_connector_run_tenant FOREIGN KEY (tenant_id) REFERENCES platform.tenant(id);

CREATE TABLE integration.api_call_log (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES platform.tenant(id),
    client_id uuid NOT NULL,
    scope varchar(160) NOT NULL,
    api_path varchar(200) NOT NULL,
    http_method varchar(10) NOT NULL,
    status varchar(24) NOT NULL,
    trace_id varchar(100) NOT NULL,
    created_at timestamptz NOT NULL
);

ALTER TABLE integration.api_client
    ADD CONSTRAINT uq_api_client_id_tenant UNIQUE (id, tenant_id);
ALTER TABLE integration.api_idempotency
    ADD CONSTRAINT fk_api_idempotency_client_tenant
    FOREIGN KEY (client_id, tenant_id) REFERENCES integration.api_client(id, tenant_id);
ALTER TABLE integration.api_call_log
    ADD CONSTRAINT fk_api_call_log_client_tenant
    FOREIGN KEY (client_id, tenant_id) REFERENCES integration.api_client(id, tenant_id);
ALTER TABLE integration.webhook_subscription
    ADD CONSTRAINT uq_webhook_subscription_id_tenant UNIQUE (id, tenant_id);
ALTER TABLE integration.webhook_delivery
    ADD CONSTRAINT fk_webhook_delivery_subscription_tenant
    FOREIGN KEY (subscription_id, tenant_id) REFERENCES integration.webhook_subscription(id, tenant_id);

CREATE INDEX ix_api_client_tenant_created
    ON integration.api_client(tenant_id, created_at DESC);
CREATE INDEX ix_api_idempotency_tenant_created
    ON integration.api_idempotency(tenant_id, created_at DESC);
CREATE INDEX ix_api_call_log_tenant_created
    ON integration.api_call_log(tenant_id, created_at DESC);
CREATE INDEX ix_webhook_subscription_tenant_created
    ON integration.webhook_subscription(tenant_id, created_at DESC);
CREATE INDEX ix_webhook_delivery_tenant_created
    ON integration.webhook_delivery(tenant_id, created_at DESC);
CREATE INDEX ix_connector_run_tenant_started
    ON integration.connector_run(tenant_id, started_at DESC);
