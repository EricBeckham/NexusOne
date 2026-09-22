package one.nexus.permission.surface;

/**
 * Physical ledger dump / raw export cells. Same resource/action pairs as
 * desktop list ({@code ledger}/{@code read}) and {@link SurfaceCatalog}
 * export ({@code ledger.export.csv}/{@code submit}) — not a second engine.
 *
 * <p>Dump is the raw {@link AuthorizedExport#writeProjected} path. Report
 * export stays the Task {@link AuthorizedExport#submit} leftover
 * ({@code ReportSurfaceCatalog}). Not a product-wide G2 close. {@code pg_dump}
 * ops / RecoveryPort stay off this engine.
 */
public final class DumpSurfaceCatalog {
    public static final String READ_RESOURCE = "ledger";
    public static final String READ_ACTION = "read";

    public static final String DUMP_RESOURCE = SurfaceCatalog.EXPORT_RESOURCE;
    public static final String DUMP_ACTION = SurfaceCatalog.EXPORT_ACTION;

    private DumpSurfaceCatalog() {}
}
