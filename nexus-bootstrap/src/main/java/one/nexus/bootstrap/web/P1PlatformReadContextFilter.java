package one.nexus.bootstrap.web;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.UUID;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import one.nexus.p1.config.P1Properties;
import one.nexus.p1.domain.Actor;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.infra.isolation.IsolationContextHolder;
import one.nexus.p1.port.IsolationGuard.IsolationSnapshot;
import one.nexus.p1.port.PlatformContext;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.PlatformDutyCatalog;
import one.nexus.permission.engine.PermissionEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * AUTH6: authenticated, permission-checked bridge for an exact allowlist of read-only platform GETs.
 * Each route names the platform duty it binds and the permission it checks; nothing outside {@link #ROUTES} and no
 * non-GET method is ever bridged.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 21)
public class P1PlatformReadContextFilter extends OncePerRequestFilter {
    private static final String UUID_SEGMENT = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    /**
     * One bridged read. {@code dutyRole} is the only platform duty the caller may hold; {@code duty} is what the
     * isolation actor is bound to, so the controller's own {@code requirePlatformContext} sees the same duty.
     */
    record Route(Pattern path, PlatformDuty duty, String dutyRole, String resource, String action) {
        static Route systemAdmin(String regex, String resource, String action) {
            return new Route(Pattern.compile(regex), PlatformDuty.SYSTEM_ADMIN, PlatformDutyCatalog.SYSTEM_ADMIN,
                    resource, action);
        }

        /** AUTH6G3: three-power split — audit reads bind AUDIT_ADMIN only; a system admin never passes here. */
        static Route auditAdmin(String regex, String resource, String action) {
            return new Route(Pattern.compile(regex), PlatformDuty.AUDIT_ADMIN, PlatformDutyCatalog.AUDIT_ADMIN,
                    resource, action);
        }
    }

    static final List<Route> ROUTES = List.of(
            // AUTH6F: tenant directory and registration review.
            Route.systemAdmin("/api/v1/p1/tenants", "tenant", "list"),
            Route.systemAdmin("/api/v1/p1/tenant-registrations", "tenant", "list"),
            // AUTH6G2: tenant relations, platform service status, form template list / detail.
            Route.systemAdmin("/api/v1/p1/tenants/tree", "tenant", "list"),
            Route.systemAdmin("/api/v1/p1/tenants/" + UUID_SEGMENT + "/children", "tenant", "get"),
            Route.systemAdmin("/api/v1/p1/tenants/" + UUID_SEGMENT + "/ancestors", "tenant", "get"),
            Route.systemAdmin("/api/v1/p1/platform/services", "tenant", "list"),
            Route.systemAdmin("/api/v1/p1/platform/form-templates", "tenant", "list"),
            Route.systemAdmin("/api/v1/p1/platform/form-templates/" + UUID_SEGMENT, "tenant", "list"),
            // AUTH6H2: tenant entitlement projection and quota usage (权益与配额), product definitions (P4 平台配置 /
            // 产品配置向导). QuotaService.list already accepts a SYSTEM_ADMIN platform context.
            Route.systemAdmin("/api/v1/p1/tenants/" + UUID_SEGMENT + "/entitlement", "tenant.entitlement", "get"),
            Route.systemAdmin("/api/v1/p1/tenants/" + UUID_SEGMENT + "/quotas", "tenant", "get"),
            Route.systemAdmin("/api/v1/p4/product-definitions", "tenant", "list"),
            Route.systemAdmin("/api/v1/p4/product-definitions/" + UUID_SEGMENT, "tenant", "list"),
            Route.systemAdmin("/api/v1/p4/product-definitions/" + UUID_SEGMENT + "/config-readiness", "tenant", "list"),
            // AUTH6G3: platform audit event search (the audit center list).
            Route.auditAdmin("/api/v1/audit/events",
                    PlatformDutyCatalog.RESOURCE_AUDIT, PlatformDutyCatalog.ACTION_READ));

    /** The bridged route for this request, if it is a GET on an allowlisted path. */
    static Optional<Route> route(HttpServletRequest request) {
        if (!"GET".equals(request.getMethod())) {
            return Optional.empty();
        }
        String uri = request.getRequestURI();
        return ROUTES.stream().filter(route -> route.path().matcher(uri).matches()).findFirst();
    }

