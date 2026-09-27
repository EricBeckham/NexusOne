package one.nexus.bootstrap.content;

import java.util.List;
import one.nexus.bootstrap.demo.ApprovalProductDemoSeedConfiguration;
import one.nexus.bootstrap.demo.P1DemoSeedConfiguration;
import org.flowable.engine.RepositoryService;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * DL-L1 / DL-L4: the ordered, idempotent steps of the minimal demo content — the same rows the local demo seed
 * writes (marked 演示数据, no payment), applied on request instead of by NEXUS_DEMO_ENABLED. Accounts are not a
 * step: demo users live in the IdP (external blocker E5). Nothing here touches upgrade steps.
 */
public final class ContentSteps {
    private ContentSteps() {}

    public interface Step {
        String key();

        String label();

        /** Presence only; never reads or returns configuration values. */
        boolean present(JdbcClient jdbc, RepositoryService processes);

        /** Idempotent: safe to run when already present. */
        void apply(JdbcClient jdbc, RepositoryService processes);
    }

    public static final Step TENANT = new Step() {
        public String key() { return "tenant"; }
        public String label() { return "演示租户（星河科技 · 演示数据）"; }
        public boolean present(JdbcClient jdbc, RepositoryService processes) {
            return exists(jdbc, "SELECT 1 FROM platform.tenant WHERE id = :id", P1DemoSeedConfiguration.TENANT_ID);
        }
        public void apply(JdbcClient jdbc, RepositoryService processes) {
            P1DemoSeedConfiguration.seedTenant(jdbc);
        }
    };

    public static final Step CATALOG = new Step() {
        public String key() { return "catalog"; }
        public String label() { return "请假报销产品：已发布表单、应用版本、SKU、产品定义与绑定"; }
        public boolean present(JdbcClient jdbc, RepositoryService processes) {
            return jdbc.sql("""
                    SELECT 1 FROM market.product_definition p
                      JOIN market.sku_product_binding b ON b.product_definition_id = p.id
                     WHERE p.id = :id AND b.sku_code = :sku
                    """).param("id", ApprovalProductDemoSeedConfiguration.DEFINITION_ID)
                    .param("sku", ApprovalProductDemoSeedConfiguration.SKU_CODE)
                    .query(Integer.class).optional().isPresent();
        }
        public void apply(JdbcClient jdbc, RepositoryService processes) {
            ApprovalProductDemoSeedConfiguration.seedCatalog(jdbc);
        }
    };

    public static final Step PROCESS = new Step() {
        public String key() { return "process"; }
        public String label() { return "一级审批流程（演示租户下部署）"; }
        public boolean present(JdbcClient jdbc, RepositoryService processes) {
            if (processes == null) return false;
            return processes.createProcessDefinitionQuery()
                    .processDefinitionKey(ApprovalProductDemoSeedConfiguration.PROCESS_KEY)
                    .processDefinitionTenantId(P1DemoSeedConfiguration.TENANT_ID.toString())
                    .count() > 0;
        }
        public void apply(JdbcClient jdbc, RepositoryService processes) {
            if (processes == null) throw new IllegalStateException("process engine is not available");
            ApprovalProductDemoSeedConfiguration.publishProcess(processes);
        }
    };

    /** Order matters: the catalog's process draft references the tenant; the process needs the tenant. */
    public static final List<Step> ORDER = List.of(TENANT, CATALOG, PROCESS);

    private static boolean exists(JdbcClient jdbc, String sql, Object id) {
        return jdbc.sql(sql).param("id", id).query(Integer.class).optional().isPresent();
    }
}
