package one.nexus.bootstrap.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import one.nexus.bootstrap.content.ContentBootstrapService.StepResult;
import one.nexus.bootstrap.demo.ApprovalProductDemoSeedConfiguration;
import one.nexus.bootstrap.demo.P1DemoSeedConfiguration;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p1.port.PlatformContext;
import org.flowable.engine.RepositoryService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * DL-L4: step-by-step content preparation aligned with the DL-X2 handbook
 * (docs/engineering/runbooks/demo-host-prod-safe-content.md). The checklist reports presence only; three steps can
 * be applied one at a time with exactly the DL-L1 authorisation (platform admin, named operator, open window, no demo
 * switches, reason); the rest are manual (IdP accounts, real user actions) and are only reported. No payment, and
 * nothing here touches upgrade steps.
 */
@Service
public class ContentChecklistService {
    private final JdbcClient jdbc;
    private final ObjectProvider<RepositoryService> processes;
    private final IsolationGuard isolation;
    private final ContentBootstrapService bootstrap;

    public ContentChecklistService(JdbcClient jdbc, ObjectProvider<RepositoryService> processes, IsolationGuard isolation,
            ContentBootstrapService bootstrap) {
        this.jdbc = jdbc;
        this.processes = processes;
        this.isolation = isolation;
        this.bootstrap = bootstrap;
    }

    /**
     * @param applyKey the ContentSteps key that creates this item through the API, or null when it is manual
     */
    public record ChecklistItem(int order, String step, String label, boolean present, String applyKey, String manualNote) {}

    public record Checklist(List<ChecklistItem> items, String nextMissing, String note) {
        public Checklist {
            items = List.copyOf(items);
        }
    }

    public record StepRequest(String reason) {}

    static final String NOTE = "只报是否存在；可经 API 单步准备的是 tenant / catalog / process，其余由管理员或真实用户完成。"
            + "不做支付，不改升级步骤。";

    public Checklist checklist() {
        isolation.requirePlatformContext("content-checklist.read", PlatformDuty.SYSTEM_ADMIN);
        RepositoryService engine = processes.getIfAvailable();
        String tenant = P1DemoSeedConfiguration.TENANT_ID.toString();
        String definition = ApprovalProductDemoSeedConfiguration.DEFINITION_ID.toString();
        List<ChecklistItem> items = new ArrayList<>();
        add(items, 1, "tenant", "专用演示租户", () -> ContentSteps.TENANT.present(jdbc, engine), "tenant", null);
        add(items, 2, "identities", "发起人与审批人账号及同租户 membership",
                () -> exists("SELECT 1 FROM iam.iam_tenant_membership WHERE tenant_id = CAST(:t AS uuid) AND deleted_at IS NULL LIMIT 1", tenant),
                null, "在 IdP 管理台开通账号并由 IAM 管理员建立 membership（外部阻塞 E5）；本 API 不创建账号。");
        add(items, 3, "form", "已发布的申请表单版本",
                () -> exists("SELECT 1 FROM platform.form_template_version v JOIN market.product_definition p ON p.form_template_version_id = v.id WHERE p.id = CAST(:t AS uuid)", definition),
                "catalog", null);
        add(items, 4, "flow", "已部署的一级审批流程", () -> ContentSteps.PROCESS.present(jdbc, engine), "process", null);
        add(items, 5, "product", "绑定表单与流程的产品定义",
                () -> exists("SELECT 1 FROM market.product_definition WHERE id = CAST(:t AS uuid) AND process_definition_key IS NOT NULL", definition),
                "catalog", null);
        add(items, 6, "catalog", "已发布目录中的 SKU 绑定到该产品", () -> ContentSteps.CATALOG.present(jdbc, engine), "catalog", null);
        add(items, 7, "activation", "发起人在目录开通并完成安装",
                () -> exists("SELECT 1 FROM market.installation WHERE tenant_id = CAST(:t AS uuid) AND app_id = '01900000-0000-7000-8000-000000000503' AND status = 'INSTALLED'", tenant),
                null, "由发起人在目录申请开通；需要审批时由平台管理员按受控流程处理。");
        add(items, 8, "application", "发起人提交申请并由审批人处理",
                () -> exists("SELECT 1 FROM business_meta.product_application WHERE tenant_id = CAST(:t AS uuid) LIMIT 1", tenant),
                null, "由真实用户在页面完成；不生成审批结果。");
        String next = items.stream().filter(item -> !item.present()).map(ChecklistItem::step).findFirst().orElse(null);
        return new Checklist(items, next, NOTE);
    }

    /** Apply one API step; earlier API steps must be present first. Idempotent. */
    public StepResult apply(String key, StepRequest request) {
        ContentSteps.Step step = ContentSteps.ORDER.stream().filter(s -> s.key().equals(key)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no applicable step: " + key));
        String reason = request == null ? null : request.reason();
        PlatformContext actor = bootstrap.authorize("content-bootstrap.step", reason);
        RepositoryService engine = processes.getIfAvailable();
        for (ContentSteps.Step earlier : ContentSteps.ORDER.subList(0, ContentSteps.ORDER.indexOf(step))) {
            if (!earlier.present(jdbc, engine)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "apply step '" + earlier.key() + "' first");
            }
        }
        StepResult result = bootstrap.applyStep(step);
        bootstrap.audit(actor, "platform.content_bootstrap.step_applied", reason.trim(), List.of(result));
        return result;
    }

    private static void add(List<ChecklistItem> items, int order, String step, String label, BooleanSupplier present,
            String applyKey, String manualNote) {
        items.add(new ChecklistItem(order, step, label, present.getAsBoolean(), applyKey, manualNote));
    }

    private boolean exists(String sql, String id) {
        return jdbc.sql(sql).params(Map.of("t", id)).query(Integer.class).optional().isPresent();
    }
}