    private final IdentityModule identity;
    private final PermissionModule permissions;
    private final P1Properties properties;
    private final boolean enabled;

    public P1PlatformReadContextFilter(IdentityModule identity, PermissionModule permissions,
            P1Properties properties,
            @Value("${nexus.p1.platform-read-context.enabled:false}") boolean enabled) {
        this.identity = identity;
        this.permissions = permissions;
        this.properties = properties;
        this.enabled = enabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || route(request).isEmpty();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var previousTenant = TenantContextHolder.get();
        var previousIsolation = IsolationContextHolder.get();
        Route route = route(request).orElseThrow(); // shouldNotFilter already kept everything else out
        try {
            // Never let an existing/demo snapshot substitute for authentication.
            String authorization = request.getHeader("Authorization");
            String contextId = request.getHeader("X-Nexus-Context-Id");
            if (request.getHeader("X-Tenant-Id") != null) {
                deny(response, 400, Reason.CLIENT_TENANT_HEADER);
                return;
            }
            if (authorization == null || !authorization.startsWith("Bearer ")
                    || contextId == null || contextId.isBlank()) {
                deny(response, 401, Reason.MISSING_CREDENTIALS);
                return;
            }
            try {
                var session = identity.sessions().authenticate(authorization.substring(7));
                var context = identity.sessions().loadContext(UUID.fromString(contextId), session);
                if (!properties.getPlatformTenant().getId().equals(context.tenantId())
                        || context.personId() == null) {
                    deny(response, 403, Reason.NOT_PLATFORM_CONTEXT);
                    return;
                }
                var service = permissions.service();
                var duties = PlatformDutyCatalog.heldDuties(PermissionEngine.expandedRoleCodes(
                        service.currentSubject(), service.snapshot()));
                if (!duties.equals(Set.of(route.dutyRole()))) {
                    deny(response, 403, Reason.DUTY_SET);
                    return;
                }
                service.check(route.resource(), route.action());
                String trace = String.valueOf(request.getAttribute("trace_id"));
                var actor = new Actor(context.personId(), context.tenantId(), "authenticated-platform-reader",
                        Set.of(route.duty()));
                var platform = new PlatformContext(true, "authenticated platform read-only list",
                        actor, trace, context.tenantId());
                IsolationContextHolder.set(new IsolationSnapshot(actor, platform, null, trace));
            } catch (one.nexus.p2.support.DomainException ex) {
                deny(response, ex.httpStatus(), "P2.IAM.MFA_REQUIRED".equals(ex.code()) ? Reason.MFA_REQUIRED
                        : ex.httpStatus() == 401 ? Reason.SESSION_INVALID : Reason.SESSION_FORBIDDEN);
                return;
            } catch (IllegalArgumentException ex) {
                deny(response, 401, Reason.SESSION_INVALID);
                return;
            } catch (one.nexus.permission.support.PermissionException ex) {
                deny(response, 403, Reason.PERMISSION_DENIED);
                return;
            } catch (one.nexus.permission.engine.SnapshotUnavailableException ex) {
                deny(response, 403, Reason.SNAPSHOT_UNAVAILABLE);
                return;
            }
            chain.doFilter(request, response);
        } finally {
            if (previousTenant == null) TenantContextHolder.clear();
            else TenantContextHolder.set(previousTenant);
            if (previousIsolation == null) IsolationContextHolder.clear();
            else IsolationContextHolder.set(previousIsolation);
        }
    }

    /**
     * AUTH6F2: which branch denied, so a denial can be located on site. Fixed lower-case tokens only — never the
     * token, context id, person id, role set or exception text.
     */
    enum Reason {
        CLIENT_TENANT_HEADER,
        MISSING_CREDENTIALS,
        SESSION_INVALID,
        SESSION_FORBIDDEN,
        MFA_REQUIRED,
        NOT_PLATFORM_CONTEXT,
        DUTY_SET,
        PERMISSION_DENIED,
        SNAPSHOT_UNAVAILABLE;

        String wire() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private static void deny(HttpServletResponse response, int status, Reason reason) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"AUTH6.PLATFORM_READ_DENIED\",\"message\":\"platform read context denied\","
                + "\"reason\":\"" + reason.wire() + "\"}");
    }
}
