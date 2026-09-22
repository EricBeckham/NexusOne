-- M0-10 in-app notification templates and inbox. Async delivery is blocked on M0-09 Outbox.
CREATE SCHEMA IF NOT EXISTS notify;
COMMENT ON SCHEMA notify IS 'M0-10 in-app notifications. Email/SMS are out of M0.';
GRANT USAGE ON SCHEMA notify TO CURRENT_USER;

CREATE TABLE notify.notification_template (
    template_code    varchar(64) PRIMARY KEY,
    channel          varchar(16) NOT NULL,
    title_template   varchar(200) NOT NULL,
    body_template    varchar(2000) NOT NULL,
    required_vars    varchar(512) NOT NULL,
    locale           varchar(16) NOT NULL DEFAULT 'zh-CN',
    CONSTRAINT notify_template_channel CHECK (channel = 'IN_APP')
);

CREATE TABLE notify.inbox (
    notification_id     uuid PRIMARY KEY,
    tenant_id           uuid NOT NULL,
    recipient_subject   varchar(128) NOT NULL,
    template_code       varchar(64) NOT NULL REFERENCES notify.notification_template (template_code),
    title               varchar(200) NOT NULL,
    body                varchar(2000) NOT NULL,
    payload_json        text NOT NULL,
    status              varchar(16) NOT NULL,
    outbox_message_id   uuid,
    created_at          timestamptz NOT NULL,
    read_at             timestamptz,
    CONSTRAINT notify_inbox_status CHECK (status IN ('PENDING', 'DELIVERED', 'READ'))
);

CREATE INDEX notify_inbox_recipient_idx ON notify.inbox (tenant_id, recipient_subject, created_at DESC);

INSERT INTO notify.notification_template (template_code, channel, title_template, body_template, required_vars, locale)
VALUES
    ('approval.task.assigned', 'IN_APP', '待审批：{taskTitle}',
     '{actor} 提交了「{taskTitle}」，请办理。', 'taskTitle,actor', 'zh-CN'),
    ('approval.task.approved', 'IN_APP', '审批已通过：{taskTitle}',
     '「{taskTitle}」已由 {actor} 通过。', 'taskTitle,actor', 'zh-CN'),
    ('approval.task.rejected', 'IN_APP', '审批已驳回：{taskTitle}',
     '「{taskTitle}」已被 {actor} 驳回。', 'taskTitle,actor', 'zh-CN');
