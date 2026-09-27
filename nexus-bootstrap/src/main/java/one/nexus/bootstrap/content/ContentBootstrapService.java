package one.nexus.bootstrap.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.bootstrap.profile.ProductionProfileGuard;
import one.nexus.p1.config.P1Properties;
import one.nexus.p1.domain.AuditEvent;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.AuditPort;
import one.nexus.p1.port.ClockPort;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p1.port.PlatformContext;
import org.flowable.engine.RepositoryService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * DL-L1: prod-safe content bootstrap. Creates the minimal demo content on an explicit platform-admin request, so a
 * prod / demo host never needs NEXUS_DEMO_ENABLED. It only runs while NEXUS_CONTENT_BOOTSTRAP_ENABLED is on (a
 * short window the operator opens and closes), it refuses to start together with any demo switch, and every
 * message names variables only.
 */
@Service
public class ContentBootstrapService {
    public static final String ENABLED = "nexus.content-bootstrap.enabled";
    public static final String ENABLED_ENV = "NEXUS_CONTENT_BOOTSTRAP_ENABLED";

    private final Environment env;
    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final ObjectProvider<RepositoryService> processes;
    private final IsolationGuard isolation;
    private final AuditPort audit;
    private final ClockPort clock;
    private final UUID platformTenantId;

    public ContentBootstrapService(Environment env, JdbcClient jdbc, TransactionTemplate transaction,
            ObjectProvider<RepositoryService> processes, IsolationGuard isolation, AuditPort audit, ClockPort clock,
            P1Properties properties) {
        this.env = env;
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.processes = processes;
        this.isolation = isolation;
        this.audit = audit;
        this.clock = clock;
        this.platformTenantId = properties.getPlatformTenant().getId();
        refuseDemoFlags(env);
    }

    /**
     * Fail-fast at startup: the bootstrap window must not be combined with any demo switch (those trust demo
     * identity headers or auto-seed). Names only.
     */
    static void refuseDemoFlags(Environment env) {
        if (!enabled(env)) return;
        if (!ProductionProfileGuard.demoFlagsOff(env)) {
            throw new IllegalStateException(ENABLED_ENV + " must not be combined with demo switches ("
                    + "NEXUS_DEMO_ENABLED / demo profile / NEXUS_*_DEMO_CONTEXT_ENABLED); turn those off");
        }
    }

    static boolean enabled(Environment env) {
        return "true".equalsIgnoreCase(env.getProperty(ENABLED, "false").trim());
    }

    public record StepResult(String step, String label, String outcome) {
        public static final String CREATED = "CREATED";
        public static final String ALREADY_PRESENT = "ALREADY_PRESENT";
    }

    public record BootstrapResult(List<StepResult> steps, String note) {
        public BootstrapResult {
            steps = List.copyOf(steps);
        }
    }

    public record BootstrapRequest(String reason) {}

    public static final String NOTE = "只创建最小演示内容（租户 / 产品目录 / 审批流程，标注 演示数据，无支付）；"
            + "演示账号在 IdP 管理台创建（E5）。完成后请关闭 " + ENABLED_ENV + "。";

    /** Checks shared with DL-L4: platform admin, a named operator, the open window, a reason. */
    PlatformContext authorize(String operation, String reason) {
        PlatformContext actor = isolation.requirePlatformContext(operation, PlatformDuty.SYSTEM_ADMIN);
        if (actor == null || actor.actor() == null || actor.actor().subjectId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "a named platform operator is required");
        }
        if (!enabled(env)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ENABLED_ENV + " is not set to true");
        }
        refuseDemoFlags(env);
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reason is required (at most 500 characters)");
        }
        return actor;
    }

    public BootstrapResult bootstrap(BootstrapRequest request) {
        String reason = request == null ? null : request.reason();
        PlatformContext actor = authorize("content-bootstrap.apply", reason);
        List<StepResult> results = new ArrayList<>();
        for (ContentSteps.Step step : ContentSteps.ORDER) results.add(applyStep(step));
        audit(actor, "platform.content_bootstrap.applied", reason.trim(), results);
        return new BootstrapResult(results, NOTE);
    }

    StepResult applyStep(ContentSteps.Step step) {
        RepositoryService engine = processes.getIfAvailable();
        boolean before = step.present(jdbc, engine);
        if (!before) transaction.executeWithoutResult(status -> step.apply(jdbc, engine));
        return new StepResult(step.key(), step.label(), before ? StepResult.ALREADY_PRESENT : StepResult.CREATED);
    }

    void audit(PlatformContext actor, String type, String reason, List<StepResult> results) {
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        results.forEach(result -> details.put(result.step(), result.outcome()));
        details.put("reason", reason);
        audit.append(new AuditEvent(UUID.randomUUID(), type, AuditEvent.VERSION, clock.now(),
                actor.auditTenantId(platformTenantId), actor.actor().subjectId(), actor.actorHomeTenantId(), true,
                actor.reason(), actor.traceId(), actor.traceId(), null, AuditEvent.OUTCOME_ACCEPTED,
                "content_bootstrap", "demo-content", details));
    }
}
