package one.nexus.isolation.spi;

import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.TableDescriptor;

/**
 * Optional soft-delete seam. Default adds {@code deleted_at IS NULL} on SELECT when the column exists.
 */
@FunctionalInterface
public interface SoftDeleteInjector {
    QueryAst inject(QueryAst query, TableDescriptor table, AccessContext context);

    static SoftDeleteInjector passthrough() {
        return (query, table, context) -> query;
    }
}
