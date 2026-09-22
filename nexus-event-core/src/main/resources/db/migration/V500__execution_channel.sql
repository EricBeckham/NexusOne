-- M0-09 execution channel (Flyway window V500+).
-- Quartz JDBC JobStore tables are the official 2.5.2 PostgreSQL DDL, schema-qualified
-- into `quartz` (source: quartz v2.5.2 tables_postgres.sql). No DROP; Flyway owns create.
-- Task queue, outbox, idempotency, dead-letter and job log are platform-static.

CREATE SCHEMA IF NOT EXISTS platform;
CREATE SCHEMA IF NOT EXISTS quartz;

COMMENT ON SCHEMA quartz IS 'Quartz 2.5.2 JDBC JobStore. Flyway-managed by M0-09. Not a business schema.';

GRANT USAGE ON SCHEMA platform TO CURRENT_USER;
GRANT USAGE ON SCHEMA quartz TO CURRENT_USER;

CREATE TABLE quartz.QRTZ_JOB_DETAILS
(
  SCHED_NAME        VARCHAR(120) NOT NULL,
  JOB_NAME          VARCHAR(200) NOT NULL,
  JOB_GROUP         VARCHAR(200) NOT NULL,
  DESCRIPTION       VARCHAR(250) NULL,
  JOB_CLASS_NAME    VARCHAR(250) NOT NULL,
  IS_DURABLE        BOOL         NOT NULL,
  IS_NONCONCURRENT  BOOL         NOT NULL,
  IS_UPDATE_DATA    BOOL         NOT NULL,
  REQUESTS_RECOVERY BOOL         NOT NULL,
  JOB_DATA          BYTEA        NULL,
  PRIMARY KEY (SCHED_NAME, JOB_NAME, JOB_GROUP)
);

CREATE TABLE quartz.QRTZ_TRIGGERS
(
  SCHED_NAME     VARCHAR(120) NOT NULL,
  TRIGGER_NAME   VARCHAR(200) NOT NULL,
  TRIGGER_GROUP  VARCHAR(200) NOT NULL,
  JOB_NAME       VARCHAR(200) NOT NULL,
  JOB_GROUP      VARCHAR(200) NOT NULL,
  DESCRIPTION    VARCHAR(250) NULL,
  NEXT_FIRE_TIME BIGINT       NULL,
  PREV_FIRE_TIME BIGINT       NULL,
  PRIORITY       INTEGER      NULL,
  TRIGGER_STATE  VARCHAR(16)  NOT NULL,
  TRIGGER_TYPE   VARCHAR(8)   NOT NULL,
  START_TIME     BIGINT       NOT NULL,
  END_TIME       BIGINT       NULL,
  CALENDAR_NAME  VARCHAR(200) NULL,
  MISFIRE_INSTR  SMALLINT     NULL,
  JOB_DATA       BYTEA        NULL,
  PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
  FOREIGN KEY (SCHED_NAME, JOB_NAME, JOB_GROUP)
  REFERENCES quartz.QRTZ_JOB_DETAILS (SCHED_NAME, JOB_NAME, JOB_GROUP)
);

CREATE TABLE quartz.QRTZ_SIMPLE_TRIGGERS
(
  SCHED_NAME      VARCHAR(120) NOT NULL,
  TRIGGER_NAME    VARCHAR(200) NOT NULL,
  TRIGGER_GROUP   VARCHAR(200) NOT NULL,
  REPEAT_COUNT    BIGINT       NOT NULL,
  REPEAT_INTERVAL BIGINT       NOT NULL,
  TIMES_TRIGGERED BIGINT       NOT NULL,
  PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
  FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
  REFERENCES quartz.QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
);

