package one.nexus.p3.studio.form;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import one.nexus.common.id.UuidV7;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import one.nexus.p3.domain.Application;
import one.nexus.p3.domain.ApplicationKind;
import one.nexus.p3.domain.ApplicationStatus;
import one.nexus.p3.domain.FieldType;
import one.nexus.p3.domain.LedgerDefinition;
import one.nexus.p3.domain.LedgerField;
import one.nexus.p3.domain.LedgerScope;
import one.nexus.p3.domain.LedgerStatus;
import one.nexus.p3.infra.jdbc.JdbcApplicationRepository;
import one.nexus.p3.infra.jdbc.JdbcFieldRepository;
import one.nexus.p3.infra.jdbc.JdbcLedgerRepository;
import one.nexus.p3.infra.jdbc.JdbcTableRegistryWriter;
import one.nexus.p3.infra.schema.JdbcSchemaService;
import one.nexus.p3.schema.SchemaService;
import one.nexus.p3.studio.StudioIsolation;
import one.nexus.p3.studio.meta.InMemoryLedgerMetadataCatalog;
import one.nexus.p3.studio.meta.JdbcLedgerMetadataPort;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.JdbcTransactionManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * G5 form-publish paths on PostgreSQL 15: validate-fail stays DRAFT;
 * product {@link SchemaServicePublishHook} CREATE/evolve on drift; mid-publish
 * failure (PublishHook stand-in and SchemaService evolve DDL abort) leaves the
 * Running pointer and published snapshot unchanged.
 */
class JdbcFormPublishRollbackIT {
    private static final String TENANT = "11111111-1111-4111-8111-111111111111";

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void validateFailureKeepsDraftAndDoesNotWriteVersionOnPostgres15() throws Exception {
        try (Fixture fx = Fixture.start(PublishHook.noop())) {
            FormRecord form = fx.service.create(
                    "demo",
                    "bad_leave",
                    "坏请假单",
                    null,
                    List.of(new FormBinding("w1", "missing_ledger", "title", true, false, WidgetType.TEXT, 0)));

            NexusException failed = assertThrows(NexusException.class, () -> fx.service.validate(form.formId()));
            assertEquals(ErrorCode.FORM_VALIDATE_FAILED, failed.errorCode());

            FormRow row = fx.readForm(form.formId());
            assertEquals("DRAFT", row.draftStatus());
            assertNull(row.runningVersion());
            assertEquals(0, fx.countVersions(form.formId()));
            assertEquals(0, fx.countRunning(form.formId()));
            assertTrue(fx.service.versions(form.formId()).isEmpty());
        }
    }

    @Test
    void validateFailureAfterRunningLeavesPointerAndSnapshotUnchanged() throws Exception {
        try (Fixture fx = Fixture.start(PublishHook.noop())) {
            FormRecord form = fx.service.create("demo", "leave_form", "请假单", null, leaveBindings());
            fx.service.validate(form.formId());
            fx.service.publish(form.formId(), "v1");
            PublishedPointer before = fx.readPointer(form.formId());
            assertEquals(1, before.runningVersion());

            fx.service.update(
                    form.formId(),
                    "请假单坏绑定",
                    null,
                    List.of(new FormBinding("w1", "missing_ledger", "title", true, false, WidgetType.TEXT, 0)));
            NexusException failed = assertThrows(NexusException.class, () -> fx.service.validate(form.formId()));
            assertEquals(ErrorCode.FORM_VALIDATE_FAILED, failed.errorCode());

            FormRow afterDraft = fx.readForm(form.formId());
            assertEquals("DRAFT", afterDraft.draftStatus());
            PublishedPointer after = fx.readPointer(form.formId());
            assertEquals(before, after);
        }
    }

