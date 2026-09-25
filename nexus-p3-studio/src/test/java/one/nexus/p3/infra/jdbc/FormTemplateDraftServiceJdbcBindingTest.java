package one.nexus.p3.infra.jdbc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import one.nexus.p1.domain.Actor;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.AuditPort;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p1.port.PlatformContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * H11: PostgreSQL JDBC cannot infer a SQL type for {@link Instant}. Every platform form template
 * write (create, update, validate, publish) must bind timestamps as {@link Timestamp}.
 */
class FormTemplateDraftServiceJdbcBindingTest {
    private static final Instant NOW = Instant.parse("2026-09-25T08:00:00Z");
    private static final UUID TEMPLATE_ID = UUID.randomUUID();

    private final List<Object[]> writes = new ArrayList<>();
    private FormTemplateDraftService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenAnswer(call -> {
            writes.add(Arrays.copyOfRange(call.getArguments(), 1, call.getArguments().length));
            return 1;
        });
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), any(Object[].class)))
                .thenAnswer(call -> call.<ResultSetExtractor<?>>getArgument(1).extractData(templateRow()));
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(call -> List.of(call.<RowMapper<?>>getArgument(1).mapRow(templateRow(), 0)));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);

        IsolationGuard isolation = mock(IsolationGuard.class);
        when(isolation.requirePlatformContext(anyString(), any())).thenReturn(new PlatformContext(true, "i4",
                new Actor(UUID.randomUUID(), null, "platform-admin", Set.of(PlatformDuty.SYSTEM_ADMIN)), "trace-i4", null));
        service = new FormTemplateDraftService(jdbc, JsonMapper.builder().build(), isolation, mock(AuditPort.class),
                () -> NOW, new one.nexus.p1.config.P1Properties());
    }

    @Test
    void createBindsTimestampsNotInstants() {
        service.create(write());
        assertWritesBindTimestamps(2);
    }

    @Test
    void updateBindsTimestampsNotInstants() {
        service.update(TEMPLATE_ID, write());
        assertWritesBindTimestamps(2);
    }

    @Test
    void validateBindsTimestampsNotInstants() {
        assertTrue(service.validateDraft(TEMPLATE_ID).valid());
        assertWritesBindTimestamps(1);
    }

    @Test
    void publishBindsTimestampsNotInstants() {
        service.publishDraft(TEMPLATE_ID);
        assertWritesBindTimestamps(2);
    }

    private void assertWritesBindTimestamps(int minimumWrites) {
        assertTrue(writes.size() >= minimumWrites, () -> "expected at least " + minimumWrites + " writes, got " + writes.size());
        long timestamps = 0;
        for (Object[] args : writes) {
            assertFalse(Arrays.stream(args).anyMatch(Instant.class::isInstance), () -> Arrays.toString(args));
            timestamps += Arrays.stream(args).filter(Timestamp.from(NOW)::equals).count();
        }
        assertTrue(timestamps > 0, "timestamp columns must be bound as java.sql.Timestamp");
    }

    private static FormTemplateDraftService.TemplateWrite write() {
        return new FormTemplateDraftService.TemplateWrite("i4_leave", "I4 请假", null,
                List.of(new FormTemplateDraftService.FieldWrite("title", "事由", "TEXT", true, null)));
    }

    private static ResultSet templateRow() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true, false);
        when(rs.getInt(anyInt())).thenReturn(1);
        when(rs.getInt("position")).thenReturn(0);
        when(rs.getString(1)).thenReturn("VALIDATED");
        when(rs.getString("status")).thenReturn("DRAFT");
        when(rs.getString("code")).thenReturn("i4_leave");
        when(rs.getString("name")).thenReturn("I4 请假");
        when(rs.getString("field_key")).thenReturn("title");
        when(rs.getString("label")).thenReturn("事由");
        when(rs.getString("field_type")).thenReturn("TEXT");
        when(rs.getBoolean("required")).thenReturn(true);
        when(rs.getObject(anyString(), eq(UUID.class))).thenReturn(TEMPLATE_ID);
        when(rs.getTimestamp(anyString())).thenReturn(Timestamp.from(NOW));
        when(rs.getLong("version")).thenReturn(1L);
        return rs;
    }
}
