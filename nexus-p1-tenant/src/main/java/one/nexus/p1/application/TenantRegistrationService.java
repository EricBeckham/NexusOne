package one.nexus.p1.application;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import one.nexus.p1.application.TenantLifecycleService.CreateTenantCommand;
import one.nexus.p1.application.TenantLifecycleService.CreatedTenant;
import one.nexus.p1.domain.AuditEvent;
import one.nexus.p1.domain.DomainException;
import one.nexus.p1.domain.ErrorCode;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.domain.RegistrationStatus;
import one.nexus.p1.domain.TenantRegistrationApplication;
import one.nexus.p1.port.AuditPort;
import one.nexus.p1.port.ClockPort;
import one.nexus.p1.port.IdGenerator;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p1.port.PlatformContext;
import one.nexus.p1.port.TenantRegistrationRepository;

/**
 * Thin tenant self-registration: public apply, platform approve/reject.
 * Approve reuses {@link TenantLifecycleService#create} (admin init + default pack).
 */
@Service
public class TenantRegistrationService {

    private final TenantRegistrationRepository registrations;
    private final TenantLifecycleService lifecycle;
    private final IsolationGuard isolation;
    private final AuditPort audit;
    private final IdGenerator ids;
    private final ClockPort clock;

    public TenantRegistrationService(
            TenantRegistrationRepository registrations,
            TenantLifecycleService lifecycle,
            IsolationGuard isolation,
            AuditPort audit,
            IdGenerator ids,
            ClockPort clock) {
        this.registrations = registrations;
        this.lifecycle = lifecycle;
        this.isolation = isolation;
        this.audit = audit;
        this.ids = ids;
        this.clock = clock;
    }

    /** Public/gated apply — no platform duty. Honest validation only. */
    @Transactional
    public TenantRegistrationApplication apply(ApplyCommand command) {
        requireText(command.orgName(), "org_name");
        requireText(command.adminDisplayName(), "admin_display_name");
        requireEmail(command.adminEmail());
        String email = command.adminEmail().trim().toLowerCase();
        if (registrations.existsPendingEmail(email)) {
            throw new DomainException(
                    ErrorCode.TENANT_CODE_CONFLICT,
                    "a pending registration already exists for this admin email",
                    Map.of("admin_email", email));
        }
        Instant now = clock.now();
        TenantRegistrationApplication application = TenantRegistrationApplication.pending(
                ids.next(),
                command.orgName(),
                command.adminDisplayName(),
                email,
                command.contactNote(),
                now);
        registrations.insert(application);
        IsolationGuard.IsolationSnapshot snapshot = isolation.current();
        UUID actorId = snapshot.actorId() != null ? snapshot.actorId() : UUID.fromString("00000000-0000-7000-8000-000000000000");
        String trace = snapshot.traceId() != null ? snapshot.traceId() : ids.next().toString();
        audit.append(new AuditEvent(
                ids.next(),
                AuditEvent.TENANT_REGISTRATION_APPLIED,
                AuditEvent.VERSION,
                now,
                UUID.fromString("00000000-0000-7000-8000-000000000001"),
                actorId,
                null,
                false,
                null,
                trace,
                trace,
                null,
                AuditEvent.OUTCOME_ACCEPTED,
                "tenant_registration",
                application.id().toString(),
                Map.of(
                        "org_name", application.orgName(),
                        "admin_email", application.adminEmail())));
        return application;
    }

    @Transactional(readOnly = true)
    public List<TenantRegistrationApplication> list(RegistrationStatus status) {
        isolation.requirePlatformContext("tenant.registration.list", PlatformDuty.SYSTEM_ADMIN);
        return registrations.list(status);
    }

    @Transactional(readOnly = true)
    public TenantRegistrationApplication get(UUID id) {
        isolation.requirePlatformContext("tenant.registration.get", PlatformDuty.SYSTEM_ADMIN);
        return require(id);
    }

    @Transactional
    public TenantRegistrationApplication approve(UUID id, String note) {
        PlatformContext platform = isolation.requirePlatformContext(
                "tenant.registration.approve", PlatformDuty.SYSTEM_ADMIN);
        TenantRegistrationApplication current = require(id);
        Instant now = clock.now();
        CreatedTenant created = lifecycle.create(new CreateTenantCommand(
                current.orgName(),
                null,
                current.orgName(),
                current.adminDisplayName(),
                current.adminEmail(),
                null));
        TenantRegistrationApplication next = current.approve(
                created.tenant().id(), now, platform.actor().subjectId(), note);
        registrations.update(next);
        audit.append(event(AuditEvent.TENANT_REGISTRATION_APPROVED, next, platform, AuditEvent.OUTCOME_ACCEPTED,
                Map.of(
                        "resulting_tenant_id", created.tenant().id().toString(),
                        "tenant_code", created.tenant().code())));
        return next;
    }

    @Transactional
    public TenantRegistrationApplication reject(UUID id, String note) {
        PlatformContext platform = isolation.requirePlatformContext(
                "tenant.registration.reject", PlatformDuty.SYSTEM_ADMIN);
        TenantRegistrationApplication current = require(id);
        Instant now = clock.now();
        TenantRegistrationApplication next = current.reject(now, platform.actor().subjectId(), note);
        registrations.update(next);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("review_note", next.reviewNote());
        audit.append(event(AuditEvent.TENANT_REGISTRATION_REJECTED, next, platform, AuditEvent.OUTCOME_REJECTED, details));
        return next;
    }

    private TenantRegistrationApplication require(UUID id) {
        return registrations.findById(id).orElseThrow(() -> new DomainException(
                ErrorCode.TENANT_NOT_FOUND,
                "registration application not found",
                Map.of("application_id", id.toString())));
    }

    private AuditEvent event(
            String type,
            TenantRegistrationApplication application,
            PlatformContext platform,
            String outcome,
            Map<String, Object> details) {
        return new AuditEvent(
                ids.next(),
                type,
                AuditEvent.VERSION,
                clock.now(),
                application.resultingTenantId() != null
                        ? application.resultingTenantId()
                        : UUID.fromString("00000000-0000-7000-8000-000000000001"),
                platform.actor().subjectId(),
                platform.actorHomeTenantId(),
                true,
                platform.reason(),
                platform.traceId(),
                platform.traceId(),
                null,
                outcome,
                "tenant_registration",
                application.id().toString(),
                details);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainException(ErrorCode.TENANT_INVALID_CODE, field + " is required");
        }
    }

    private static void requireEmail(String value) {
        if (value == null || value.isBlank() || !value.contains("@")) {
            throw new DomainException(ErrorCode.TENANT_INVALID_CODE, "admin_email is required");
        }
    }

    public record ApplyCommand(String orgName, String adminDisplayName, String adminEmail, String contactNote) {}
}