CREATE TABLE quartz.QRTZ_CRON_TRIGGERS
(
  SCHED_NAME      VARCHAR(120) NOT NULL,
  TRIGGER_NAME    VARCHAR(200) NOT NULL,
  TRIGGER_GROUP   VARCHAR(200) NOT NULL,
  CRON_EXPRESSION VARCHAR(120) NOT NULL,
  TIME_ZONE_ID    VARCHAR(80),
  PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
  FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
  REFERENCES quartz.QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
);

CREATE TABLE quartz.QRTZ_SIMPROP_TRIGGERS
(
  SCHED_NAME    VARCHAR(120)   NOT NULL,
  TRIGGER_NAME  VARCHAR(200)   NOT NULL,
  TRIGGER_GROUP VARCHAR(200)   NOT NULL,
  STR_PROP_1    VARCHAR(512)   NULL,
  STR_PROP_2    VARCHAR(512)   NULL,
  STR_PROP_3    VARCHAR(512)   NULL,
  INT_PROP_1    INT            NULL,
  INT_PROP_2    INT            NULL,
  LONG_PROP_1   BIGINT         NULL,
  LONG_PROP_2   BIGINT         NULL,
  DEC_PROP_1    NUMERIC(13, 4) NULL,
  DEC_PROP_2    NUMERIC(13, 4) NULL,
  BOOL_PROP_1   BOOL           NULL,
  BOOL_PROP_2   BOOL           NULL,
  PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
  FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
  REFERENCES quartz.QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
);

CREATE TABLE quartz.QRTZ_BLOB_TRIGGERS
(
  SCHED_NAME    VARCHAR(120) NOT NULL,
  TRIGGER_NAME  VARCHAR(200) NOT NULL,
  TRIGGER_GROUP VARCHAR(200) NOT NULL,
  BLOB_DATA     BYTEA        NULL,
  PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
  FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
  REFERENCES quartz.QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
);

CREATE TABLE quartz.QRTZ_CALENDARS
(
  SCHED_NAME    VARCHAR(120) NOT NULL,
  CALENDAR_NAME VARCHAR(200) NOT NULL,
  CALENDAR      BYTEA        NOT NULL,
  PRIMARY KEY (SCHED_NAME, CALENDAR_NAME)
);

CREATE TABLE quartz.QRTZ_PAUSED_TRIGGER_GRPS
(
  SCHED_NAME    VARCHAR(120) NOT NULL,
  TRIGGER_GROUP VARCHAR(200) NOT NULL,
  PRIMARY KEY (SCHED_NAME, TRIGGER_GROUP)
);

CREATE TABLE quartz.QRTZ_FIRED_TRIGGERS
(
  SCHED_NAME        VARCHAR(120) NOT NULL,
  ENTRY_ID          VARCHAR(95)  NOT NULL,
  TRIGGER_NAME      VARCHAR(200) NOT NULL,
  TRIGGER_GROUP     VARCHAR(200) NOT NULL,
  INSTANCE_NAME     VARCHAR(200) NOT NULL,
  FIRED_TIME        BIGINT       NOT NULL,
  SCHED_TIME        BIGINT       NOT NULL,
  PRIORITY          INTEGER      NOT NULL,
  STATE             VARCHAR(16)  NOT NULL,
  JOB_NAME          VARCHAR(200) NULL,
  JOB_GROUP         VARCHAR(200) NULL,
  IS_NONCONCURRENT  BOOL         NULL,
  REQUESTS_RECOVERY BOOL         NULL,
  PRIMARY KEY (SCHED_NAME, ENTRY_ID)
);

CREATE TABLE quartz.QRTZ_SCHEDULER_STATE
(
  SCHED_NAME        VARCHAR(120) NOT NULL,
  INSTANCE_NAME     VARCHAR(200) NOT NULL,
  LAST_CHECKIN_TIME BIGINT       NOT NULL,
  CHECKIN_INTERVAL  BIGINT       NOT NULL,
  PRIMARY KEY (SCHED_NAME, INSTANCE_NAME)
);

