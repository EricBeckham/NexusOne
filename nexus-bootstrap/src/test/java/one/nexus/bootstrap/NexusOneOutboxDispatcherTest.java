package one.nexus.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import one.nexus.cache.IsolationSupport;
import one.nexus.event.DeliveryLagProbe;
import one.nexus.event.OutboxDispatcher;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.notification.InAppDeliveryHandler;
import one.nexus.notification.Notification;
import one.nexus.notification.NotificationChannel;
import one.nexus.notification.NotificationService;
import one.nexus.notification.NotificationStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Product {@link NexusOneApplication} with the <strong>default</strong>
 * {@code nexus.execution.dispatcher-enabled=true}. Proves PENDING
 * {@code notify.inbox} becomes DISPATCHED and the inbox row is readable.
 * Worker/Quartz stay off so this is dispatcher-only, not a G18 close.
 */
@SpringBootTest(classes = NexusOneApplication.class)
@AutoConfigureMockMvc
class NexusOneOutboxDispatcherTest {
    private static final String TENANT = "11111111-1111-4111-8111-111111111111";
    private static final String ACTOR = "dispatcher-user";
    private static final String TRACE = "0199a1c0-0000-7000-8000-00000000d509";
    private static EmbeddedPostgres postgres;

    @Autowired
    OutboxDispatcher dispatcher;

    @Autowired
    NotificationService notifications;

    @Autowired
    IsolationKernel isolationKernel;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockMvc mvc;

    @BeforeAll
    static void startPostgres() throws Exception {
        postgres = EmbeddedPostgres.builder().start();
        try (Connection connection = postgres.getPostgresDatabase().getConnection();
                Statement statement = connection.createStatement();
                var rs = statement.executeQuery("SHOW server_version")) {
            rs.next();
            if (!rs.getString(1).startsWith("15.")) {
                throw new AssertionError("expected PostgreSQL 15, got " + rs.getString(1));
            }
        }
    }

    @AfterAll
    static void stopPostgres() throws Exception {
        if (postgres != null) {
            postgres.close();
        }
    }

    @AfterEach
    void clearContext() {
        AccessContextHolder.clear();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:postgresql://127.0.0.1:" + postgres.getPort() + "/postgres");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("nexus.notify.outbox", () -> "auto");
        registry.add("nexus.execution.worker-enabled", () -> "false");
        registry.add("nexus.execution.quartz-auto-startup", () -> "false");
        registry.add("nexus.cache.mode", () -> "memory");
    }

    @Test
    void productDispatcherDeliversNotifyInboxWithinSlo() throws Exception {
        assertTrue(dispatcher.isAutoStartup(), "product yaml must leave dispatcher-enabled=true");
        assertTrue(dispatcher.isRunning(), "SmartLifecycle should have started the poller");
        assertTrue(
                dispatcher.handlers().stream().anyMatch(InAppDeliveryHandler.class::isInstance),
                "M0-10 InAppDeliveryHandler must be registered on the product dispatcher");

        IsolationSupport.bindTenant(isolationKernel, TENANT, ACTOR, "mem-" + TENANT, TRACE);
        Notification pending = notifications.enqueue(
                "approval.task.assigned",
                ACTOR,
                NotificationChannel.IN_APP,
                Map.of("taskTitle", "年假", "actor", "alice"));
        assertEquals(NotificationStatus.PENDING, pending.status());
        UUID outboxId = pending.outboxMessageId();
        assertEquals("PENDING", jdbc.queryForObject(
                "select status from platform.exec_outbox where id = ?",
                String.class,
                outboxId));

        Instant deadline = Instant.now().plusSeconds(5);
        String outboxStatus = "PENDING";
        while (Instant.now().isBefore(deadline)) {
            outboxStatus = jdbc.queryForObject(
                    "select status from platform.exec_outbox where id = ?",
                    String.class,
                    outboxId);
            if ("DISPATCHED".equals(outboxStatus)) {
                break;
            }
            Thread.sleep(25);
        }
        assertEquals("DISPATCHED", outboxStatus, "poller did not deliver within 5s SLO window");
        assertEquals("DELIVERED", jdbc.queryForObject(
                "select status from notify.inbox where notification_id = ?",
                String.class,
                pending.id()));
        Long startMs = jdbc.queryForObject(
                """
                select (extract(epoch from (dispatched_at - created_at)) * 1000)::bigint
                  from platform.exec_outbox where id = ?
                """,
                Long.class,
                outboxId);
        assertTrue(startMs != null && startMs <= DeliveryLagProbe.TARGET_P95_MS,
                "start-of-delivery " + startMs + "ms exceeds 5000ms (Zonky PG 15, product poll-interval 500ms)");
        assertFalse(dispatcher.lastCycleLagAlarm());

        IsolationSupport.bindTenant(isolationKernel, TENANT, ACTOR, "mem-" + TENANT, TRACE);
        assertEquals(1, notifications.listMine().size());
        assertEquals(NotificationStatus.DELIVERED, notifications.listMine().getFirst().status());

        mvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("DELIVERED"))
                .andExpect(jsonPath("$[0].template_code").value("approval.task.assigned"));
    }
}
