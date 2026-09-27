package one.nexus.bootstrap.content;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import one.nexus.bootstrap.demo.ApprovalProductDemoSeedConfiguration;
import one.nexus.bootstrap.demo.P1DemoSeedConfiguration;
import one.nexus.p1.config.P1Properties;
import one.nexus.p1.domain.AuditEvent;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.AuditPort;
import one.nexus.p1.port.ClockPort;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p1.port.PlatformContext;
import org.flowable.engine.RuntimeService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * DL-L2: controlled demo-host remoke. Clears only the fixed demo tenant's ACTIVITY on the demo product
 * (subscriptions, installations + pins + upgrade-step records, activation and product applications with their
 * process rows) so a demo can be re-run; catalog content, the tenant and accounts are kept.
 *
 * <p>Two phases. {@code plan} needs the server-side gate NEXUS_DEMO_HOST_ALLOW=1 (exact), a platform
 * SYSTEM_ADMIN and a named operator; it returns the exact scope and a short-lived single-use confirmation token
 * bound to that operator and scope. {@code execute} needs the same gates, the token and a reason, re-checks that the
 * scope has not changed, then deletes in one transaction and audits (without the token). scripts/demo/reset.ps1 stays
 * loopback-only and is not involved.
 */
@Service
public class DemoRemokeService {
    public static final String ALLOW = "nexus.demo-host.allow";
    public static final String ALLOW_ENV = "NEXUS_DEMO_HOST_ALLOW";
    static final Duration TOKEN_TTL = Duration.ofMinutes(5);
    static final String TENANT = P1DemoSeedConfiguration.TENANT_ID.toString();
    static final String APP = "01900000-0000-7000-8000-000000000503";
    static final String SKU = ApprovalProductDemoSeedConfiguration.SKU_CODE;
    static final String PRODUCT = ApprovalProductDemoSeedConfiguration.DEFINITION_ID.toString();
    static final List<String> KEPT = List.of("演示产品目录（表单模板、应用、SKU、产品定义、流程部署）", "演示租户本身与账号 membership");

    private final Environment env;
    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final ObjectProvider<RuntimeService> runtime;
    private final IsolationGuard isolation;
    private final AuditPort audit;
    private final ClockPort clock;
    private final UUID platformTenantId;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    public DemoRemokeService(Environment env, JdbcClient jdbc, TransactionTemplate transaction,
            ObjectProvider<RuntimeService> runtime, IsolationGuard isolation, AuditPort audit, ClockPort clock,
            P1Properties properties) {
        this.env = env;
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.runtime = runtime;
        this.isolation = isolation;
        this.audit = audit;
        this.clock = clock;
        this.platformTenantId = properties.getPlatformTenant().getId();
    }

    private record Pending(UUID operator, Map<String, Integer> scope, Instant expiresAt) {}

    public record RemokePlan(String tenantId, String tenantName, Map<String, Integer> scope, List<String> kept,
            String confirmationToken, Instant expiresAt, String note) {}

    public record RemokeRequest(String confirmationToken, String reason) {}

    public record RemokeResult(Map<String, Integer> removed, boolean entitlementProjectionReset, List<String> kept,
            String note) {}

    static final String PLAN_NOTE = "只清演示租户在演示产品上的开通与申请记录；目录、租户与账号保留。"
            + "确认令牌 5 分钟内单次有效，只绑定当前操作者和上面这份范围；请与操作单核对后再执行。";
    static final String DONE_NOTE = "已清理。升级步骤不会因此标记完成；按 prod-safe 内容手册重新走演示路径。";

