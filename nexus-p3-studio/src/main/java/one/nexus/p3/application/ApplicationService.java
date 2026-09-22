package one.nexus.p3.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import one.nexus.common.id.UuidV7;
import one.nexus.p3.domain.Application;
import one.nexus.p3.domain.ApplicationKind;
import one.nexus.p3.domain.ApplicationStatus;
import one.nexus.p3.domain.LedgerIdentifiers;
import one.nexus.p3.domain.P3Exception;
import one.nexus.p3.infra.isolation.P3RequestContext;
import one.nexus.p3.port.ApplicationRepository;
import one.nexus.p3.port.QuotaHook;

@Service
public class ApplicationService {
    private final ApplicationRepository applications;
    private final QuotaHook quotas;

    public ApplicationService(ApplicationRepository applications, QuotaHook quotas) {
        this.applications = applications;
        this.quotas = quotas;
    }

    @Transactional
    public Application create(String appCode, String displayName, ApplicationKind kind) {
        P3RequestContext.Snapshot ctx = P3RequestContext.require();
        String code = LedgerIdentifiers.normalizeCode(appCode);
        if (displayName == null || displayName.isBlank()) {
            throw P3Exception.queryInvalid("display_name is required");
        }
        if (applications.findByCode(ctx.tenantId(), code).isPresent()) {
            throw P3Exception.appConflict(code);
        }
        Instant now = Instant.now();
        Application application = new Application(
                UuidV7.generate(),
                ctx.tenantId(),
                ctx.tenantCode(),
                code,
                displayName.trim(),
                kind == null ? ApplicationKind.CUSTOM : kind,
                ApplicationStatus.ACTIVE,
                now,
                ctx.actorId(),
                now,
                ctx.actorId(),
                null,
                1);
        if (application.kind() == ApplicationKind.CUSTOM) {
            quotas.consumeApp(ctx.tenantId(), "p3.app:" + ctx.tenantId() + ":" + code);
        }
        applications.insert(application);
        return application;
    }

    @Transactional(readOnly = true)
    public List<Application> list() {
        return applications.list(P3RequestContext.require().tenantId());
    }

    @Transactional(readOnly = true)
    public Application get(UUID id) {
        return applications.findById(P3RequestContext.require().tenantId(), id)
                .orElseThrow(() -> P3Exception.appNotFound(id));
    }
}
