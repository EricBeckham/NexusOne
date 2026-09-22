package one.nexus.permission.surface;

/**
 * Todo list / detail HTTP cells. Same {@code process.task}/{@code read} pair
 * as H5 {@link H5SurfaceCatalog} todo — not a second engine.
 *
 * <p>Approve / reject / withdraw stay {@code SequentialApprovalService.guard}.
 * Not a product-wide G2 close. Isolation-only {@code tenant_only} / catalog
 * honesty is the sibling leftover {@code IsolationOnlyDecision} /
 * {@code CatalogSurfaceCatalog}.
 */
public final class TodoSurfaceCatalog {
    public static final String TODO_RESOURCE = H5SurfaceCatalog.TODO_RESOURCE;
    public static final String TODO_READ = H5SurfaceCatalog.TODO_READ;

    private TodoSurfaceCatalog() {}
}
