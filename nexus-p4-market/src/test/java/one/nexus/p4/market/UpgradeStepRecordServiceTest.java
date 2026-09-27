package one.nexus.p4.market;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import one.nexus.p1.domain.Actor;
import one.nexus.p1.domain.AuditEvent;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.AuditPort;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p1.port.PlatformContext;
import one.nexus.p4.market.MarketModels.Installation;
import one.nexus.p4.market.MarketModels.InstallationStatus;
import one.nexus.p4.market.MarketModels.UpgradeStepRecord;
import one.nexus.p4.market.MarketModels.UpgradeStepStatus;
import one.nexus.p4.market.UpgradeStepRecordService.StepRecordRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** DPO-L6: the controlled operator write — named operator, reason, current version, audited; never UNKNOWN. */
class UpgradeStepRecordServiceTest {
    private final MarketRepository market = mock(MarketRepository.class);
    private final IsolationGuard isolation = mock(IsolationGuard.class);
    private final AuditPort audit = mock(AuditPort.class);
    private final Instant now = Instant.parse("2026-09-27T09:00:00Z");
    private final UpgradeStepRecordService service = new UpgradeStepRecordService(market, isolation, audit, () -> now,
            new one.nexus.p1.config.P1Properties());
    private final UUID operator = UUID.randomUUID();
    private final UUID tenant = UUID.randomUUID();
    private final UUID installation = UUID.randomUUID();
    private final UUID version = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(isolation.requirePlatformContext(anyString(), eq(PlatformDuty.SYSTEM_ADMIN)))
                .thenReturn(new PlatformContext(true, "ops change 42", new Actor(operator, null, "ops", Set.of(PlatformDuty.SYSTEM_ADMIN)), "trace", null));
        when(market.installationForPlatform(installation)).thenReturn(Optional.of(
                new Installation(installation, tenant, UUID.randomUUID(), version, InstallationStatus.INSTALLED, now, now)));
    }

    private static ResponseStatusException status(Throwable e) {
        return (ResponseStatusException) e;
    }

    @Test
    void recordsTheStepForTheCurrentVersionWithOperatorReasonAndAudit() {
        UpgradeStepRecord record = service.record(installation, "precheck", new StepRecordRequest("done", " prechecked by ops ", ""));
        assertThat(record.status()).isEqualTo(UpgradeStepStatus.DONE);
        assertThat(record.versionId()).isEqualTo(version);
        assertThat(record.tenantId()).isEqualTo(tenant);
        assertThat(record.recordedBy()).isEqualTo(operator);
        assertThat(record.recordedAt()).isEqualTo(now);
        assertThat(record.reason()).isEqualTo("prechecked by ops");
        assertThat(record.note()).isNull();
        verify(market).saveUpgradeStepRecord(record);
        verify(audit).append(argThat((AuditEvent event) -> event.eventType().equals("market.upgrade_step.recorded")
                && event.tenantId() != null));
        verify(isolation).requirePlatformContext("upgrade-step.record", PlatformDuty.SYSTEM_ADMIN);
    }

    @Test
    void unknownBlankReasonUnknownStepAndUninstalledAreRefusedAndNothingIsStored() {
        assertThatThrownBy(() -> service.record(installation, "precheck", new StepRecordRequest("UNKNOWN", "r", null)))
                .satisfies(e -> assertThat(status(e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("absence of a record");
        assertThatThrownBy(() -> service.record(installation, "precheck", new StepRecordRequest("COMPLETED", "r", null)))
                .satisfies(e -> assertThat(status(e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.record(installation, "precheck", new StepRecordRequest("DONE", "  ", null)))
                .satisfies(e -> assertThat(status(e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("reason");
        assertThatThrownBy(() -> service.record(installation, "deploy", new StepRecordRequest("DONE", "r", null)))
                .satisfies(e -> assertThat(status(e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        UUID gone = UUID.randomUUID();
        when(market.installationForPlatform(gone)).thenReturn(Optional.of(
                new Installation(gone, tenant, UUID.randomUUID(), version, InstallationStatus.UNINSTALLED, now, now)));
        assertThatThrownBy(() -> service.record(gone, "verify", new StepRecordRequest("DONE", "r", null)))
                .satisfies(e -> assertThat(status(e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> service.record(UUID.randomUUID(), "verify", new StepRecordRequest("DONE", "r", null)))
                .satisfies(e -> assertThat(status(e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(market, never()).saveUpgradeStepRecord(any());
        verifyNoInteractions(audit);
    }

    @Test
    void anAnonymousPlatformContextCannotRecord() {
        when(isolation.requirePlatformContext(anyString(), eq(PlatformDuty.SYSTEM_ADMIN)))
                .thenReturn(new PlatformContext(true, "r", null, "trace", null));
        assertThatThrownBy(() -> service.record(installation, "verify", new StepRecordRequest("DONE", "r", null)))
                .satisfies(e -> assertThat(status(e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(market, never()).saveUpgradeStepRecord(any());
    }
}
