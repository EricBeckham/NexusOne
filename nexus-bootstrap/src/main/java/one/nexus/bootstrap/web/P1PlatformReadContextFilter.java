package one.nexus.bootstrap.web;

import java.io.IOException;
import java.util.Set;
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

/** AUTH6: authenticated, permission-checked bridge for two read-only P1 lists. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 21)
public class P1PlatformReadContextFilter extends OncePerRequestFilter {
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
        return !enabled || !"GET".equals(request.getMethod())
                || !("/api/v1/p1/tenants".equals(request.getRequestURI())
                || "/api/v1/p1/tenant-registrations".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var previousTenant = TenantContextHolder.get();
        var previousIsolation = IsolationContextHolder.get();
        try {
            // Never let an existing/demo snapshot substitute for authentication.
            String authorization = request.getHeader("Authorization");
            String contextId = request.getHeader("X-Nexus-Context-Id");
            if (request.getHeader("X-Tenant-Id") != null) {
                deny(response, 400);
                return;
            }
            if (authorization == null || !authorization.startsWith("Bearer ")
                    || contextId == null || contextId.isBlank()) {
                deny(response, 401);
                return;
            }
            try {
                var session = identity.sessions().authenticate(authorization.substring(7));
                var context = identity.sessions().loadContext(UUID.fromString(contextId), session);
                if (!properties.getPlatformTenant().getId().equals(context.tenantId())
                        || context.personId() == null) {
                    deny(response, 403);
                    return;
                }
                var service = permissions.service();
                var duties = PlatformDutyCatalog.heldDuties(PermissionEngine.expandedRoleCodes(
                        service.currentSubject(), service.snapshot()));
                if (!duties.equals(Set.of(PlatformDutyCatalog.SYSTEM_ADMIN))) {
                    deny(response, 403);
                    return;
                }
                service.check("tenant", "list");
                String trace = String.valueOf(request.getAttribute("trace_id"));
                var actor = new Actor(context.personId(), context.tenantId(), "authenticated-platform-reader",
                        Set.of(PlatformDuty.SYSTEM_ADMIN));
                var platform = new PlatformContext(true, "authenticated platform read-only list",
                        actor, trace, context.tenantId());
                IsolationContextHolder.set(new IsolationSnapshot(actor, platform, null, trace));
            } catch (one.nexus.p2.support.DomainException ex) {
                deny(response, ex.httpStatus());
                return;
            } catch (IllegalArgumentException ex) {
                deny(response, 401);
                return;
            } catch (one.nexus.permission.support.PermissionException
                    | one.nexus.permission.engine.SnapshotUnavailableException ex) {
                deny(response, 403);
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

    private static void deny(HttpServletResponse response, int status) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"AUTH6.PLATFORM_READ_DENIED\",\"message\":\"platform read context denied\"}");
    }
}
