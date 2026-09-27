package one.nexus.p4.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void recordsAreTheOnlyWayOutOfUnknownAndOnlyForTheCurrentVersion() {
        UUID installation = UUID.randomUUID();
        UUID current = UUID.randomUUID();
        UUID earlier = UUID.randomUUID();
        MarketModels.UpgradeStepRecord oldDone = new MarketModels.UpgradeStepRecord(UUID.randomUUID(), TENANT, installation,
                earlier, "verify", UpgradeStepStatus.DONE, NOW, UUID.randomUUID(), "earlier upgrade", null);
        assertFalse(UpgradeSteps.fromRecords(installation, current, List.of()).recorded());
        UpgradeSteps ignored = UpgradeSteps.fromRecords(installation, current, List.of(oldDone));
        assertFalse(ignored.recorded(), "a record for an earlier version says nothing about this upgrade");
        assertTrue(ignored.steps().stream().allMatch(step -> step.status() == UpgradeStepStatus.UNKNOWN));

        MarketModels.UpgradeStepRecord active = new MarketModels.UpgradeStepRecord(UUID.randomUUID(), TENANT, installation,
                current, "precheck", UpgradeStepStatus.ACTIVE, NOW, UUID.randomUUID(), "started", null);
        MarketModels.UpgradeStepRecord done = new MarketModels.UpgradeStepRecord(UUID.randomUUID(), TENANT, installation,
                current, "precheck", UpgradeStepStatus.DONE, NOW.plusSeconds(60), UUID.randomUUID(), "prechecked by ops", null);
        UpgradeSteps steps = UpgradeSteps.fromRecords(installation, current, List.of(done, active, oldDone));
        assertTrue(steps.recorded());
        assertEquals(UpgradeStepStatus.DONE, steps.steps().get(0).status(), "latest record wins");
        assertEquals("prechecked by ops", steps.steps().get(0).reason());
        assertEquals(NOW.plusSeconds(60), steps.steps().get(0).recordedAt());
        assertTrue(steps.steps().subList(1, 6).stream().allMatch(step -> step.status() == UpgradeStepStatus.UNKNOWN
                && step.recordedAt() == null && step.reason() == null), "unrecorded steps stay UNKNOWN");
    }

    @Test
    void unknownCanNeverBeStoredAndNoOtherFactoryBuildsSteps() {
        assertThrows(IllegalArgumentException.class, () -> new MarketModels.UpgradeStepRecord(UUID.randomUUID(), TENANT,
                UUID.randomUUID(), UUID.randomUUID(), "verify", UpgradeStepStatus.UNKNOWN, NOW, UUID.randomUUID(), "x", null));
        List<String> factories = Arrays.stream(UpgradeSteps.class.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isStatic(m.getModifiers()) && m.getReturnType() == UpgradeSteps.class)
                .map(Method::getName).sorted().toList();
        assertEquals(List.of("fromRecords", "unrecorded"), factories);
    }

    @Test
    void theReadUsesOnlyRecordsOfTheInstallationsCurrentVersion() {
        MarketRepository market = mock(MarketRepository.class);
        MarketService service = new MarketService(market, mock(TenantRepository.class), Clock.fixed(NOW, ZoneOffset.UTC));
        IsolationKernel kernel = IsolationKernel.create(new TableRegistry().freeze(), Set.of(), new CollectingAuditSink());
        AccessContextHolder.set(kernel.tenants().issue(new AuthenticatedMembership(
                Actor.person("dpo-l6"), TENANT.toString(), TENANT.toString(), Set.of(TENANT.toString())), "dpo-l6-trace"));
        UUID id = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        when(market.installationForCurrentTenant(id)).thenReturn(Optional.of(
                new Installation(id, TENANT, UUID.randomUUID(), version, InstallationStatus.INSTALLED, NOW, NOW)));
        when(market.upgradeStepRecords(id, version)).thenReturn(List.of(new MarketModels.UpgradeStepRecord(UUID.randomUUID(),
                TENANT, id, version, "switch", UpgradeStepStatus.FAILED, NOW, UUID.randomUUID(), "switch failed", null)));
        UpgradeSteps steps = service.upgradeSteps(id);
        assertTrue(steps.recorded());
        assertEquals(UpgradeSteps.RECORDED_NOTE, steps.note());
        assertEquals(List.of("UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN", "FAILED", "UNKNOWN"),
                steps.steps().stream().map(step -> step.status().name()).toList());
    }

    @Test
    void theEndpointsTheWebClientCallsExist() throws NoSuchMethodException {
        Method steps = MarketController.class.getDeclaredMethod("upgradeSteps", UUID.class);
        Method facts = MarketController.class.getDeclaredMethod("upgradeFacts", UUID.class);
        assertEquals(List.of("/installations/{id}/upgrade-steps"), List.of(steps.getAnnotation(GetMapping.class).value()));
        assertEquals(List.of("/installations/{id}/upgrade-facts"), List.of(facts.getAnnotation(GetMapping.class).value()));
    }
}