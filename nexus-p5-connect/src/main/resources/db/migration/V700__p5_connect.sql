CREATE SCHEMA IF NOT EXISTS integration;
CREATE TABLE integration.api_client (
 id uuid PRIMARY KEY, name varchar(160) NOT NULL, key_prefix varchar(16) NOT NULL UNIQUE,
 secret_digest bytea NOT NULL, scopes jsonb NOT NULL, rate_limit_per_minute integer NOT NULL CHECK(rate_limit_per_minute>0),
 active boolean NOT NULL, created_at timestamptz NOT NULL
);
CREATE TABLE integration.api_rate_window (
 client_id uuid NOT NULL REFERENCES integration.api_client(id), window_start timestamptz NOT NULL,
 request_count integer NOT NULL, PRIMARY KEY(client_id,window_start)
);
CREATE TABLE integration.api_idempotency (
 client_id uuid NOT NULL REFERENCES integration.api_client(id), idempotency_key varchar(160) NOT NULL,
 response jsonb NOT NULL, created_at timestamptz NOT NULL, PRIMARY KEY(client_id,idempotency_key)
);
CREATE TABLE integration.webhook_subscription (
 id uuid PRIMARY KEY, name varchar(160) NOT NULL, endpoint text NOT NULL, event_types jsonb NOT NULL,
 active boolean NOT NULL, created_at timestamptz NOT NULL
);
CREATE TABLE integration.webhook_delivery (
 id uuid PRIMARY KEY, subscription_id uuid NOT NULL REFERENCES integration.webhook_subscription(id),
 event_id varchar(160) NOT NULL, event_type varchar(160) NOT NULL, payload jsonb NOT NULL,
 status varchar(24) NOT NULL, attempts integer NOT NULL, next_attempt_at timestamptz NOT NULL,
 last_error text, created_at timestamptz NOT NULL, delivered_at timestamptz,
 UNIQUE(subscription_id,event_id)
);
CREATE INDEX ix_webhook_due ON integration.webhook_delivery(status,next_attempt_at);
CREATE TABLE integration.connector_run (
 id uuid PRIMARY KEY, connector_code varchar(100) NOT NULL, status varchar(24) NOT NULL,
 output jsonb NOT NULL, error text, started_at timestamptz NOT NULL, finished_at timestamptz NOT NULL
);
