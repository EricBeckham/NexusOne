package one.nexus.permission.surface;

import java.util.List;

/**
 * Honesty catalog: ops-level {@code pg_dump} is <em>out</em> of the product
 * {@code SurfaceGuard} path.
 *
 * <p>Product physical dump / raw export is {@link DumpSurfaceCatalog} through
 * {@link AuthorizedExport#writeProjected}. Ops {@code pg_dump} stays on G6
 * runbooks / scripts. The only product Java process mention is
 * {@code DryRunRecoveryPort} probing {@code pg_dump --version}.
 *
 * <p>Not a product-wide G2 close. Does not redo #137 dump HTTP, #144
 * {@code tenant_only}/catalog, or #150 todo HTTP. AuditPort same-TX is
 * unchanged.
 */
public final class OpsPhysicalDumpCatalog {
    public static final String PRODUCT_PATH = "AuthorizedExport.writeProjected";
    public static final String PRODUCT_GUARD = "DumpSurfaceGuard";
    public static final String OPS_BINARY = "pg_dump";
    public static final String BOUNDARY =
            "ops-level pg_dump is out of product SurfaceGuard; product dump uses AuthorizedExport";
    public static final String ALLOWED_PRODUCT_PROBE = "DryRunRecoveryPort --version only";

    /**
     * Product Java that may spawn {@code pg_dump}, and only as {@code --version}.
     */
    public static final List<String> ALLOWED_PRODUCT_PROBE_PATHS = List.of(
            "nexus-audit-core/src/main/java/one/nexus/audit/recovery/DryRunRecoveryPort.java");

    /**
     * Product dump / export surfaces that must not spawn {@code pg_dump}.
     */
    public static final List<String> PRODUCT_DUMP_SURFACES = List.of(
            "nexus-p3-studio/src/main/java/one/nexus/p3/dump/DumpController.java",
            "nexus-p3-studio/src/main/java/one/nexus/p3/infra/permission/DumpSurfaceGuard.java",
            "nexus-permission-core/src/main/java/one/nexus/permission/surface/AuthorizedExport.java",
            "nexus-permission-core/src/main/java/one/nexus/permission/surface/DumpSurfaceCatalog.java");

    /**
     * Ops scripts that execute {@code pg_dump}. They are not SurfaceGuard
     * surfaces and must not claim {@code AuthorizedExport}.
     */
    public static final List<String> OPS_SCRIPT_PATHS = List.of(
            "scripts/g6-recovery/run-drill.sh",
            "scripts/g6-recovery/run-single-tenant.sh",
            "scripts/linux-arm-profile/lifecycle.sh");

    public static final List<String> OPS_RUNBOOK_PATHS = List.of(
            "docs/engineering/runbooks/postgres-whole-db-restore.md",
            "docs/engineering/runbooks/postgres-single-tenant-logical-restore.md");

    private OpsPhysicalDumpCatalog() {}
}
