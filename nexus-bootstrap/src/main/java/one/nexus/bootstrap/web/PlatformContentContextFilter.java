package one.nexus.bootstrap.web;

import java.util.List;
import java.util.Optional;
import jakarta.servlet.http.HttpServletRequest;
import one.nexus.p1.config.P1Properties;
import one.nexus.p2.IdentityModule;
import one.nexus.permission.PermissionModule;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Real platform content operators only. This does not extend AUTH6's read-only switch or accept demo headers. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 22)
public class PlatformContentContextFilter extends P1PlatformReadContextFilter {
    private static final String ID = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    private record Operation(String method, Route route) {}
    private static final String CODE = "[a-z][a-z0-9_]{0,23}";
    private static Operation write(String method, String path) {
        // Existing SYSTEM_ADMIN content/configuration permission; explicit DENY still wins.
        return new Operation(method, Route.systemAdmin(path, "tenant.config", "update"));
    }
    private static Operation read(String path) {
        // Platform-only content reads (PLB2 / PLB5): the same duty's existing tenant.config:get.
        return new Operation("GET", Route.systemAdmin(path, "tenant.config", "get"));
    }
    private static final List<Operation> OPERATIONS = List.of(
            write("POST", "/api/v1/p1/platform/form-templates"),
            write("PUT", "/api/v1/p1/platform/form-templates/" + ID),
            write("POST", "/api/v1/p1/platform/form-templates/" + ID + "/(validate|publish)"),
            write("POST", "/api/v1/p4/product-definitions"),
            write("PUT", "/api/v1/p4/product-definitions/" + ID),
            write("PUT", "/api/v1/p4/product-definitions/" + ID + "/skus/[a-zA-Z0-9_.-]+"),
            write("POST", "/api/v1/p4/apps"),
            write("POST", "/api/v1/p4/apps/" + ID + "/versions"),
            write("POST", "/api/v1/p4/versions/" + ID + "/(review|approve|publish|unpublish)"),
            write("POST", "/api/v1/p4/skus"),
            write("POST", "/api/v1/p4/activation-applications/" + ID + "/(approve|reject)"),
            // PL re-plan platform content (PLB2 categories + manifests, PLB5/6/8 products). Their services require
            // a SYSTEM_ADMIN platform context; without these routes no real login could ever reach them (403).
            read("/api/v1/p4/app-categories(/" + CODE + ")?"),
            write("POST", "/api/v1/p4/app-categories/" + CODE),
            write("PUT", "/api/v1/p4/app-categories/" + CODE),
            write("DELETE", "/api/v1/p4/app-categories/" + CODE),
            write("PUT", "/api/v1/p4/apps/" + ID + "/category"),
            read("/api/v1/p4/versions/" + ID + "/manifest"),
            write("POST", "/api/v1/p4/versions/" + ID + "/manifest/validate"),
            write("PUT", "/api/v1/p4/versions/" + ID + "/manifest"),
            read("/api/v1/p4/products(/" + ID + ")?"),
            write("POST", "/api/v1/p4/products"),
            write("PUT", "/api/v1/p4/products/" + ID + "(/apps|/auto-upgrade-minor)?"),
            write("POST", "/api/v1/p4/products/" + ID + "/(publish|archive|skus)"),
            new Operation("GET", Route.systemAdmin("/api/v1/p4/activation-applications(/" + ID + ")?", "tenant", "list")));

    public PlatformContentContextFilter(IdentityModule identity, PermissionModule permissions, P1Properties properties) {
        super(identity, permissions, properties, true);
    }

    @Override
    protected Optional<Route> matchedRoute(HttpServletRequest request) {
        // The tenant's own application list is not a platform aggregate and must retain tenant isolation.
        if ("GET".equals(request.getMethod()) && "/api/v1/p4/activation-applications".equals(request.getRequestURI())
                && "true".equals(request.getParameter("mine"))) return Optional.empty();
        return OPERATIONS.stream().filter(op -> op.method().equals(request.getMethod())
                && op.route().path().matcher(request.getRequestURI()).matches()).map(Operation::route).findFirst();
    }

    @Override protected String bridgeReason() { return "authenticated platform content operation"; }
    @Override protected String denialCode() { return "PLATFORM.CONTENT_DENIED"; }
}
