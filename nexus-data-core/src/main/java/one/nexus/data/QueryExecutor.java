package one.nexus.data;

import one.nexus.common.context.RequestContext;

/**
 * Query execution entry owned by M0-02. Business modules must not open JDBC
 * connections; they call this port after M0-02 provides an implementation.
 */
public interface QueryExecutor {
    /**
     * Placeholder so sibling modules can compile against the boundary.
     * M0-02 replaces this with Query AST + TableAccessPlan Guard.
     */
    default void requireTenant() {
        RequestContext.tenant().orElseThrow(one.nexus.common.error.NexusException::missingTenant);
    }
}
