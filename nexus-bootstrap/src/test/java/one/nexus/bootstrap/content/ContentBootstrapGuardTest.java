package one.nexus.bootstrap.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import one.nexus.bootstrap.content.ContentBootstrapService.BootstrapRequest;
import one.nexus.p1.config.P1Properties;
import one.nexus.p1.domain.Actor;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.AuditPort;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p1.port.PlatformContext;
import org.flowable.engine.RepositoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** DL-L1: every refusal path of the prod-safe bootstrap, before any row is touched; messages name variables only. */
class ContentBootstrapGuardTest {
    private final IsolationGuard isolation = mock(IsolationGuard.class);
    private final AuditPort audit = mock(AuditPort.class);
    private final JdbcClient jdbc = mock(JdbcClient.class);
    private final TransactionTemplate transaction = mock(TransactionTemplate.class);

    @SuppressWarnings("unchecked")
    private ContentBootstrapService service(MockEnvironment env) {
        return new ContentBootstrapService(env, jdbc, transaction, mock(ObjectProvider.class), isolation, audit,
                () -> Instant.EPOCH, new P1Properties());
    }

    private static MockEnvironment open() {
        return new MockEnvironment().withProperty("nexus.content-bootstrap.enabled", "true");
    }

    private void operator(UUID subject) {
        when(isolation.requirePlatformContext(anyString(), eq(PlatformDuty.SYSTEM_ADMIN)))
                .thenReturn(new PlatformContext(true, "bootstrap", subject == null ? null
                        : new Actor(subject, null, "ops", Set.of(PlatformDuty.SYSTEM_ADMIN)), "trace", null));
    }

    private static HttpStatus status(Throwable e) {
        return (HttpStatus) ((ResponseStatusException) e).getStatusCode();
    }

    @Test
    void theWindowCombinedWithADemoSwitchRefusesToStartNamingVariablesOnly() {
        for (MockEnvironment env : new MockEnvironment[] {
                open().withProperty("nexus.demo.enabled", "true"),
                open().withProperty("nexus.p1.demo-context.enabled", "true")}) {
            assertThatThrownBy(() -> service(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("NEXUS_CONTENT_BOOTSTRAP_ENABLED")
                    .hasMessageContaining("NEXUS_DEMO_ENABLED")
                    .satisfies(e -> assertThat(e.getMessage()).doesNotContain("=true").doesNotContain("true"));
        }
        MockEnvironment demoProfile = open();
        demoProfile.setActiveProfiles("demo");
        assertThatThrownBy(() -> service(demoProfile)).isInstanceOf(IllegalStateException.class);
        // Window closed: demo switches alone are none of this service's business (the local demo seed owns them).
        service(new MockEnvironment().withProperty("nexus.demo.enabled", "true"));
    }

    @Test
    void closedWindowIsRefusedByVariableName() {
        operator(UUID.randomUUID());
        assertThatThrownBy(() -> service(new MockEnvironment()).bootstrap(new BootstrapRequest("demo prep")))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.FORBIDDEN))
                .hasMessageContaining("NEXUS_CONTENT_BOOTSTRAP_ENABLED is not set to true");
        verifyNoInteractions(jdbc, audit);
    }

    @Test
    void platformAdminAndANamedOperatorAreRequired() {
        when(isolation.requirePlatformContext("content-bootstrap.apply", PlatformDuty.SYSTEM_ADMIN))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "platform context required"));
        assertThatThrownBy(() -> service(open()).bootstrap(new BootstrapRequest("demo prep")))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.FORBIDDEN));

        operator(null);
        assertThatThrownBy(() -> service(open()).bootstrap(new BootstrapRequest("demo prep")))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.FORBIDDEN))
                .hasMessageContaining("named platform operator");
        verifyNoInteractions(jdbc, audit);
    }

    @Test
    void aReasonIsRequired() {
        operator(UUID.randomUUID());
        for (BootstrapRequest request : new BootstrapRequest[] {null, new BootstrapRequest(" "), new BootstrapRequest("x".repeat(501))}) {
            assertThatThrownBy(() -> service(open()).bootstrap(request))
                    .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verifyNoInteractions(jdbc, audit);
        verify(transaction, never()).executeWithoutResult(any());
    }

    @Test
    void theStepsAreOrderedAndNeverIncludeAccountsPaymentOrUpgrades() {
        assertThat(ContentSteps.ORDER).extracting(ContentSteps.Step::key).containsExactly("tenant", "catalog", "process");
        assertThat(ContentSteps.ORDER).allSatisfy(step -> assertThat(step.key() + step.label())
                .doesNotContain("payment").doesNotContain("支付").doesNotContain("upgrade").doesNotContain("升级"));
        assertThat(ContentBootstrapService.NOTE).contains("IdP").contains("NEXUS_CONTENT_BOOTSTRAP_ENABLED");
        RepositoryService none = null;
        assertThat(ContentSteps.PROCESS.present(jdbc, none)).isFalse();
    }
}
