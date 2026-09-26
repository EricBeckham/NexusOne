package one.nexus.p4.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import one.nexus.p1.port.TenantRepository;
import one.nexus.p4.market.MarketModels.AppVersion;
import one.nexus.p4.market.MarketModels.Installation;
import one.nexus.p4.market.MarketModels.InstallationStatus;
import one.nexus.p4.market.MarketModels.UpgradeStepStatus;
import one.nexus.p4.market.MarketModels.UpgradeSteps;
import one.nexus.p4.market.MarketModels.VersionStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** XB6: the upgrade-steps read is honest — no step records, so every step is UNKNOWN (never DONE). */
class MarketUpgradeStepsTest {
    private static final UUID ALPHA = UUID.fromString("01900000-0000-7000-8000-000000000021");
    private static final Instant NOW = Instant.parse("2026-09-26T00:00:00Z");
    private final MarketRepository market = mock(MarketRepository.class);
    private final MarketService service = new MarketService(market, mock(TenantRepository.class), Clock.fixed(NOW, ZoneOffset.UTC));
    private final IsolationKernel kernel = IsolationKernel.create(new TableRegistry().freeze(), Set.of(), new CollectingAuditSink());

    @AfterEach
    void clearContext() {
        AccessContextHolder.clear();
    }

    private <T> T asAlpha(java.util.concurrent.Callable<T> call) {
        var context = kernel.tenants().issue(new AuthenticatedMembership(
                Actor.person("xb6"), ALPHA.toString(), ALPHA.toString(), Set.of(ALPHA.toString())), "xb6-trace");
        return AccessContextHolder.call(context, call);
    }

    private Installation installed(UUID id, UUID appId, UUID versionId) {
        return new Installation(id, ALPHA, appId, versionId, InstallationStatus.INSTALLED, NOW, NOW);
    }

    @Test
    void returnsTheSixStepsAllUnknownWithAPlainReason() {
        UUID id = UUID.randomUUID();
        when(market.installationForCurrentTenant(id)).thenReturn(Optional.of(installed(id, UUID.randomUUID(), UUID.randomUUID())));

        UpgradeSteps steps = asAlpha(() -> service.upgradeSteps(id));

        assertEquals(id, steps.installationId());
        assertFalse(steps.recorded());
        assertTrue(steps.note().contains("没有被记录"));
        assertEquals(List.of("precheck", "dependency", "schema_migration", "config", "switch", "verify"),
                steps.steps().stream().map(MarketModels.UpgradeStep::key).toList());
        assertEquals(List.of("预检", "依赖检查", "结构扩展与迁移", "配置", "切换", "验证"),
                steps.steps().stream().map(MarketModels.UpgradeStep::label).toList());
        assertTrue(steps.steps().stream().allMatch(step -> step.status() == UpgradeStepStatus.UNKNOWN));
    }

    @Test
    void anUpgradeDoesNotTurnTheStepsGreen() {
        UUID id = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        when(market.installationForCurrentTenant(id)).thenReturn(Optional.of(installed(id, appId, UUID.randomUUID())));
        when(market.version(target)).thenReturn(Optional.of(new AppVersion(target, appId, "2.0.0", VersionStatus.PUBLISHED, null, NOW, NOW)));

        asAlpha(() -> service.upgrade(id, target));
        UpgradeSteps steps = asAlpha(() -> service.upgradeSteps(id));

        assertFalse(steps.recorded());
        assertTrue(steps.steps().stream().noneMatch(step -> step.status() == UpgradeStepStatus.DONE));
    }

    @Test
    void foreignOrMissingInstallationIsNotFoundAndNoContextIsForbidden() {
        UUID id = UUID.randomUUID();
        when(market.installationForCurrentTenant(id)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, asAlpha(() -> assertThrows(ResponseStatusException.class,
                () -> service.upgradeSteps(id)).getStatusCode()));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.upgradeSteps(id)).getStatusCode());
    }
}