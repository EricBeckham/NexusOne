package one.nexus.p3.studio.form;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import one.nexus.p3.studio.StudioIsolation;
import one.nexus.p3.studio.meta.InMemoryLedgerMetadataCatalog;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.JdbcTransactionManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * G5 form-publish failed paths on PostgreSQL 15: validate-fail stays DRAFT;
 * mid-publish failure leaves the Running pointer and published snapshot unchanged.
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

    private static List<FormBinding> leaveBindings() {
        return List.of(
                new FormBinding("title", "leave_request", "title", true, false, WidgetType.TEXT, 0),
                new FormBinding("days", "leave_request", "days", true, false, WidgetType.NUMBER, 1),
                new FormBinding("applicant", "leave_request", "applicant", true, false, WidgetType.PERSON, 2));
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
