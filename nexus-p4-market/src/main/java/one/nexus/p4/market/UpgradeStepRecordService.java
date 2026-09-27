package one.nexus.p4.market;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import one.nexus.p1.domain.AuditEvent;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.AuditPort;
import one.nexus.p1.port.ClockPort;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p1.port.PlatformContext;
import one.nexus.p4.market.MarketModels.Installation;
import one.nexus.p4.market.MarketModels.InstallationStatus;
import one.nexus.p4.market.MarketModels.UpgradeStepRecord;
import one.nexus.p4.market.MarketModels.UpgradeStepStatus;
import one.nexus.p4.market.MarketModels.UpgradeSteps;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * DPO-L6: the controlled operator action that is the only source of a non-UNKNOWN upgrade step. A platform
 * SYSTEM_ADMIN records one step of the installation's current version with a reason; the record is appended and
 * audited. There is no scheduler, no automatic completion and no way to store UNKNOWN.
 */
@Service
@ConditionalOnBean({IsolationGuard.class, AuditPort.class, ClockPort.class})
public class UpgradeStepRecordService {
    static final Set<String> STEP_KEYS = UpgradeSteps.CATALOG.stream().map(step -> step[0]).collect(Collectors.toUnmodifiableSet());

    private final MarketRepository market;
    private final IsolationGuard isolation;
    private final AuditPort audit;
    private final ClockPort clock;
    private final UUID platformTenantId;

    public UpgradeStepRecordService(MarketRepository market, IsolationGuard isolation, AuditPort audit, ClockPort clock,
            one.nexus.p1.config.P1Properties properties) {
        this.market = market;
        this.isolation = isolation;
        this.audit = audit;
        this.clock = clock;
        this.platformTenantId = properties.getPlatformTenant().getId();
    }

    public record StepRecordRequest(String status, String reason, String note) {}

    @Transactional
    public UpgradeStepRecord record(UUID installationId, String stepKey, StepRecordRequest request) {
        PlatformContext actor = isolation.requirePlatformContext("upgrade-step.record", PlatformDuty.SYSTEM_ADMIN);
        if (actor == null || actor.actor() == null || actor.actor().subjectId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "a named operator is required to record an upgrade step");
        }
        if (!STEP_KEYS.contains(stepKey)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown upgrade step: " + stepKey);
        }
        UpgradeStepStatus status = status(request == null ? null : request.status());
        String reason = request == null || request.reason() == null ? "" : request.reason().trim();
        if (reason.isEmpty() || reason.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reason is required (at most 500 characters)");
        }
        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
        Installation installation = market.installationForPlatform(installationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "installation not found"));
        if (installation.status() != InstallationStatus.INSTALLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "only an installed product has upgrade steps to record");
        }
        UpgradeStepRecord record = new UpgradeStepRecord(UUID.randomUUID(), installation.tenantId(), installation.id(),
                installation.versionId(), stepKey, status, clock.now(), actor.actor().subjectId(), reason, note);
        market.saveUpgradeStepRecord(record);
        audit.append(new AuditEvent(UUID.randomUUID(), "market.upgrade_step.recorded", AuditEvent.VERSION, record.recordedAt(),
                actor.auditTenantId(platformTenantId), actor.actor().subjectId(), actor.actorHomeTenantId(), true,
                actor.reason(), actor.traceId(), actor.traceId(), null, AuditEvent.OUTCOME_ACCEPTED,
                "installation", installation.id().toString(),
                Map.of("step", stepKey, "status", status.name(), "version_id", installation.versionId().toString(),
                        "tenant_id", installation.tenantId().toString())));
        return record;
    }

    private static UpgradeStepStatus status(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (value) {
            case "ACTIVE" -> UpgradeStepStatus.ACTIVE;
            case "DONE" -> UpgradeStepStatus.DONE;
            case "FAILED" -> UpgradeStepStatus.FAILED;
            case "UNKNOWN" -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "UNKNOWN is the absence of a record and cannot be recorded");
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be ACTIVE, DONE or FAILED");
        };
    }
}
