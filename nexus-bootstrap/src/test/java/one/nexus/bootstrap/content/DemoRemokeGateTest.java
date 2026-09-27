package one.nexus.bootstrap.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import one.nexus.bootstrap.content.DemoRemokeService.RemokeRequest;
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

/** DL-L2: the gate matrix — exact server allow, platform admin, named operator — refuses before any DB work. */
class DemoRemokeGateTest {
    private final IsolationGuard isolation = mock(IsolationGuard.class);
    private final JdbcClient jdbc = mock(JdbcClient.class);
    private final AuditPort audit = mock(AuditPort.class);

    @SuppressWarnings("unchecked")
    private DemoRemokeService service(MockEnvironment env) {
        return new DemoRemokeService(env, jdbc, mock(TransactionTemplate.class), mock(ObjectProvider.class), isolation,
                audit, () -> Instant.EPOCH, new P1Properties());
    }

    private static HttpStatus status(Throwable e) {
        return (HttpStatus) ((ResponseStatusException) e).getStatusCode();
    }

    @Test
    void withoutTheExactServerAllowNothingHappens() {
        for (String allow : new String[] {null, "", "0", "true", "yes", " 1", "1 ", "01"}) {
            MockEnvironment env = new MockEnvironment();
            if (allow != null) env.setProperty("nexus.demo-host.allow", allow);
            assertThatThrownBy(() -> service(env).plan())
                    .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.FORBIDDEN))
                    .hasMessage("403 FORBIDDEN \"NEXUS_DEMO_HOST_ALLOW is not set to 1 on the server\"");
            assertThatThrownBy(() -> service(env).execute(new RemokeRequest("t", "r")))
                    .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.FORBIDDEN));
        }
        verifyNoInteractions(isolation, jdbc, audit);
    }

    @Test
    void allowIsNotAuthorisationPlatformAdminAndANamedOperatorAreStillRequired() {
        MockEnvironment env = new MockEnvironment().withProperty("nexus.demo-host.allow", "1");
        when(isolation.requirePlatformContext("demo-remoke.plan", PlatformDuty.SYSTEM_ADMIN))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "platform context required"));
        assertThatThrownBy(() -> service(env).plan()).satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.FORBIDDEN));

        when(isolation.requirePlatformContext(eq("demo-remoke.execute"), eq(PlatformDuty.SYSTEM_ADMIN)))
                .thenReturn(new PlatformContext(true, "remoke", null, "trace", null));
        assertThatThrownBy(() -> service(env).execute(new RemokeRequest("t", "r")))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.FORBIDDEN))
                .hasMessageContaining("named platform operator");
        verifyNoInteractions(jdbc, audit);
    }

    @Test
    void aReasonAndAKnownTokenAreRequired() {
        MockEnvironment env = new MockEnvironment().withProperty("nexus.demo-host.allow", "1");
        when(isolation.requirePlatformContext(anyString(), eq(PlatformDuty.SYSTEM_ADMIN)))
                .thenReturn(new PlatformContext(true, "remoke", new Actor(UUID.randomUUID(), null, "ops",
                        Set.of(PlatformDuty.SYSTEM_ADMIN)), "trace", null));
        assertThatThrownBy(() -> service(env).execute(null)).satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service(env).execute(new RemokeRequest("t", " ")))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service(env).execute(new RemokeRequest(null, "clean for rerun")))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.FORBIDDEN))
                .hasMessageContaining("confirmation token");
        assertThatThrownBy(() -> service(env).execute(new RemokeRequest("made-up", "clean for rerun")))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(jdbc);
        verify(audit, never()).append(org.mockito.ArgumentMatchers.any());
    }
}