CREATE TABLE quartz.QRTZ_LOCKS
(
  SCHED_NAME VARCHAR(120) NOT NULL,
  LOCK_NAME  VARCHAR(40)  NOT NULL,
  PRIMARY KEY (SCHED_NAME, LOCK_NAME)
);

CREATE INDEX IDX_QRTZ_J_REQ_RECOVERY
  ON quartz.QRTZ_JOB_DETAILS (SCHED_NAME, REQUESTS_RECOVERY);
CREATE INDEX IDX_QRTZ_J_GRP
  ON quartz.QRTZ_JOB_DETAILS (SCHED_NAME, JOB_GROUP);
CREATE INDEX IDX_QRTZ_T_J
  ON quartz.QRTZ_TRIGGERS (SCHED_NAME, JOB_NAME, JOB_GROUP);
CREATE INDEX IDX_QRTZ_T_JG
  ON quartz.QRTZ_TRIGGERS (SCHED_NAME, JOB_GROUP);
CREATE INDEX IDX_QRTZ_T_C
  ON quartz.QRTZ_TRIGGERS (SCHED_NAME, CALENDAR_NAME);
CREATE INDEX IDX_QRTZ_T_G
  ON quartz.QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_GROUP);
CREATE INDEX IDX_QRTZ_T_STATE
  ON quartz.QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_T_N_STATE
  ON quartz.QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP, TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_T_N_G_STATE
  ON quartz.QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_GROUP, TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_T_NEXT_FIRE_TIME
  ON quartz.QRTZ_TRIGGERS (SCHED_NAME, NEXT_FIRE_TIME);
CREATE INDEX IDX_QRTZ_T_NFT_ST
  ON quartz.QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_STATE, NEXT_FIRE_TIME);
CREATE INDEX IDX_QRTZ_T_NFT_MISFIRE
  ON quartz.QRTZ_TRIGGERS (SCHED_NAME, MISFIRE_INSTR, NEXT_FIRE_TIME);
