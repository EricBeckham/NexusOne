package one.nexus.isolation.spi;

import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.TableDescriptor;

/**
 * M0-05 DataScope seam. Isolation kernel always injects tenant_id first; DataScope may only AND extra predicates.
 * Implementations must not remove or replace the tenant predicate.
 */
@FunctionalInterface
public interface DataScopeInjector {
    QueryAst inject(QueryAst query, TableDescriptor table, AccessContext context);

    static DataScopeInjector passthrough() {
        return (query, table, context) -> query;
    }
}
