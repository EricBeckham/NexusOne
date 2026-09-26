package one.nexus.p4.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import one.nexus.p4.market.MarketModels.Installation;
import one.nexus.p4.market.MarketModels.InstallationStatus;
import one.nexus.p4.market.MarketModels.UpgradeFact;
import one.nexus.p4.market.MarketModels.UpgradeFacts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** XB7: upgrade facts are null (暂无) where nothing declares them; nothing claims to have been checked. */
class MarketUpgradeFactsTest {
    private static final UUID ALPHA = UUID.fromString("01900000-0000-7000-8000-000000000031");
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
                Actor.person("xb7"), ALPHA.toString(), ALPHA.toString(), Set.of(ALPHA.toString())), "xb7-trace");
        return AccessContextHolder.call(context, call);
    }

    @Test
    void undeclaredFactsAreNullWithAReasonAndRollbackDescribesTheVersionSwitch() {
        UUID id = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        when(market.installationForCurrentTenant(id)).thenReturn(Optional.of(
                new Installation(id, ALPHA, UUID.randomUUID(), version, InstallationStatus.INSTALLED, NOW, NOW)));

        UpgradeFacts facts = asAlpha(() -> service.upgradeFacts(id));

        assertEquals(id, facts.installationId());
        assertEquals(version, facts.currentVersionId());
        assertEquals(List.of("compatibility", "schema_change", "data_migration", "rollback"),
                facts.facts().stream().map(UpgradeFact::key).toList());
        assertEquals(List.of("兼容范围", "Schema 变更", "数据迁移", "回滚能力"),
                facts.facts().stream().map(UpgradeFact::label).toList());
        for (UpgradeFact fact : facts.facts().subList(0, 3)) {
            assertNull(fact.value(), fact.key() + " must not be invented");
            assertTrue(!fact.note().isBlank());
        }
        UpgradeFact rollback = facts.facts().get(3);
        assertEquals("仅切换版本", rollback.value());
        assertTrue(rollback.note().contains("数据不会随之回滚"));
        // No fact may read as "already checked / passed".
        for (UpgradeFact fact : facts.facts()) {
            String text = String.valueOf(fact.value()) + fact.note();
            for (String claim : List.of("已检查", "已通过", "已迁移", "成功")) {
                assertTrue(!text.contains(claim), fact.key() + " claims " + claim);
            }
        }
    }

    @Test
    void missingInstallationIsNotFoundAndNoContextIsForbidden() {
        UUID id = UUID.randomUUID();
        when(market.installationForCurrentTenant(id)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, asAlpha(() -> assertThrows(ResponseStatusException.class,
                () -> service.upgradeFacts(id)).getStatusCode()));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.upgradeFacts(id)).getStatusCode());
    }
}