CREATE INDEX IDX_QRTZ_T_NFT_ST_MISFIRE
  ON quartz.QRTZ_TRIGGERS (SCHED_NAME, MISFIRE_INSTR, NEXT_FIRE_TIME, TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_T_NFT_ST_MISFIRE_GRP
  ON quartz.QRTZ_TRIGGERS (SCHED_NAME, MISFIRE_INSTR, NEXT_FIRE_TIME, TRIGGER_GROUP, TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_FT_TRIG_INST_NAME
  ON quartz.QRTZ_FIRED_TRIGGERS (SCHED_NAME, INSTANCE_NAME);
CREATE INDEX IDX_QRTZ_FT_INST_JOB_REQ_RCVRY
  ON quartz.QRTZ_FIRED_TRIGGERS (SCHED_NAME, INSTANCE_NAME, REQUESTS_RECOVERY);
CREATE INDEX IDX_QRTZ_FT_J_G
  ON quartz.QRTZ_FIRED_TRIGGERS (SCHED_NAME, JOB_NAME, JOB_GROUP);
CREATE INDEX IDX_QRTZ_FT_JG
  ON quartz.QRTZ_FIRED_TRIGGERS (SCHED_NAME, JOB_GROUP);
CREATE INDEX IDX_QRTZ_FT_T_G
  ON quartz.QRTZ_FIRED_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP);
CREATE INDEX IDX_QRTZ_FT_TG
  ON quartz.QRTZ_FIRED_TRIGGERS (SCHED_NAME, TRIGGER_GROUP);

CREATE TABLE platform.exec_outbox (
    id uuid PRIMARY KEY,
    tenant_id text NOT NULL,
    trace_id text NOT NULL,
    actor_id text NOT NULL,
    actor_type text NOT NULL,
    membership_id text NOT NULL,
    destination text NOT NULL,
    event_type text NOT NULL,
    event_version integer NOT NULL,
    aggregate_type text,
    aggregate_id uuid,
    correlation_id text,
    causation_id text,
    payload text NOT NULL,
    status text NOT NULL,
    attempt integer NOT NULL DEFAULT 0,
    max_attempts integer NOT NULL DEFAULT 5,
    available_at timestamptz NOT NULL,
    lease_until timestamptz,
    leased_by text,
    last_error text,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    dispatched_at timestamptz,
    CONSTRAINT exec_outbox_status CHECK (status IN ('PENDING', 'DISPATCHING', 'DISPATCHED', 'FAILED', 'DEAD'))
);

CREATE INDEX exec_outbox_poll_idx
    ON platform.exec_outbox (available_at)
    WHERE status IN ('PENDING', 'FAILED');
CREATE INDEX exec_outbox_tenant_idx ON platform.exec_outbox (tenant_id, created_at);

CREATE TABLE platform.exec_task (
    id uuid PRIMARY KEY,
    tenant_id text NOT NULL,
    trace_id text NOT NULL,
    actor_id text NOT NULL,
    actor_type text NOT NULL,
    membership_id text NOT NULL,
    task_type text NOT NULL,
    payload text NOT NULL,
    status text NOT NULL,
    attempt integer NOT NULL DEFAULT 0,
    max_attempts integer NOT NULL DEFAULT 5,
    available_at timestamptz NOT NULL,
    lease_until timestamptz,
    leased_by text,
    last_error text,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    completed_at timestamptz,
    CONSTRAINT exec_task_status CHECK (status IN ('PENDING', 'LEASED', 'SUCCEEDED', 'FAILED', 'DEAD'))
);

CREATE INDEX exec_task_poll_idx
    ON platform.exec_task (available_at)
    WHERE status IN ('PENDING', 'FAILED');
CREATE INDEX exec_task_tenant_idx ON platform.exec_task (tenant_id, created_at);

CREATE TABLE platform.exec_idempotency (
    scope text NOT NULL,
    idempotency_key text NOT NULL,
    tenant_id text NOT NULL,
    trace_id text NOT NULL,
    first_seen_at timestamptz NOT NULL,
    result_ref text,
    PRIMARY KEY (scope, idempotency_key)
);

CREATE INDEX exec_idempotency_tenant_idx ON platform.exec_idempotency (tenant_id, first_seen_at);

CREATE TABLE platform.exec_dead_letter (
    id uuid PRIMARY KEY,
    channel text NOT NULL,
    source_id uuid NOT NULL,
    tenant_id text NOT NULL,
    trace_id text NOT NULL,
    payload_type text NOT NULL,
    payload text NOT NULL,
    last_error text,
    attempt integer NOT NULL,
    failed_at timestamptz NOT NULL,
    CONSTRAINT exec_dead_letter_channel CHECK (channel IN ('OUTBOX', 'TASK', 'QUARTZ'))
);

CREATE INDEX exec_dead_letter_channel_idx ON platform.exec_dead_letter (channel, failed_at);

CREATE TABLE platform.exec_job_log (
    id uuid PRIMARY KEY,
    channel text NOT NULL,
    source_id uuid,
    job_key text,
    tenant_id text NOT NULL,
    trace_id text NOT NULL,
    status text NOT NULL,
    detail text,
    created_at timestamptz NOT NULL,
    CONSTRAINT exec_job_log_channel CHECK (channel IN ('OUTBOX', 'TASK', 'QUARTZ'))
);

CREATE INDEX exec_job_log_trace_idx ON platform.exec_job_log (trace_id, created_at);

COMMENT ON TABLE platform.exec_outbox IS
    'Transactional outbox. Dispatcher leases with SKIP LOCKED. M0-10 notifications use destination notify.inbox.';
COMMENT ON TABLE platform.exec_task IS
    'Database task queue. Worker leases with SKIP LOCKED. Payloads always carry tenant_id + trace_id.';
COMMENT ON TABLE platform.exec_idempotency IS
    'Critical-path idempotency keys (outbox consume, not cache).';
