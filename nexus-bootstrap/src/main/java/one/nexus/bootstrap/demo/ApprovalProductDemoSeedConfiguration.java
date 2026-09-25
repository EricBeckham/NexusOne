package one.nexus.bootstrap.demo;

import java.util.UUID;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Local/demo simple-approval product. Inserts a published form template, a one-level
 * approval process (same deployment shape as M10), a product definition, and a published
 * catalog SKU. It does not subscribe or install a tenant. Rows are marked 演示数据.
 */
@Configuration
public class ApprovalProductDemoSeedConfiguration {
    static final UUID TEMPLATE_ID = UUID.fromString("01900000-0000-7000-8000-000000000501");
    static final UUID TEMPLATE_VERSION_ID = UUID.fromString("01900000-0000-7000-8000-000000000502");
    static final UUID APP_ID = UUID.fromString("01900000-0000-7000-8000-000000000503");
    static final UUID APP_VERSION_ID = UUID.fromString("01900000-0000-7000-8000-000000000504");
    static final UUID DEFINITION_ID = UUID.fromString("01900000-0000-7000-8000-000000000505");
    static final UUID DRAFT_ID = UUID.fromString("01900000-0000-7000-8000-000000000506");
    static final String SKU_CODE = "demo.simple-approval";
    static final String PROCESS_KEY = "demoSimpleApproval";
    static final String SNAPSHOT = """
            [{"key":"title","label":"事由","type":"TEXT","required":true},\
            {"key":"approver","label":"审批人","type":"PERSON","required":true}]""";

