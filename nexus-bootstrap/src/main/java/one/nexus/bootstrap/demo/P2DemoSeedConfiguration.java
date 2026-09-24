package one.nexus.bootstrap.demo;

import java.util.UUID;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** Minimal P2 directory and role rows for the local visual demo only. */
@Configuration
public class P2DemoSeedConfiguration {
    static final UUID TENANT_ID = UUID.fromString("01900000-0000-7000-8000-000000000101");
    static final UUID ACCOUNT_LIN = UUID.fromString("01900000-0000-7000-8000-000000000201");
    static final UUID ACCOUNT_ZHOU = UUID.fromString("01900000-0000-7000-8000-000000000202");
    static final UUID PERSON_LIN = UUID.fromString("01900000-0000-7000-8000-000000000203");
    static final UUID PERSON_ZHOU = UUID.fromString("01900000-0000-7000-8000-000000000204");
    static final UUID DEPARTMENT_ENGINEERING = UUID.fromString("01900000-0000-7000-8000-000000000205");
    static final UUID DEPARTMENT_PLATFORM = UUID.fromString("01900000-0000-7000-8000-000000000206");
    static final UUID POSITION_MANAGER = UUID.fromString("01900000-0000-7000-8000-000000000207");
    static final UUID POSITION_ENGINEER = UUID.fromString("01900000-0000-7000-8000-000000000208");
    static final UUID EMPLOYMENT_LIN = UUID.fromString("01900000-0000-7000-8000-000000000209");
    static final UUID EMPLOYMENT_ZHOU = UUID.fromString("01900000-0000-7000-8000-000000000210");
    static final UUID ROLE_OPERATOR = UUID.fromString("01900000-0000-7000-8000-000000000211");
    static final UUID ASSIGNMENT_LIN = UUID.fromString("01900000-0000-7000-8000-000000000212");

