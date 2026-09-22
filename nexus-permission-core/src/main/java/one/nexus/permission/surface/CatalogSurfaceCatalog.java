package one.nexus.permission.surface;

/**
 * Ledger / application / business-type catalog cells. Same
 * {@code ledger}/{@code read} and {@code ledger}/{@code write} pairs as
 * desktop list/detail — not a second engine.
 *
 * <p>Definition HTTP is not a record row surface and not a physical dump.
 * Not a product-wide G2 close.
 */
public final class CatalogSurfaceCatalog {
    public static final String LEDGER_RESOURCE = "ledger";
    public static final String LEDGER_READ = "read";
    public static final String LEDGER_WRITE = "write";

    public static final String APPLICATION_RESOURCE = LEDGER_RESOURCE;
    public static final String APPLICATION_READ = LEDGER_READ;
    public static final String APPLICATION_WRITE = LEDGER_WRITE;

    public static final String BUSINESS_TYPE_RESOURCE = LEDGER_RESOURCE;
    public static final String BUSINESS_TYPE_READ = LEDGER_READ;
    public static final String BUSINESS_TYPE_WRITE = LEDGER_WRITE;

    private CatalogSurfaceCatalog() {}
}
