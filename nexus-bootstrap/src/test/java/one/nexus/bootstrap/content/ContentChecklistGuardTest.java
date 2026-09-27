package one.nexus.bootstrap.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import one.nexus.bootstrap.content.ContentChecklistService.StepRequest;
import one.nexus.p1.config.P1Properties;
import one.nexus.p1.domain.Actor;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.AuditPort;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p1.port.PlatformContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** DL-L4: apply-one-step has exactly the DL-L1 authorisation and refuses before touching the database. */
class ContentChecklistGuardTest {
    private final IsolationGuard isolation = mock(IsolationGuard.class);
    private final JdbcClient jdbc = mock(JdbcClient.class);
    private final AuditPort audit = mock(AuditPort.class);

    @SuppressWarnings("unchecked")
    private ContentChecklistService service(MockEnvironment env) {
        ContentBootstrapService bootstrap = new ContentBootstrapService(env, jdbc, mock(TransactionTemplate.class),
                mock(ObjectProvider.class), isolation, audit, () -> Instant.EPOCH, new P1Properties());
        return new ContentChecklistService(jdbc, mock(ObjectProvider.class), isolation, bootstrap);
    }

    private static HttpStatus status(Throwable e) {
        return (HttpStatus) ((ResponseStatusException) e).getStatusCode();
    }

    @Test
    void closedWindowNoAdminAnonymousOrNoReasonAreAllRefused() {
        when(isolation.requirePlatformContext(anyString(), eq(PlatformDuty.SYSTEM_ADMIN)))
                .thenReturn(new PlatformContext(true, "prep", new Actor(UUID.randomUUID(), null, "ops", Set.of(PlatformDuty.SYSTEM_ADMIN)), "t", null));
        assertThatThrownBy(() -> service(new MockEnvironment()).apply("tenant", new StepRequest("r")))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.FORBIDDEN))
                .hasMessageContaining("NEXUS_CONTENT_BOOTSTRAP_ENABLED");
        MockEnvironment open = new MockEnvironment().withProperty("nexus.content-bootstrap.enabled", "true");
        assertThatThrownBy(() -> service(open).apply("tenant", new StepRequest(" ")))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.BAD_REQUEST));

        when(isolation.requirePlatformContext(anyString(), eq(PlatformDuty.SYSTEM_ADMIN)))
                .thenReturn(new PlatformContext(true, "prep", null, "t", null));
        assertThatThrownBy(() -> service(open).apply("tenant", new StepRequest("r")))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.FORBIDDEN));

        when(isolation.requirePlatformContext(anyString(), eq(PlatformDuty.SYSTEM_ADMIN)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "platform context required"));
        assertThatThrownBy(() -> service(open).apply("tenant", new StepRequest("r")))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service(open).checklist())
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(jdbc, audit);
    }

    @Test
    void demoSwitchesRefuseStartJustLikeDlL1() {
        MockEnvironment env = new MockEnvironment().withProperty("nexus.content-bootstrap.enabled", "true")
                .withProperty("nexus.demo.enabled", "true");
        assertThatThrownBy(() -> service(env)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NEXUS_DEMO_ENABLED");
    }
}
