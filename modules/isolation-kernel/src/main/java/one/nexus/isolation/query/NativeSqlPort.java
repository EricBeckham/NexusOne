package one.nexus.isolation.query;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.RenderedSql;
import one.nexus.isolation.audit.IsolationAuditor;

/**
 * Infrastructure-owned native templates only. Callers cannot supply SQL text or table names.
 * Every compile walks the same Query AST / TenantInjector / Guard path.
 */
public final class NativeSqlPort {
    private final QueryCompiler compiler;
    private final Map<String, String> countTemplates;
    private final IsolationAuditor auditor;

    public NativeSqlPort(QueryCompiler compiler, Map<String, String> countTemplates, IsolationAuditor auditor) {
        this.compiler = Objects.requireNonNull(compiler, "compiler");
        this.countTemplates = Map.copyOf(new LinkedHashMap<>(countTemplates));
        this.auditor = Objects.requireNonNull(auditor, "auditor");
    }

    public RenderedSql compileCount(String templateId, AccessContext context) {
        String table = countTemplates.get(templateId);
        if (table == null) {
            throw auditor.reject(IsolationErrorCode.NATIVE_TEMPLATE_NOT_WHITELISTED, context, templateId);
        }
        RenderedSql checked = compiler.compile(QueryAst.select(table, List.of("id"), Map.of()), context);
        String sql = "SELECT count(*) FROM " + Identifiers.quoteTable(checked.plan().table());
        if (checked.plan().tenantRequired() && !checked.plan().platformContext()) {
            sql += " WHERE \"tenant_id\" = ?";
            if (checked.parameters().size() != 1) {
                throw auditor.reject(IsolationErrorCode.TENANT_PREDICATE_REQUIRED, context, "native tenant binding mismatch");
            }
        } else if (!checked.parameters().isEmpty()) {
            throw auditor.reject(IsolationErrorCode.NATIVE_TEMPLATE_NOT_WHITELISTED, context, "unexpected native parameters");
        }
        return new RenderedSql(sql, checked.parameters(), checked.plan());
    }
}
