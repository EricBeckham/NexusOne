package one.nexus.isolation.query;

import java.util.List;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.PlatformContext;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.QueryJoin;
import one.nexus.isolation.api.QueryKind;
import one.nexus.isolation.api.TableAccessPlan;
import one.nexus.isolation.api.TableDescriptor;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.audit.IsolationAuditor;
import one.nexus.isolation.context.PlatformContextIssuer;

public final class TableAccessPlanGuard {
    private final PlatformContextIssuer platformIssuer;
    private final IsolationAuditor auditor;

    public TableAccessPlanGuard(PlatformContextIssuer platformIssuer, IsolationAuditor auditor) {
        this.platformIssuer = platformIssuer;
        this.auditor = auditor;
    }

    public TableAccessPlan check(TableDescriptor table, QueryAst query, AccessContext context) {
        return check(table, query, context, List.of());
    }

    public TableAccessPlan check(
            TableDescriptor table, QueryAst query, AccessContext context, List<TableDescriptor> joinTables) {
        if (context == null) {
            throw auditor.reject(IsolationErrorCode.MISSING_CONTEXT, null, "missing context");
        }
        if (context instanceof PlatformContext platform) {
            if (!platformIssuer.allows(platform.path())) {
                throw auditor.reject(IsolationErrorCode.PLATFORM_PATH_REVOKED, context, platform.path());
            }
        } else if (!(context instanceof TenantContext)) {
            throw auditor.reject(IsolationErrorCode.MISSING_CONTEXT, context, "invalid context");
        }
        if (table.scope() == one.nexus.isolation.api.TableScope.PLATFORM && !(context instanceof PlatformContext)) {
            throw auditor.reject(IsolationErrorCode.ILLEGAL_PLATFORM_ACCESS, context, table.name());
        }
        if (table.tenantRequired() && context instanceof TenantContext tenant) {
            if (query.kind() == QueryKind.INSERT) {
                if (!tenant.tenantId().equals(query.values().get("tenant_id"))) {
                    throw auditor.reject(IsolationErrorCode.CROSS_TENANT_WRITE, context, "insert tenant mismatch");
                }
            } else if (!tenant.tenantId().equals(query.filters().get("tenant_id"))) {
                IsolationErrorCode code = query.kind() == QueryKind.SELECT
                        ? IsolationErrorCode.TENANT_PREDICATE_REQUIRED
                        : IsolationErrorCode.CROSS_TENANT_WRITE;
                throw auditor.reject(code, context, "tenant predicate absent or mismatched");
            }
        }
        if (table.tenantRequired()
                && context instanceof PlatformContext
                && query.kind() != QueryKind.SELECT
                && !query.filters().containsKey("tenant_id")
                && query.kind() != QueryKind.INSERT) {
            throw auditor.reject(IsolationErrorCode.UNBOUNDED_WRITE, context, "platform write must target a tenant");
        }
        if (query.kind() == QueryKind.INSERT
                && table.tenantRequired()
                && context instanceof PlatformContext
                && !query.values().containsKey("tenant_id")) {
            throw auditor.reject(IsolationErrorCode.TENANT_PREDICATE_REQUIRED, context, "platform insert needs tenant_id");
        }
        checkJoins(query, context, joinTables);
        return new TableAccessPlan(
                table.name(),
                table.scope(),
                query.kind(),
                table.tenantRequired(),
                context instanceof TenantContext ? "TenantContext" : "PlatformContext",
                query.values().keySet(),
                context instanceof PlatformContext,
                false
        );
    }

    private void checkJoins(QueryAst query, AccessContext context, List<TableDescriptor> joinTables) {
        if (joinTables == null || joinTables.isEmpty()) {
            return;
        }
        for (int i = 0; i < query.joins().size(); i++) {
            TableDescriptor joinTable = joinTables.get(i);
            QueryJoin join = query.joins().get(i);
            if (joinTable.scope() == one.nexus.isolation.api.TableScope.PLATFORM
                    && !(context instanceof PlatformContext)) {
                throw auditor.reject(IsolationErrorCode.ILLEGAL_PLATFORM_ACCESS, context, joinTable.name());
            }
            if (joinTable.tenantRequired() && context instanceof TenantContext tenant) {
                if (!tenant.tenantId().equals(join.filters().get("tenant_id"))) {
                    throw auditor.reject(
                            IsolationErrorCode.TENANT_PREDICATE_REQUIRED, context, "join tenant predicate absent or mismatched");
                }
            }
        }
    }
}