    @Test
    void failedPublishLeavesRunningPointerAndSnapshotUnchangedOnPostgres15() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        PublishHook hook = snapshot -> {
            if (calls.incrementAndGet() >= 2) {
                throw new IllegalStateException("ddl failed");
            }
        };
        try (Fixture fx = Fixture.start(hook)) {
            FormRecord form = fx.service.create("demo", "leave_form", "请假单", null, leaveBindings());
            fx.service.validate(form.formId());
            fx.service.publish(form.formId(), "v1");
            PublishedPointer before = fx.readPointer(form.formId());
            assertEquals(1, before.runningVersion());
            assertEquals("RUNNING", before.status());
            assertTrue(before.snapshotJson().contains("\"version_no\": 1")
                    || before.snapshotJson().contains("\"version_no\":1"));

            fx.service.update(form.formId(), "请假单 v2", null, leaveBindings());
            fx.service.validate(form.formId());
            assertEquals("VALIDATED", fx.readForm(form.formId()).draftStatus());

            NexusException failed = assertThrows(NexusException.class, () -> fx.service.publish(form.formId(), "v2"));
            assertEquals(ErrorCode.FORM_PUBLISH_FAILED, failed.errorCode());

            FormRow afterForm = fx.readForm(form.formId());
            assertEquals("VALIDATED", afterForm.draftStatus());
            PublishedPointer after = fx.readPointer(form.formId());
            assertEquals(before, after);
            assertEquals(1, fx.countVersions(form.formId()));
            assertEquals(1, fx.countRunning(form.formId()));
            assertTrue(fx.service.publishLog(form.formId()).stream().anyMatch(log -> "ROLLED_BACK".equals(log.outcome())));
        }
    }

    @Test
    void schemaServiceCreateOnMissingPhysicalTableSucceeds() throws Exception {
        try (SchemaEnv env = SchemaEnv.start()) {
            env.dropPhysicalTable();
            assertEquals(0, env.countPhysicalTables());
            FormService service = env.formService(env.schema);
            FormRecord form = service.create("demo", "leave_form", "请假单", null, leaveBindings());
            service.validate(form.formId());
            FormRecord published = service.publish(form.formId(), "v1");
            assertEquals(1, published.runningVersion());
            assertEquals(1, env.countPhysicalTables());
            assertEquals(1, env.countColumn("title"));
            assertEquals(1, env.countColumn("days"));
            assertEquals(1, env.countColumn("applicant"));
            assertEquals(1, env.countRunning(form.formId()));
            assertEquals(1, env.ledgerSchemaVersion());
        }
    }

    @Test
    void schemaServiceEvolveNewFieldOnPublishSucceeds() throws Exception {
        try (SchemaEnv env = SchemaEnv.start()) {
            FormService service = env.formService(env.schema);
            FormRecord form = service.create("demo", "leave_form", "请假单", null, leaveBindings());
            service.validate(form.formId());
            service.publish(form.formId(), "v1");
            assertEquals(0, env.countColumn("remark"));
            assertEquals(1, env.ledgerSchemaVersion());

            env.insertField(env.newField("remark", FieldType.TEXT, false, 3));
            service.update(form.formId(), "请假单 v2", null, remarkBindings());
            service.validate(form.formId());
            FormRecord published = service.publish(form.formId(), "v2");
            assertEquals(2, published.runningVersion());
            assertEquals(1, env.countColumn("remark"));
            assertEquals(2, env.ledgerSchemaVersion());
            PublishedPointer after = env.readPointer(form.formId());
            assertEquals(2, after.runningVersion());
            assertTrue(after.snapshotJson().contains("remark"), after.snapshotJson());
        }
    }

    @Test
    void schemaServiceEvolveAbortLeavesRunningPointerAndSnapshotUnchanged() throws Exception {
        try (SchemaEnv env = SchemaEnv.start()) {
            FormService service = env.formService(env.schema);
            FormRecord form = service.create("demo", "leave_form", "请假单", null, leaveBindings());
            service.validate(form.formId());
            service.publish(form.formId(), "v1");
            PublishedPointer before = env.readPointer(form.formId());
            assertEquals(1, before.runningVersion());
            assertEquals(1, env.countPhysicalTables());

            env.insertPhysicalRow();
            env.insertField(env.newField("remark", FieldType.TEXT, true, 3));
            service.update(form.formId(), "请假单 v2", null, remarkBindings());
            service.validate(form.formId());
            NexusException failed = assertThrows(NexusException.class, () -> service.publish(form.formId(), "v2"));
            assertEquals(ErrorCode.FORM_PUBLISH_FAILED, failed.errorCode());
            String cause = String.valueOf(failed.details().get("cause"));
            assertTrue(cause.contains("SchemaService evolve failed"), () -> cause);
            assertTrue(cause.contains(SchemaEnv.PHYSICAL_TABLE), () -> cause);

            PublishedPointer after = env.readPointer(form.formId());
            assertEquals(before, after);
            assertEquals(1, env.countVersions(form.formId()));
            assertEquals(1, env.countRunning(form.formId()));
            assertEquals(1, env.countPhysicalTables());
            assertEquals(0, env.countColumn("remark"));
            assertEquals(1, env.ledgerSchemaVersion());
            assertTrue(service.publishLog(form.formId()).stream().anyMatch(log -> "ROLLED_BACK".equals(log.outcome())));
        }
    }

    private static List<FormBinding> leaveBindings() {
        return List.of(
                new FormBinding("title", "leave_request", "title", true, false, WidgetType.TEXT, 0),
                new FormBinding("days", "leave_request", "days", true, false, WidgetType.NUMBER, 1),
                new FormBinding("applicant", "leave_request", "applicant", true, false, WidgetType.PERSON, 2));
    }

    private static List<FormBinding> remarkBindings() {
        return List.of(
                new FormBinding("title", "leave_request", "title", true, false, WidgetType.TEXT, 0),
                new FormBinding("days", "leave_request", "days", true, false, WidgetType.NUMBER, 1),
                new FormBinding("applicant", "leave_request", "applicant", true, false, WidgetType.PERSON, 2),
                new FormBinding("remark", "leave_request", "remark", false, false, WidgetType.TEXTAREA, 3));
    }

    private record FormRow(String draftStatus, Integer runningVersion) {}

    private record PublishedPointer(
            Integer runningVersion, UUID versionId, String status, String snapshotJson, int versionCount) {}

    private static final class Fixture implements AutoCloseable {
        private final EmbeddedPostgres postgres;
        final JdbcClient jdbc;
        final FormService service;

        private Fixture(EmbeddedPostgres postgres, JdbcClient jdbc, FormService service) {
            this.postgres = postgres;
            this.jdbc = jdbc;
            this.service = service;
        }

        static Fixture start(PublishHook hook) throws Exception {
            EmbeddedPostgres postgres = EmbeddedPostgres.builder().start();
            assertPostgres15(postgres);
            String url = "jdbc:postgresql://127.0.0.1:" + postgres.getPort() + "/postgres";
            Flyway.configure()
                    .dataSource(url, "postgres", "postgres")
                    .locations("classpath:db/migration")
                    .schemas("platform")
                    .defaultSchema("platform")
                    .load()
                    .migrate();
            PGSimpleDataSource dataSource = new PGSimpleDataSource();
            dataSource.setURL(url);
            dataSource.setUser("postgres");
            dataSource.setPassword("postgres");
            JdbcClient jdbc = JdbcClient.create(dataSource);
            JdbcFormStore store = new JdbcFormStore(jdbc, JsonMapper.builder().build());
            InMemoryLedgerMetadataCatalog metadata = new InMemoryLedgerMetadataCatalog();
            metadata.seedDemoLeaveRequest();
            JdbcTransactionManager tx = new JdbcTransactionManager(dataSource);
            IsolationKernel kernel =
                    IsolationKernel.create(new TableRegistry(), Set.of("p3.studio"), new CollectingAuditSink());
            StudioIsolation.bindTenant(kernel, TENANT, "designer-a", "mem-a", "trace-pg");
            FormService service = new FormService(store, metadata, hook, Clock.systemUTC(), tx);
            return new Fixture(postgres, jdbc, service);
        }

        FormRow readForm(UUID formId) {
            return jdbc.sql(
                            """
                            select draft_status, running_version
                              from business_meta.form
                             where form_id = :id
                            """)
                    .param("id", formId)
                    .query((rs, row) -> new FormRow(
                            rs.getString("draft_status"), (Integer) rs.getObject("running_version")))
                    .single();
        }

        PublishedPointer readPointer(UUID formId) {
            FormRow form = readForm(formId);
            return jdbc.sql(
                            """
                            select version_id, status, snapshot_json::text as snapshot_json
                              from business_meta.form_version
                             where form_id = :id and status = 'RUNNING'
                            """)
                    .param("id", formId)
                    .query((rs, row) -> new PublishedPointer(
                            form.runningVersion(),
                            rs.getObject("version_id", UUID.class),
                            rs.getString("status"),
                            rs.getString("snapshot_json"),
                            countVersions(formId)))
                    .single();
        }

        int countVersions(UUID formId) {
            return jdbc.sql("select count(*) from business_meta.form_version where form_id = :id")
                    .param("id", formId)
                    .query(Integer.class)
                    .single();
        }

        int countRunning(UUID formId) {
            return jdbc.sql(
                            "select count(*) from business_meta.form_version where form_id = :id and status = 'RUNNING'")
                    .param("id", formId)
                    .query(Integer.class)
                    .single();
        }

        @Override
        public void close() throws Exception {
            AccessContextHolder.clear();
            postgres.close();
        }
    }

    /**
     * Real {@link JdbcSchemaService} + physical {@code l_g5a_leave_request} on Zonky PG 15.
     * Form publish goes through {@link SchemaServicePublishHook}.
     */
    private static final class SchemaEnv implements AutoCloseable {
        static final String PHYSICAL_TABLE = "l_g5a_leave_request";
        static final String TENANT_CODE = "g5a";

        private final EmbeddedPostgres postgres;
        final JdbcClient jdbc;
        final PGSimpleDataSource dataSource;
        final JdbcFormStore store;
        final JdbcTransactionManager tx;
        final JdbcSchemaService schema;
        final JdbcLedgerRepository ledgers;
        final JdbcFieldRepository fields;
        final JdbcLedgerMetadataPort metadata;
        final LedgerDefinition ledger;
        final List<LedgerField> ledgerFields;

        private SchemaEnv(
                EmbeddedPostgres postgres,
                JdbcClient jdbc,
                PGSimpleDataSource dataSource,
                JdbcFormStore store,
                JdbcTransactionManager tx,
                JdbcSchemaService schema,
                JdbcLedgerRepository ledgers,
                JdbcFieldRepository fields,
                JdbcLedgerMetadataPort metadata,
                LedgerDefinition ledger,
                List<LedgerField> ledgerFields) {
            this.postgres = postgres;
            this.jdbc = jdbc;
            this.dataSource = dataSource;
            this.store = store;
            this.tx = tx;
            this.schema = schema;
            this.ledgers = ledgers;
            this.fields = fields;
            this.metadata = metadata;
            this.ledger = ledger;
            this.ledgerFields = ledgerFields;
        }

        static SchemaEnv start() throws Exception {
            EmbeddedPostgres postgres = EmbeddedPostgres.builder().start();
            assertPostgres15(postgres);
            String url = "jdbc:postgresql://127.0.0.1:" + postgres.getPort() + "/postgres";
            Flyway.configure()
                    .dataSource(url, "postgres", "postgres")
                    .locations("classpath:db/migration")
                    .schemas("platform")
                    .defaultSchema("platform")
                    .load()
                    .migrate();
            PGSimpleDataSource dataSource = new PGSimpleDataSource();
            dataSource.setURL(url);
            dataSource.setUser("postgres");
            dataSource.setPassword("postgres");
            JdbcClient jdbc = JdbcClient.create(dataSource);
            JdbcApplicationRepository apps = new JdbcApplicationRepository(jdbc);
            JdbcLedgerRepository ledgers = new JdbcLedgerRepository(jdbc);
            JdbcFieldRepository fields = new JdbcFieldRepository(jdbc);
            IsolationKernel kernel =
                    IsolationKernel.create(new TableRegistry(), Set.of("p3.studio"), new CollectingAuditSink());
            JdbcSchemaService schema =
                    new JdbcSchemaService(dataSource, kernel, new JdbcTableRegistryWriter(), ledgers);
            UUID tenantId = UUID.fromString(TENANT);
            Instant now = Instant.parse("2026-09-22T00:00:00Z");
            UUID appId = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
            UUID ledgerId = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
            apps.insert(new Application(
                    appId,
                    tenantId,
                    TENANT_CODE,
                    "demo",
                    "Demo",
                    ApplicationKind.CUSTOM,
                    ApplicationStatus.ACTIVE,
                    now,
                    "designer",
                    now,
                    "designer",
                    null,
                    1L));
            LedgerDefinition draft = new LedgerDefinition(
                    ledgerId,
                    tenantId,
                    TENANT_CODE,
                    appId,
                    "leave_request",
                    "请假台账",
                    LedgerScope.TENANT,
                    PHYSICAL_TABLE,
                    1,
                    LedgerStatus.DRAFT,
                    now,
                    "designer",
                    now,
                    "designer",
                    null,
                    1L);
            ledgers.insert(draft);
            List<LedgerField> ledgerFields = List.of(
                    field(tenantId, ledgerId, "title", FieldType.TEXT, 0),
                    field(tenantId, ledgerId, "days", FieldType.INTEGER, 1),
                    field(tenantId, ledgerId, "applicant", FieldType.TEXT, 2));
            ledgerFields.forEach(fields::insert);
            LedgerDefinition ready = schema.createPhysicalTable(draft, ledgerFields);
            StudioIsolation.bindTenant(kernel, TENANT, "designer-a", "mem-a", "trace-schema-g5");
            JdbcFormStore store = new JdbcFormStore(jdbc, JsonMapper.builder().build());
            JdbcTransactionManager tx = new JdbcTransactionManager(dataSource);
            JdbcLedgerMetadataPort metadata = new JdbcLedgerMetadataPort(ledgers, fields, apps);
            return new SchemaEnv(
                    postgres, jdbc, dataSource, store, tx, schema, ledgers, fields, metadata, ready, ledgerFields);
        }

        FormService formService(SchemaService gate) {
            return new FormService(
                    store, metadata, new SchemaServicePublishHook(gate, ledgers, fields), Clock.systemUTC(), tx);
        }

        void dropPhysicalTable() throws Exception {
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.execute("DROP TABLE business_data." + PHYSICAL_TABLE);
            }
        }

        void insertField(LedgerField field) {
            fields.insert(field);
        }

        LedgerField newField(String fieldCode, FieldType type, boolean required, int order) {
            Instant now = Instant.parse("2026-09-22T00:00:00Z");
            return new LedgerField(
                    UuidV7.generate(),
                    ledger.tenantId(),
                    ledger.id(),
                    fieldCode,
                    fieldCode,
                    type,
                    required,
                    false,
                    order,
                    now,
                    "designer",
                    now,
                    "designer",
                    null,
                    1L);
        }

        void insertPhysicalRow() {
            jdbc.sql(
                            """
                            insert into business_data.l_g5a_leave_request (
                              id, tenant_id, created_at, created_by, updated_at, updated_by,
                              version, schema_version, ext_data, title, days, applicant)
                            values (
                              :id, :tenant_id, :now, 'designer', :now, 'designer',
                              1, 1, '{}'::jsonb, '年假', 3, 'alice')
                            """)
                    .param("id", UuidV7.generate())
                    .param("tenant_id", TENANT)
                    .param("now", java.sql.Timestamp.from(Instant.parse("2026-09-22T00:00:00Z")))
                    .update();
        }

        int countColumn(String column) {
            return jdbc.sql(
                            """
                            select count(*) from information_schema.columns
                             where table_schema = 'business_data'
                               and table_name = :name
                               and column_name = :column
                            """)
                    .param("name", PHYSICAL_TABLE)
                    .param("column", column)
                    .query(Integer.class)
                    .single();
        }

        int ledgerSchemaVersion() {
            return jdbc.sql("select schema_version from business_meta.ledger where id = :id")
                    .param("id", ledger.id())
                    .query(Integer.class)
                    .single();
        }

        int countPhysicalTables() {
            return jdbc.sql(
                            """
                            select count(*) from information_schema.tables
                             where table_schema = 'business_data' and table_name = :name
                            """)
                    .param("name", PHYSICAL_TABLE)
                    .query(Integer.class)
                    .single();
        }

        FormRow readForm(UUID formId) {
            return jdbc.sql(
                            """
                            select draft_status, running_version
                              from business_meta.form
                             where form_id = :id
                            """)
                    .param("id", formId)
                    .query((rs, row) -> new FormRow(
                            rs.getString("draft_status"), (Integer) rs.getObject("running_version")))
                    .single();
        }

        PublishedPointer readPointer(UUID formId) {
            FormRow form = readForm(formId);
            return jdbc.sql(
                            """
                            select version_id, status, snapshot_json::text as snapshot_json
                              from business_meta.form_version
                             where form_id = :id and status = 'RUNNING'
                            """)
                    .param("id", formId)
                    .query((rs, row) -> new PublishedPointer(
                            form.runningVersion(),
                            rs.getObject("version_id", UUID.class),
                            rs.getString("status"),
                            rs.getString("snapshot_json"),
                            countVersions(formId)))
                    .single();
        }

        int countVersions(UUID formId) {
            return jdbc.sql("select count(*) from business_meta.form_version where form_id = :id")
                    .param("id", formId)
                    .query(Integer.class)
                    .single();
        }

        int countRunning(UUID formId) {
            return jdbc.sql(
                            "select count(*) from business_meta.form_version where form_id = :id and status = 'RUNNING'")
                    .param("id", formId)
                    .query(Integer.class)
                    .single();
        }

        @Override
        public void close() throws Exception {
            AccessContextHolder.clear();
            postgres.close();
        }

        private static LedgerField field(UUID tenantId, UUID ledgerId, String code, FieldType type, int order) {
            Instant now = Instant.parse("2026-09-22T00:00:00Z");
            return new LedgerField(
                    UuidV7.generate(),
                    tenantId,
                    ledgerId,
                    code,
                    code,
                    type,
                    true,
                    false,
                    order,
                    now,
                    "designer",
                    now,
                    "designer",
                    null,
                    1L);
        }
    }

    private static void assertPostgres15(EmbeddedPostgres postgres) throws Exception {
        try (Connection connection = postgres.getPostgresDatabase().getConnection();
                Statement statement = connection.createStatement();
                var rs = statement.executeQuery("SHOW server_version")) {
            if (!rs.next() || !rs.getString(1).startsWith("15.")) {
                throw new AssertionError("expected PostgreSQL 15, got " + (rs.getRow() == 0 ? "?" : rs.getString(1)));
            }
        }
    }
}