    public RemokePlan plan() {
        PlatformContext actor = gate("demo-remoke.plan");
        Map<String, Integer> scope = scope();
        String token = token();
        Instant expires = clock.now().plus(TOKEN_TTL);
        pending.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(clock.now()));
        pending.put(token, new Pending(actor.actor().subjectId(), scope, expires));
        String name = jdbc.sql("SELECT name FROM platform.tenant WHERE id = CAST(:t AS uuid)").param("t", TENANT)
                .query(String.class).optional().orElse("（演示租户不存在）");
        return new RemokePlan(TENANT, name, scope, KEPT, token, expires, PLAN_NOTE);
    }

    public RemokeResult execute(RemokeRequest request) {
        PlatformContext actor = gate("demo-remoke.execute");
        String reason = request == null || request.reason() == null ? "" : request.reason().trim();
        if (reason.isEmpty() || reason.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reason is required (at most 500 characters)");
        }
        String token = request.confirmationToken();
        Pending plan = token == null ? null : pending.remove(token);
        if (plan == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "confirmation token is missing, used or unknown; plan again");
        }
        if (plan.expiresAt().isBefore(clock.now())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "confirmation token expired; plan again");
        }
        if (!plan.operator().equals(actor.actor().subjectId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "confirmation token belongs to another operator");
        }
        Map<String, Integer> now = scope();
        if (!now.equals(plan.scope())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "the remoke scope changed since the plan; plan again");
        }
        boolean[] projectionReset = {false};
        transaction.executeWithoutResult(status -> projectionReset[0] = remove());
        Map<String, Object> details = new LinkedHashMap<>(now);
        details.put("entitlement_projection_reset", projectionReset[0]);
        details.put("reason", reason);
        audit.append(new AuditEvent(UUID.randomUUID(), "platform.demo_remoke.executed", AuditEvent.VERSION, clock.now(),
                actor.auditTenantId(platformTenantId), actor.actor().subjectId(), actor.actorHomeTenantId(), true,
                actor.reason(), actor.traceId(), actor.traceId(), null, AuditEvent.OUTCOME_ACCEPTED,
                "tenant", TENANT, details));
        return new RemokeResult(now, projectionReset[0], KEPT, DONE_NOTE);
    }

    /** Gate order: server allow first (no DB or identity work without it), then platform admin, then a named operator. */
    PlatformContext gate(String operation) {
        String allow = env.getProperty(ALLOW);
        if (!"1".equals(allow)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ALLOW_ENV + " is not set to 1 on the server");
        }
        PlatformContext actor = isolation.requirePlatformContext(operation, PlatformDuty.SYSTEM_ADMIN);
        if (actor == null || actor.actor() == null || actor.actor().subjectId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "a named platform operator is required");
        }
        return actor;
    }

    /** Counts only, keyed by fixed words. */
    Map<String, Integer> scope() {
        Map<String, Integer> scope = new LinkedHashMap<>();
        scope.put("product_applications", count("""
                SELECT count(*) FROM business_meta.product_application
                 WHERE tenant_id = CAST(:t AS uuid) AND (product_definition_id = CAST(:p AS uuid) OR installation_id IN (
                   SELECT id FROM market.installation WHERE tenant_id = CAST(:t AS uuid) AND app_id = CAST(:a AS uuid)))
                """));
        scope.put("process_instances", count("""
                SELECT count(*) FROM business_meta.process_instance WHERE id IN (""" + appInstances() + ")"));
        scope.put("activation_applications", count(
                "SELECT count(*) FROM market.activation_application WHERE tenant_id = CAST(:t AS uuid) AND sku_code = :s"));
        scope.put("installations", count(
                "SELECT count(*) FROM market.installation WHERE tenant_id = CAST(:t AS uuid) AND app_id = CAST(:a AS uuid)"));
        scope.put("upgrade_step_records", count("""
                SELECT count(*) FROM market.upgrade_step_record r JOIN market.installation i ON i.id = r.installation_id
                 WHERE i.tenant_id = CAST(:t AS uuid) AND i.app_id = CAST(:a AS uuid)
                """));
        scope.put("subscriptions", count(
                "SELECT count(*) FROM market.subscription WHERE tenant_id = CAST(:t AS uuid) AND sku_code = :s"));
        return scope;
    }

    private static String appInstances() {
        return """
                SELECT process_instance_id FROM business_meta.product_application
                 WHERE process_instance_id IS NOT NULL AND tenant_id = CAST(:t AS uuid)
                   AND (product_definition_id = CAST(:p AS uuid) OR installation_id IN (
                     SELECT id FROM market.installation WHERE tenant_id = CAST(:t AS uuid) AND app_id = CAST(:a AS uuid)))
                """;
    }

    private boolean remove() {
        List<String> engineIds = jdbc.sql("SELECT flowable_instance_id FROM business_meta.process_instance WHERE id IN ("
                + appInstances() + ") AND status = 'RUNNING'").params(params()).query(String.class).list();
        List<UUID> instances = jdbc.sql(appInstances()).params(params()).query(UUID.class).list();
        RuntimeService engine = runtime.getIfAvailable();
        if (engine != null) {
            for (String id : engineIds) {
                if (engine.createProcessInstanceQuery().processInstanceId(id).count() > 0) {
                    engine.deleteProcessInstance(id, "demo remoke");
                }
            }
        }
        jdbc.sql("""
                DELETE FROM business_meta.product_application
                 WHERE tenant_id = CAST(:t AS uuid) AND (product_definition_id = CAST(:p AS uuid) OR installation_id IN (
                   SELECT id FROM market.installation WHERE tenant_id = CAST(:t AS uuid) AND app_id = CAST(:a AS uuid)))
                """).params(params()).update();
        if (!instances.isEmpty()) {
            for (String table : List.of("process_action", "process_todo")) {
                jdbc.sql("DELETE FROM business_meta." + table + " WHERE tenant_id = CAST(:t AS uuid) AND instance_id IN (:ids)")
                        .param("t", TENANT).param("ids", instances).update();
            }
            jdbc.sql("DELETE FROM business_meta.process_instance WHERE tenant_id = CAST(:t AS uuid) AND id IN (:ids)")
                    .param("t", TENANT).param("ids", instances).update();
        }
        jdbc.sql("DELETE FROM market.activation_application WHERE tenant_id = CAST(:t AS uuid) AND sku_code = :s")
                .params(params()).update();
        // Pins and upgrade-step records go with the installation (ON DELETE CASCADE, V705 / V709).
        jdbc.sql("DELETE FROM market.installation WHERE tenant_id = CAST(:t AS uuid) AND app_id = CAST(:a AS uuid)")
                .params(params()).update();
        jdbc.sql("DELETE FROM market.subscription WHERE tenant_id = CAST(:t AS uuid) AND sku_code = :s")
                .params(params()).update();
        int projection = jdbc.sql("""
                DELETE FROM platform.entitlement_projection
                 WHERE tenant_id = CAST(:t AS uuid) AND source = 'SUBSCRIPTION' AND sku_code = :s
                """).params(params()).update();
        if (projection > 0) P1DemoSeedConfiguration.seedTenant(jdbc); // restores the default pack projection only
        return projection > 0;
    }

    private int count(String sql) {
        return jdbc.sql(sql).params(params()).query(Integer.class).single();
    }

    private static Map<String, Object> params() {
        return Map.of("t", TENANT, "a", APP, "s", SKU, "p", PRODUCT);
    }

    private String token() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