    static String bpmnXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:flowable="http://flowable.org/bpmn" targetNamespace="https://nexus.one/process">
                  <process id="demoSimpleApproval" name="演示数据 · 一级审批" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow id="toReview" sourceRef="start" targetRef="review"/>
                    <userTask id="review" name="审批" flowable:assignee="${assignee}"/>
                    <sequenceFlow id="toEnd" sourceRef="review" targetRef="end"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """;
    }

    @Bean
    @Order(100)
    ApplicationRunner approvalProductDemoSeed(JdbcClient jdbc, Environment env, TransactionTemplate transaction,
            ObjectProvider<RepositoryService> processes) {
        return args -> {
            if (!DemoProfileGate.enabled(env.getProperty(DemoProfileGate.ENABLED))) {
                return;
            }
            String datasourceUrl = env.getProperty("spring.datasource.url", "");
            DemoProfileGate.assertAllowed(true, env.getProperty(DemoProfileGate.ENVIRONMENT, "local"), datasourceUrl);
            transaction.executeWithoutResult(status -> {
                seedCatalog(jdbc);
                publishProcess(processes.getIfAvailable());
            });
        };
    }

    private static void seedCatalog(JdbcClient jdbc) {
        jdbc.sql("""
                INSERT INTO platform.form_template
                  (id, code, name, description, status, running_version_id, created_at, updated_at, version)
                VALUES (:id, 'demo_simple_approval', '演示数据 · 简单审批', '演示数据。文本加审批人。',
                  'PUBLISHED', NULL, timestamptz '2026-01-01 00:00:00+00', timestamptz '2026-01-01 00:00:00+00', 1)
                ON CONFLICT (id) DO NOTHING
                """).param("id", TEMPLATE_ID).update();
        jdbc.sql("""
                INSERT INTO platform.form_template_field
                  (id, template_id, field_key, label, field_type, required, options, position, created_at, updated_at)
                SELECT :id, :template, 'title', '事由', 'TEXT', true, NULL, 0,
                  timestamptz '2026-01-01 00:00:00+00', timestamptz '2026-01-01 00:00:00+00'
                 WHERE NOT EXISTS (SELECT 1 FROM platform.form_template_field
                                    WHERE template_id = :template AND field_key = 'title')
                ON CONFLICT DO NOTHING
                """).param("id", UUID.fromString("01900000-0000-7000-8000-000000000511"))
                .param("template", TEMPLATE_ID).update();
        jdbc.sql("""
                INSERT INTO platform.form_template_field
                  (id, template_id, field_key, label, field_type, required, options, position, created_at, updated_at)
                SELECT :id, :template, 'approver', '审批人', 'PERSON', true, NULL, 1,
                  timestamptz '2026-01-01 00:00:00+00', timestamptz '2026-01-01 00:00:00+00'
                 WHERE NOT EXISTS (SELECT 1 FROM platform.form_template_field
                                    WHERE template_id = :template AND field_key = 'approver')
                ON CONFLICT DO NOTHING
                """).param("id", UUID.fromString("01900000-0000-7000-8000-000000000512"))
                .param("template", TEMPLATE_ID).update();
        jdbc.sql("""
                INSERT INTO platform.form_template_version
                  (id, template_id, version, fields_snapshot, published_at, published_by)
                VALUES (:id, :template, 1, CAST(:snapshot AS jsonb), timestamptz '2026-01-02 00:00:00+00', NULL)
                ON CONFLICT (id) DO NOTHING
                """).param("id", TEMPLATE_VERSION_ID).param("template", TEMPLATE_ID).param("snapshot", SNAPSHOT).update();
        jdbc.sql("""
                UPDATE platform.form_template
                   SET running_version_id = :version, status = 'PUBLISHED'
                 WHERE id = :id AND running_version_id IS NULL
                """).param("version", TEMPLATE_VERSION_ID).param("id", TEMPLATE_ID).update();
        jdbc.sql("""
                INSERT INTO market.app_package (id, code, name, summary, created_at)
                VALUES (:id, 'demo_simple_approval', '演示数据 · 简单审批', '演示数据。无支付的一级审批产品。',
                  timestamptz '2026-01-01 00:00:00+00')
                ON CONFLICT (id) DO NOTHING
                """).param("id", APP_ID).update();
        jdbc.sql("""
                INSERT INTO market.app_version (id, app_id, version, status, release_notes, created_at, published_at)
                VALUES (:id, :app, '1.0.0', 'PUBLISHED', '演示数据',
                  timestamptz '2026-01-01 00:00:00+00', timestamptz '2026-01-02 00:00:00+00')
                ON CONFLICT (id) DO NOTHING
                """).param("id", APP_VERSION_ID).param("app", APP_ID).update();
        jdbc.sql("""
                INSERT INTO market.sku (code, app_id, name, entitlements)
                VALUES (:code, :app, '演示数据 · 标准',
                  '{"approval":{"enabled":true,"attributes":{"demo":true}}}'::jsonb)
                ON CONFLICT (code) DO NOTHING
                """).param("code", SKU_CODE).param("app", APP_ID).update();
        jdbc.sql("""
                INSERT INTO market.product_definition
                  (id, code, name, description, form_template_version_id, process_definition_key,
                   process_definition_version, default_permission_pack, status, created_at, updated_at, version)
                VALUES (:id, 'demo_simple_approval', '演示数据 · 简单审批', '演示数据。文本与审批人，一级审批。',
                  :form, :processKey, 1, '{"summary":"演示数据"}'::jsonb, 'PUBLISHED',
                  timestamptz '2026-01-02 00:00:00+00', timestamptz '2026-01-02 00:00:00+00', 1)
                ON CONFLICT (id) DO NOTHING
                """).param("id", DEFINITION_ID).param("form", TEMPLATE_VERSION_ID).param("processKey", PROCESS_KEY).update();
        jdbc.sql("""
                INSERT INTO market.sku_product_binding (sku_code, product_definition_id, created_at)
                VALUES (:sku, :definition, timestamptz '2026-01-02 00:00:00+00')
                ON CONFLICT (sku_code) DO NOTHING
                """).param("sku", SKU_CODE).param("definition", DEFINITION_ID).update();
        jdbc.sql("""
                INSERT INTO business_meta.process_definition_draft
                  (id, tenant_id, process_key, name, bpmn_xml, version, updated_by, created_at, updated_at)
                SELECT :id, t.id, :processKey, '演示数据 · 一级审批', :xml, 1, :actor,
                  timestamptz '2026-01-02 00:00:00+00', timestamptz '2026-01-02 00:00:00+00'
                  FROM platform.tenant t
                 WHERE t.id = :tenant
                ON CONFLICT (tenant_id, process_key) DO NOTHING
                """).param("id", DRAFT_ID).param("processKey", PROCESS_KEY).param("xml", bpmnXml())
                .param("actor", P1DemoSeedConfiguration.PLATFORM_ID)
                .param("tenant", P1DemoSeedConfiguration.TENANT_ID).update();
    }

    /** Deploys the one-level BPMN the same way M10 does: tenant-scoped Flowable deployment. */
    static void publishProcess(RepositoryService repository) {
        if (repository == null) {
            return;
        }
        String tenantId = P1DemoSeedConfiguration.TENANT_ID.toString();
        ProcessDefinition existing = repository.createProcessDefinitionQuery()
                .processDefinitionKey(PROCESS_KEY)
                .processDefinitionTenantId(tenantId)
                .latestVersion()
                .singleResult();
        if (existing != null) {
            return;
        }
        repository.createDeployment()
                .name("tenant-process-" + tenantId + "-" + PROCESS_KEY)
                .tenantId(tenantId)
                .addString(PROCESS_KEY + ".bpmn20.xml", bpmnXml())
                .deploy();
    }
}
