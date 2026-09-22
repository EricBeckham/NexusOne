package one.nexus.isolation;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import one.nexus.isolation.api.IsolationAuditSink;
import one.nexus.isolation.audit.IsolationAuditor;
import one.nexus.isolation.context.PlatformContextIssuer;
import one.nexus.isolation.context.RequestIsolationPolicy;
import one.nexus.isolation.context.TenantContextIssuer;
import one.nexus.isolation.jdbc.GuardedJdbc;
import one.nexus.isolation.query.NativeSqlPort;
import one.nexus.isolation.query.QueryCompiler;
import one.nexus.isolation.query.TableAccessPlanGuard;
import one.nexus.isolation.query.TableRegistry;
import one.nexus.isolation.spi.DataScopeInjector;
import one.nexus.isolation.spi.SoftDeleteInjector;

/**
 * Facade other M0 modules call. M0-01 wires a DataSource around {@link GuardedJdbc};
 * M0-04 supplies membership to {@link RequestIsolationPolicy}; M0-05 plugs {@link DataScopeInjector}.
 */
public final class IsolationKernel {
    private final TableRegistry registry;
    private final IsolationAuditor auditor;
    private final TenantContextIssuer tenantIssuer;
    private final PlatformContextIssuer platformIssuer;
    private final RequestIsolationPolicy requestPolicy;
    private final QueryCompiler compiler;
    private final GuardedJdbc jdbc;

    private IsolationKernel(
            TableRegistry registry,
            IsolationAuditor auditor,
            TenantContextIssuer tenantIssuer,
            PlatformContextIssuer platformIssuer,
            RequestIsolationPolicy requestPolicy,
            QueryCompiler compiler,
            GuardedJdbc jdbc
    ) {
        this.registry = registry;
        this.auditor = auditor;
        this.tenantIssuer = tenantIssuer;
        this.platformIssuer = platformIssuer;
        this.requestPolicy = requestPolicy;
        this.compiler = compiler;
        this.jdbc = jdbc;
    }

    public static IsolationKernel create(TableRegistry registry, Set<String> platformPaths, IsolationAuditSink audit) {
        return create(registry, platformPaths, audit, DataScopeInjector.passthrough(), SoftDeleteInjector.passthrough());
    }

    public static IsolationKernel create(
            TableRegistry registry,
            Set<String> platformPaths,
            IsolationAuditSink audit,
            DataScopeInjector dataScopeInjector,
            SoftDeleteInjector softDeleteInjector
    ) {
        Objects.requireNonNull(registry, "registry");
        IsolationAuditor auditor = new IsolationAuditor(audit);
        TenantContextIssuer tenantIssuer = new TenantContextIssuer(auditor);
        PlatformContextIssuer platformIssuer = new PlatformContextIssuer(platformPaths, auditor);
        QueryCompiler compiler = new QueryCompiler(
                registry,
                new TableAccessPlanGuard(platformIssuer, auditor),
                auditor,
                dataScopeInjector,
                softDeleteInjector
        );
        return new IsolationKernel(
                registry,
                auditor,
                tenantIssuer,
                platformIssuer,
                new RequestIsolationPolicy(tenantIssuer, auditor),
                compiler,
                new GuardedJdbc(compiler)
        );
    }

    public TableRegistry registry() {
        return registry;
    }

    public IsolationAuditor auditor() {
        return auditor;
    }

    public TenantContextIssuer tenants() {
        return tenantIssuer;
    }

    public PlatformContextIssuer platform() {
        return platformIssuer;
    }

    public RequestIsolationPolicy requests() {
        return requestPolicy;
    }

    public QueryCompiler compiler() {
        return compiler;
    }

    public GuardedJdbc jdbc() {
        return jdbc;
    }

    public NativeSqlPort nativeSql(Map<String, String> countTemplates) {
        return new NativeSqlPort(compiler, countTemplates, auditor);
    }
}
