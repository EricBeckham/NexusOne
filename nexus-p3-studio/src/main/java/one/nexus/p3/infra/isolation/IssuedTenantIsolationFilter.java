package one.nexus.p3.infra.isolation;

import java.io.IOException;
import java.util.UUID;
import one.nexus.cache.IsolationSupport;
import one.nexus.common.trace.TraceIds;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.p1.domain.Tenant;
import one.nexus.p1.port.TenantRepository;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.session.SessionService;
import one.nexus.p2.support.DomainException;
import one.nexus.p3.domain.LedgerDefinition;
import one.nexus.p3.port.LedgerRepository;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Canonical binder for isolation-kernel TenantContext from a server-issued
 * {@code X-Nexus-Context-Id}. Demo headers stay test-only; the Vue client never
 * sends {@code X-Tenant-Id}. Lives in P3 so the G18 path IT and product boot share
 * one binder (do not add a second bootstrap {@code IssuedTenantIsolationFilter}).
 * IdentityModule is optional so M0-06 studio hosts that do not load IAM still start.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 19)
public class IssuedTenantIsolationFilter extends OncePerRequestFilter {
    public static final String HEADER_CONTEXT = "X-Nexus-Context-Id";
    public static final String HEADER_TENANT = "X-Tenant-Id";

    private final IsolationKernel kernel;
    private final ObjectProvider<IdentityModule> identity;
    private final ObjectProvider<TenantRepository> tenants;
    private final ObjectProvider<LedgerRepository> ledgers;
    private final ObjectProvider<TenantMemberGrantSeeder> grantSeeder;

    public IssuedTenantIsolationFilter(
            IsolationKernel kernel,
            ObjectProvider<IdentityModule> identity,
            ObjectProvider<TenantRepository> tenants,
            ObjectProvider<LedgerRepository> ledgers,
            ObjectProvider<TenantMemberGrantSeeder> grantSeeder) {
        this.kernel = kernel;
        this.identity = identity;
        this.tenants = tenants;
        this.ledgers = ledgers;
        this.grantSeeder = grantSeeder;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isHandled(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = TraceIds.resolve(
                request.getHeader(TraceIds.HEADER), request.getHeader(TraceIds.TRACEPARENT_HEADER));
        boolean boundIsolation = false;
        boolean boundP3 = false;
        try {
            if (request.getHeader(HEADER_TENANT) != null) {
                write(response, 400, "P1.M02.009",
                        "client-supplied tenant_id is rejected; use a server-issued context", traceId);
                return;
            }
            if (AccessContextHolder.currentOrNull() != null) {
                chain.doFilter(request, response);
                return;
            }
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            String contextId = request.getHeader(HEADER_CONTEXT);
            IdentityModule iam = identity.getIfAvailable();
            if (iam != null && header != null && header.startsWith("Bearer ") && contextId != null && !contextId.isBlank()) {
                SessionService.AuthenticatedIdentity session = iam.sessions().authenticate(header.substring(7));
                var issued = iam.sessions().loadContext(UUID.fromString(contextId), session);
                TenantMemberGrantSeeder.ensureIfPresent(grantSeeder);
                String actorId = issued.personId() == null
                        ? issued.accountId().toString()
                        : issued.personId().toString();
                String tenantCode = lookupTenantCode(issued.tenantId());
                String path = request.getRequestURI();
                if (path.startsWith("/api/v1/p3") && tenantCode != null) {
                    P3IsolationSupport.bind(kernel, issued.tenantId(), tenantCode, actorId, traceId);
                    boundP3 = true;
                } else {
                    IsolationSupport.bindTenant(
                            kernel,
                            issued.tenantId().toString(),
                            actorId,
                            issued.contextId().toString(),
                            traceId);
                    boundIsolation = true;
                }
            }
            chain.doFilter(request, response);
        } catch (DomainException ex) {
            write(response, ex.httpStatus(), ex.code(), ex.getMessage(), traceId);
        } catch (one.nexus.common.error.NexusException ex) {
            write(response, ex.httpStatus(), ex.errorCode().code(), ex.getMessage(), traceId);
        } catch (IllegalArgumentException ex) {
            write(response, 401, "P2.IAM.UNAUTHORIZED", ex.getMessage(), traceId);
        } finally {
            if (boundP3) {
                P3IsolationSupport.clear();
            } else if (boundIsolation) {
                IsolationSupport.clear();
            }
        }
    }

    static boolean isHandled(String path) {
        if (path == null) {
            return false;
        }
        if (path.startsWith("/api/v1/notifications")
                || path.startsWith("/api/v1/audit")
                || path.startsWith("/api/v1/files")) {
            return true;
        }
        // Todos / process keep FlowAuthFilter as the exclusive binder (strict 403 if context missing).
        return (path.startsWith("/api/v1/p3") && !path.startsWith("/api/v1/p3/todos"))
                || path.startsWith("/api/v1/p4")
                || path.startsWith("/api/v1/p5");
    }

    private String lookupTenantCode(UUID tenantId) {
        if (tenantId == null) {
            return null;
        }
        TenantRepository repository = tenants.getIfAvailable();
        if (repository != null) {
            String code = repository.findById(tenantId).map(Tenant::code).orElse(null);
            if (code != null && !code.isBlank()) {
                return code;
            }
        }
        LedgerRepository ledgerRepository = ledgers.getIfAvailable();
        if (ledgerRepository == null) {
            return null;
        }
        return ledgerRepository.list(tenantId).stream()
                .map(LedgerDefinition::tenantCode)
                .filter(code -> code != null && !code.isBlank())
                .findFirst()
                .orElse(null);
    }

    private static void write(HttpServletResponse response, int status, String code, String message, String traceId)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"" + escape(code) + "\",\"message\":\"" + escape(message)
                + "\",\"trace_id\":\"" + escape(traceId) + "\",\"details\":{}}");
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
