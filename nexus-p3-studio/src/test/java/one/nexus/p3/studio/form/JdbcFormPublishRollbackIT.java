package one.nexus.p3.studio.form;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.util.List;
import java.util.Set;
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

class JdbcFormPublishRollbackIT {
    private static final String TENANT = "11111111-1111-4111-8111-111111111111";

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void failedPublishRollsBackOnPostgres15() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.builder().start()) {
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
            AtomicInteger calls = new AtomicInteger();
            PublishHook hook = snapshot -> {
                if (calls.incrementAndGet() >= 2) {
                    throw new IllegalStateException("ddl failed");
                }
            };
            JdbcTransactionManager tx = new JdbcTransactionManager(dataSource);
            IsolationKernel kernel =
                    IsolationKernel.create(new TableRegistry(), Set.of("p3.studio"), new CollectingAuditSink());
            StudioIsolation.bindTenant(kernel, TENANT, "designer-a", "mem-a", "trace-pg");
            FormService service = new FormService(store, metadata, hook, Clock.systemUTC(), tx);

            FormRecord form = service.create("demo", "leave_form", "请假单", null, leaveBindings());
            service.validate(form.formId());
            service.publish(form.formId(), "v1");
            assertEquals(1, service.get(form.formId()).runningVersion());

            service.update(form.formId(), "请假单 v2", null, leaveBindings());
            service.validate(form.formId());
            NexusException failed = assertThrows(NexusException.class, () -> service.publish(form.formId(), "v2"));
            assertEquals(ErrorCode.FORM_PUBLISH_FAILED, failed.errorCode());

            assertEquals(1, service.get(form.formId()).runningVersion());
            assertEquals(1, service.versions(form.formId()).size());
            Integer versionRows = jdbc.sql(
                            "select count(*) from business_meta.form_version where form_id = :id")
                    .param("id", form.formId())
                    .query(Integer.class)
                    .single();
            assertEquals(1, versionRows);
            Integer runningRows = jdbc.sql(
                            "select count(*) from business_meta.form_version where form_id = :id and status = 'RUNNING'")
                    .param("id", form.formId())
                    .query(Integer.class)
                    .single();
            assertEquals(1, runningRows);
            assertTrue(service.publishLog(form.formId()).stream().anyMatch(log -> "ROLLED_BACK".equals(log.outcome())));
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

    private static List<FormBinding> leaveBindings() {
        return List.of(
                new FormBinding("title", "leave_request", "title", true, false, WidgetType.TEXT, 0),
                new FormBinding("days", "leave_request", "days", true, false, WidgetType.NUMBER, 1),
                new FormBinding("applicant", "leave_request", "applicant", true, false, WidgetType.PERSON, 2));
    }
}