    @Bean
    ApplicationRunner p2DemoSeed(JdbcClient jdbc, Environment env, TransactionTemplate transaction) {
        return args -> {
            if (!DemoProfileGate.enabled(env.getProperty(DemoProfileGate.ENABLED))) {
                return;
            }
            String datasourceUrl = env.getProperty("spring.datasource.url", "");
            DemoProfileGate.assertAllowed(true, env.getProperty(DemoProfileGate.ENVIRONMENT, "local"), datasourceUrl);

            transaction.executeWithoutResult(status -> {
                jdbc.sql("""
                        INSERT INTO iam.iam_global_account (
                          id, keycloak_user_id, email, phone, username, display_name, status,
                          created_at, updated_at, deleted_at, version)
                        VALUES (:id, 'demo-seed:p2:lin', 'lin.xiao@demo.invalid', '13800000000',
                          'demo-lin', '林晓', 'ACTIVE', timestamptz '2026-01-01 00:00:00+00',
                          timestamptz '2026-01-01 00:00:00+00', NULL, 0)
                        ON CONFLICT (id) DO NOTHING
                        """).param("id", ACCOUNT_LIN).update();
                jdbc.sql("""
                        INSERT INTO iam.iam_global_account (
                          id, keycloak_user_id, email, phone, username, display_name, status,
                          created_at, updated_at, deleted_at, version)
                        VALUES (:id, 'demo-seed:p2:zhou', 'zhou.ran@demo.invalid', '13900000000',
                          'demo-zhou', '周然', 'ACTIVE', timestamptz '2026-01-01 00:00:00+00',
                          timestamptz '2026-01-01 00:00:00+00', NULL, 0)
                        ON CONFLICT (id) DO NOTHING
                        """).param("id", ACCOUNT_ZHOU).update();
                jdbc.sql("""
                        INSERT INTO iam.iam_person (
                          id, tenant_id, account_id, display_name, employee_no, email, phone, hired_on,
                          status, created_at, updated_at, deleted_at, version)
                        VALUES (:id, :tenant, :account, '林晓', 'DEMO-001', 'lin.xiao@demo.invalid',
                          '13800000000', DATE '2024-03-12', 'ACTIVE', timestamptz '2026-01-01 00:00:00+00',
                          timestamptz '2026-01-01 00:00:00+00', NULL, 0)
                        ON CONFLICT (id) DO NOTHING
                        """).param("id", PERSON_LIN).param("tenant", TENANT_ID).param("account", ACCOUNT_LIN).update();
                jdbc.sql("""
                        INSERT INTO iam.iam_person (
                          id, tenant_id, account_id, display_name, employee_no, email, phone, hired_on,
                          status, created_at, updated_at, deleted_at, version)
                        VALUES (:id, :tenant, :account, '周然', 'DEMO-002', 'zhou.ran@demo.invalid',
                          '13900000000', DATE '2024-08-01', 'ACTIVE', timestamptz '2026-01-01 00:00:00+00',
                          timestamptz '2026-01-01 00:00:00+00', NULL, 0)
                        ON CONFLICT (id) DO NOTHING
                        """).param("id", PERSON_ZHOU).param("tenant", TENANT_ID).param("account", ACCOUNT_ZHOU).update();
                jdbc.sql("""
                        INSERT INTO iam.iam_tenant_membership (
                          id, tenant_id, account_id, status, joined_at, updated_at, deleted_at, version)
                        VALUES (:id, :tenant, :account, 'ACTIVE', timestamptz '2026-01-01 00:00:00+00',
                          timestamptz '2026-01-01 00:00:00+00', NULL, 0)
                        ON CONFLICT (tenant_id, account_id) DO NOTHING
                        """).param("id", UUID.fromString("01900000-0000-7000-8000-000000000213"))
                        .param("tenant", TENANT_ID).param("account", ACCOUNT_LIN).update();
                jdbc.sql("""
                        INSERT INTO iam.iam_tenant_membership (
                          id, tenant_id, account_id, status, joined_at, updated_at, deleted_at, version)
                        VALUES (:id, :tenant, :account, 'ACTIVE', timestamptz '2026-01-01 00:00:00+00',
                          timestamptz '2026-01-01 00:00:00+00', NULL, 0)
                        ON CONFLICT (tenant_id, account_id) DO NOTHING
                        """).param("id", UUID.fromString("01900000-0000-7000-8000-000000000214"))
                        .param("tenant", TENANT_ID).param("account", ACCOUNT_ZHOU).update();

                jdbc.sql("""
                        INSERT INTO iam.iam_department (
                          id, tenant_id, parent_id, code, name, path, sort_order, leader_person_id, status,
                          created_at, updated_at, deleted_at, version)
                        VALUES (:id, :tenant, NULL, 'engineering', '产品研发部', :path, 10, :leader, 'ACTIVE',
                          timestamptz '2026-01-01 00:00:00+00', timestamptz '2026-01-01 00:00:00+00', NULL, 0)
                        ON CONFLICT (id) DO NOTHING
                        """).param("id", DEPARTMENT_ENGINEERING).param("tenant", TENANT_ID)
                        .param("path", "/" + DEPARTMENT_ENGINEERING + "/").param("leader", PERSON_LIN).update();
                jdbc.sql("""
                        INSERT INTO iam.iam_department (
                          id, tenant_id, parent_id, code, name, path, sort_order, leader_person_id, status,
                          created_at, updated_at, deleted_at, version)
                        VALUES (:id, :tenant, :parent, 'platform_team', '平台组', :path, 20, :leader, 'ACTIVE',
                          timestamptz '2026-01-01 00:00:00+00', timestamptz '2026-01-01 00:00:00+00', NULL, 0)
                        ON CONFLICT (id) DO NOTHING
                        """).param("id", DEPARTMENT_PLATFORM).param("tenant", TENANT_ID)
                        .param("parent", DEPARTMENT_ENGINEERING)
                        .param("path", "/" + DEPARTMENT_ENGINEERING + "/" + DEPARTMENT_PLATFORM + "/")
                        .param("leader", PERSON_ZHOU).update();
                jdbc.sql("""
                        INSERT INTO iam.iam_position (
                          id, tenant_id, code, name, category, duties, status, created_at, updated_at, deleted_at, version)
                        VALUES (:id, :tenant, 'engineering_manager', '研发经理', '研发', '负责团队协作与交付', 'ACTIVE',
                          timestamptz '2026-01-01 00:00:00+00', timestamptz '2026-01-01 00:00:00+00', NULL, 0)
                        ON CONFLICT (id) DO NOTHING
                        """).param("id", POSITION_MANAGER).param("tenant", TENANT_ID).update();
                jdbc.sql("""
                        INSERT INTO iam.iam_position (
                          id, tenant_id, code, name, category, duties, status, created_at, updated_at, deleted_at, version)
                        VALUES (:id, :tenant, 'software_engineer', '软件工程师', '研发', '参与产品研发与维护', 'ACTIVE',
                          timestamptz '2026-01-01 00:00:00+00', timestamptz '2026-01-01 00:00:00+00', NULL, 0)
                        ON CONFLICT (id) DO NOTHING
                        """).param("id", POSITION_ENGINEER).param("tenant", TENANT_ID).update();
                jdbc.sql("""
                        INSERT INTO iam.iam_employment (
                          id, tenant_id, person_id, department_id, position_id, kind, status, valid_from, valid_to,
                          created_at, updated_at, deleted_at, version)
                        VALUES (:id, :tenant, :person, :department, :position, 'PRIMARY', 'ACTIVE', DATE '2024-03-12', NULL,
                          timestamptz '2026-01-01 00:00:00+00', timestamptz '2026-01-01 00:00:00+00', NULL, 0)
                        ON CONFLICT (id) DO NOTHING
                        """).param("id", EMPLOYMENT_LIN).param("tenant", TENANT_ID).param("person", PERSON_LIN)
                        .param("department", DEPARTMENT_ENGINEERING).param("position", POSITION_MANAGER).update();
                jdbc.sql("""
                        INSERT INTO iam.iam_employment (
                          id, tenant_id, person_id, department_id, position_id, kind, status, valid_from, valid_to,
                          created_at, updated_at, deleted_at, version)
                        VALUES (:id, :tenant, :person, :department, :position, 'PRIMARY', 'ACTIVE', DATE '2024-08-01', NULL,
                          timestamptz '2026-01-01 00:00:00+00', timestamptz '2026-01-01 00:00:00+00', NULL, 0)
                        ON CONFLICT (id) DO NOTHING
                        """).param("id", EMPLOYMENT_ZHOU).param("tenant", TENANT_ID).param("person", PERSON_ZHOU)
                        .param("department", DEPARTMENT_PLATFORM).param("position", POSITION_ENGINEER).update();

                jdbc.sql("""
                        INSERT INTO iam.perm_role (
                          id, tenant_id, code, name, system_role, status, created_at, updated_at, deleted_at, version)
                        VALUES (:id, :tenant, 'demo_operator', '演示操作员', FALSE, 'ACTIVE',
                          timestamptz '2026-01-01 00:00:00+00', timestamptz '2026-01-01 00:00:00+00', NULL, 0)
                        ON CONFLICT (id) DO NOTHING
                        """).param("id", ROLE_OPERATOR).param("tenant", TENANT_ID).update();
                jdbc.sql("""
                        INSERT INTO iam.perm_assignment (id, tenant_id, principal_type, principal_id, role_id, created_at, deleted_at)
                        VALUES (:id, :tenant, 'user', :principal, :role, timestamptz '2026-01-01 00:00:00+00', NULL)
                        ON CONFLICT (id) DO NOTHING
                        """).param("id", ASSIGNMENT_LIN).param("tenant", TENANT_ID).param("principal", PERSON_LIN.toString())
                        .param("role", ROLE_OPERATOR).update();
            });
        };
    }
}
