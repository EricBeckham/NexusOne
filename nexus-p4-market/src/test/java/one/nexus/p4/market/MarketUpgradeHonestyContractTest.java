package one.nexus.p4.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
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
import one.nexus.p4.market.MarketModels.UpgradeStepStatus;
import one.nexus.p4.market.MarketModels.UpgradeSteps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * XB13: contract for "no fake green" on upgrades. While nothing records step state, every path — any
 * installation status, before or after an upgrade — answers UNKNOWN with recorded=false; the status
 * vocabulary and the endpoint paths the web client calls are pinned.
 */
class MarketUpgradeHonestyContractTest {
    private static final UUID TENANT = UUID.fromString("01900000-0000-7000-8000-000000000041");
    private static final Instant NOW = Instant.parse("2026-09-26T00:00:00Z");

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void statusVocabularyIsExactlyTheWebSteppersFourWords() {
        assertEquals(List.of("DONE", "ACTIVE", "FAILED", "UNKNOWN"),
                Arrays.stream(UpgradeStepStatus.values()).map(Enum::name).toList());
    }

    @Test
    void theOnlyWayToBuildStepsWithoutARecordIsAllUnknown() {
        UpgradeSteps steps = UpgradeSteps.unrecorded(UUID.randomUUID());
        assertFalse(steps.recorded());
        assertEquals(6, steps.steps().size());
        assertTrue(steps.steps().stream().allMatch(step -> step.status() == UpgradeStepStatus.UNKNOWN));
        assertFalse(steps.note().isBlank(), "an unrecorded answer must say why");
    }

    @Test
    void everyInstallationStateAnswersUnknownNeverDone() {
        MarketRepository market = mock(MarketRepository.class);
        MarketService service = new MarketService(market, mock(TenantRepository.class), Clock.fixed(NOW, ZoneOffset.UTC));
        IsolationKernel kernel = IsolationKernel.create(new TableRegistry().freeze(), Set.of(), new CollectingAuditSink());
        AccessContextHolder.set(kernel.tenants().issue(new AuthenticatedMembership(
                Actor.person("xb13"), TENANT.toString(), TENANT.toString(), Set.of(TENANT.toString())), "xb13-trace"));
        for (InstallationStatus status : InstallationStatus.values()) {
            UUID id = UUID.randomUUID();
            when(market.installationForCurrentTenant(id)).thenReturn(Optional.of(
                    new Installation(id, TENANT, UUID.randomUUID(), UUID.randomUUID(), status, NOW, NOW)));
            UpgradeSteps steps = service.upgradeSteps(id);
            assertFalse(steps.recorded(), status.name());
            assertTrue(steps.steps().stream().noneMatch(step -> step.status() != UpgradeStepStatus.UNKNOWN), status.name());
        }
    }

    @Test
    void theEndpointsTheWebClientCallsExist() throws NoSuchMethodException {
        Method steps = MarketController.class.getDeclaredMethod("upgradeSteps", UUID.class);
        Method facts = MarketController.class.getDeclaredMethod("upgradeFacts", UUID.class);
        assertEquals(List.of("/installations/{id}/upgrade-steps"), List.of(steps.getAnnotation(GetMapping.class).value()));
        assertEquals(List.of("/installations/{id}/upgrade-facts"), List.of(facts.getAnnotation(GetMapping.class).value()));
    }
}