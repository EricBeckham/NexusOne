package one.nexus.permission.surface;

/**
 * Report query / export cells. Same resource/action pairs as desktop list
 * ({@code ledger}/{@code read}) and {@link SurfaceCatalog} export
 * ({@code ledger.export.csv}/{@code submit}) — not a second engine.
 *
 * <p>Physical ledger dump (pg_dump / callers that skip
 * {@link AuthorizedExport}) stays open. Not a product-wide G2 close.
 */
public final class ReportSurfaceCatalog {
    public static final String QUERY_RESOURCE = "ledger";
    public static final String QUERY_ACTION = "read";

    public static final String EXPORT_RESOURCE = SurfaceCatalog.EXPORT_RESOURCE;
    public static final String EXPORT_ACTION = SurfaceCatalog.EXPORT_ACTION;

    private ReportSurfaceCatalog() {}
}
