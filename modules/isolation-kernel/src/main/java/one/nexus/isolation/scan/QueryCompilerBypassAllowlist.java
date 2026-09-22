package one.nexus.isolation.scan;

import java.util.List;

/**
 * Documented allowlist for the G1 QueryCompiler-bypass CI gate.
 *
 * <p>Modules listed here may use JDBC for <em>platform / catalog / identity</em>
 * tables. They are not a license to issue raw SQL against tenant physical
 * tables ({@code l_*} / Query AST tenant names). Those DML paths stay on
 * {@code QueryCompiler} / {@code GuardedJdbc} / {@code NativeSqlPort}.
 *
 * <p>See {@code docs/g1-querycompiler-bypass-ci.md}.
 */
public final class QueryCompilerBypassAllowlist {
    private QueryCompilerBypassAllowlist() {}

    public record Module(String artifactId, String reason, List<String> jdbcPathPrefixes) {}

    /**
     * Product modules that may contain JDBC at all. New JDBC outside these
     * prefixes fails the gate even when the SQL is not a tenant table.
     */
    public static final List<Module> MODULES = List.of(
            new Module(
                    "isolation-kernel",
                    "QueryCompiler, GuardedJdbc, NativeSqlPort — only public tenant-table JDBC path",
                    List.of("modules/isolation-kernel/src/main/")),
            new Module(
                    "nexus-audit-core",
                    "platform.audit_* hash chain / archive; not Query AST tenant tables",
                    List.of("nexus-audit-core/src/main/java/one/nexus/audit/")),
            new Module(
                    "nexus-event-core",
                    "platform.exec_* outbox / idempotency / dead-letter; IsolationKernel envelopes",
                    List.of(
                            "nexus-event-core/src/main/java/one/nexus/event/jdbc/",
                            "nexus-event-core/src/main/java/one/nexus/event/support/")),
            new Module(
                    "nexus-task-core",
                    "platform.exec_task queue; IsolationKernel task submit guard",
                    List.of("nexus-task-core/src/main/java/one/nexus/task/jdbc/")),
            new Module(
                    "nexus-p1-tenant",
                    "p1 tenant catalog / quota; not ledger physical tables",
                    List.of("nexus-p1-tenant/src/main/java/one/nexus/p1/infra/jdbc/")),
            new Module(
                    "nexus-p2-iam",
                    "iam.* identity store; tenant_id is a column, not QueryCompiler DML",
                    List.of(
                            "nexus-p2-iam/src/main/java/one/nexus/p2/persist/",
                            "nexus-p2-iam/src/main/java/one/nexus/p2/IdentityConfiguration.java")),
            new Module(
                    "nexus-permission-core",
                    "iam.perm_* policy snapshot; IsolationKernel DataScope injector is separate",
                    List.of(
                            "nexus-permission-core/src/main/java/one/nexus/permission/persist/",
                            "nexus-permission-core/src/main/java/one/nexus/permission/PermissionConfiguration.java")),
            new Module(
                    "nexus-file-core",
                    "files.file_object metadata; IsolationKernel FileObjectKeyGuard, not QueryCompiler",
                    List.of(
                            "nexus-file-core/src/main/java/one/nexus/file/infra/",
                            "nexus-file-core/src/main/java/one/nexus/file/FileConfiguration.java")),
            new Module(
                    "nexus-notification-core",
                    "notify.inbox; IsolationKernel notify filter, not QueryCompiler",
                    List.of(
                            "nexus-notification-core/src/main/java/one/nexus/notification/infra/",
                            "nexus-notification-core/src/main/java/one/nexus/notification/NotificationConfiguration.java")),
            new Module(
                    "nexus-p3-studio",
                    "catalog/metadata + SchemaService DDL + GuardedLedgerStore (kernel.jdbc writes + leftover-bound reads)",
                    List.of(
                            "nexus-p3-studio/src/main/java/one/nexus/p3/infra/jdbc/",
                            "nexus-p3-studio/src/main/java/one/nexus/p3/infra/schema/",
                            "nexus-p3-studio/src/main/java/one/nexus/p3/studio/form/JdbcFormStore.java",
                            "nexus-p3-studio/src/main/java/one/nexus/p3/flow/persist/",
                            "nexus-p3-studio/src/main/java/one/nexus/p3/flow/engine/FlowEngineConfiguration.java",
                            "nexus-p3-studio/src/main/java/one/nexus/p3/flow/FlowConfiguration.java",
                            "nexus-p3-studio/src/main/java/one/nexus/p3/studio/StudioConfiguration.java")),
            new Module(
                    "nexus-bootstrap",
                    "platform.runtime_meta health only; not tenant data",
                    List.of("nexus-bootstrap/src/main/java/one/nexus/bootstrap/web/RuntimeMetaController.java")));

    /**
     * Files that may mention tenant physical / Query AST table identifiers
     * together with JDBC. Compiler internals, SchemaService DDL, and the
     * product {@code GuardedLedgerStore} adapter.
     */
    public static final List<String> TENANT_TABLE_SQL_PATHS = List.of(
            "modules/isolation-kernel/src/main/java/one/nexus/isolation/jdbc/GuardedJdbc.java",
            "modules/isolation-kernel/src/main/java/one/nexus/isolation/query/NativeSqlPort.java",
            "modules/isolation-kernel/src/main/java/one/nexus/isolation/query/QueryCompiler.java",
            "modules/isolation-kernel/src/main/java/one/nexus/isolation/query/PostgresRenderer.java",
            "modules/isolation-kernel/src/main/java/one/nexus/isolation/query/TenantInjector.java",
            "nexus-p3-studio/src/main/java/one/nexus/p3/infra/jdbc/GuardedLedgerStore.java",
            "nexus-p3-studio/src/main/java/one/nexus/p3/infra/schema/JdbcSchemaService.java",
            "nexus-p3-studio/src/main/java/one/nexus/p3/infra/schema/PostgresDialectRenderer.java");

    /** Product trees scanned by the gate. {@code poc/} and {@code web/} stay out. */
    public static final List<String> PRODUCT_SOURCE_ROOTS = List.of(
            "modules/isolation-kernel",
            "nexus-common",
            "nexus-data-core",
            "nexus-permission-core",
            "nexus-expression-core",
            "nexus-event-core",
            "nexus-task-core",
            "nexus-cache-core",
            "nexus-crypto-core",
            "nexus-file-core",
            "nexus-notification-core",
            "nexus-audit-core",
            "nexus-p1-tenant",
            "nexus-p2-iam",
            "nexus-p3-studio",
            "nexus-p4-market",
            "nexus-p5-connect",
            "nexus-bootstrap");

    public static List<String> jdbcPathPrefixes() {
        return MODULES.stream().flatMap(module -> module.jdbcPathPrefixes().stream()).toList();
    }
}
