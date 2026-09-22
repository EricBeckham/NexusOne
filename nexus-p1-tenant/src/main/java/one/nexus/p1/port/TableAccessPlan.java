package one.nexus.p1.port;

/**
 * Access plan metadata P1 would pass to M0-02 NativeSqlPort / TableAccessPlan Guard.
 * This module does not execute the guard; the JDBC adapter records the plan next to each statement.
 */
public record TableAccessPlan(
        String tableName,
        String scope,
        boolean tenantRequired,
        String tenantSource,
        boolean platformContextRequired,
        String ownerModule
) {

    public static TableAccessPlan platform(String tableName) {
        return new TableAccessPlan(
                tableName, "platform", false, "none", true, "nexus-p1-tenant");
    }
}
