package one.nexus.p3.flow.api;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import one.nexus.common.trace.TraceIds;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.session.SessionService;
import one.nexus.p2.support.DomainException;
import one.nexus.p3.flow.FlowException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Binds M0-04 issued context and M0-02 isolation TenantContext. Rejects client tenant_id.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 25)
@ConditionalOnProperty(name = "nexus.flow.engine")
public class FlowAuthFilter extends OncePerRequestFilter {
    public static final String HEADER_CONTEXT = "X-Nexus-Context-Id";
    public static final String HEADER_TENANT = "X-Tenant-Id";

    private final IdentityModule identity;
    private final IsolationKernel isolation;

    public FlowAuthFilter(IdentityModule identity, IsolationKernel isolation) {
        this.identity = identity;
        this.isolation = isolation;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isFlowPath(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String fromMdc = MDC.get(TraceIds.MDC_KEY);
        String traceId = (fromMdc != null && !fromMdc.isBlank())
                ? fromMdc
                : TraceIds.resolve(request.getHeader(TraceIds.HEADER), request.getHeader(TraceIds.TRACEPARENT_HEADER));
        TenantContextHolder.clear();
        boolean boundIsolation = false;
        try {
            if (request.getHeader(HEADER_TENANT) != null) {
                throw FlowException.clientTenantRejected();
            }
            if (isPublic(request.getRequestURI())) {
                chain.doFilter(request, response);
                return;
            }
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header == null || !header.startsWith("Bearer ")) {
                throw DomainException.unauthorized("missing bearer token");
            }
            SessionService.AuthenticatedIdentity session = identity.sessions().authenticate(header.substring(7));
            String contextId = request.getHeader(HEADER_CONTEXT);
            if (contextId == null || contextId.isBlank()) {
                throw FlowException.missingContext();
            }
            var issued = identity.sessions().loadContext(UUID.fromString(contextId), session.claims().subject());
            TenantContext isolationContext = isolation.tenants().issue(
                    new AuthenticatedMembership(
                            Actor.person(issued.personId().toString()),
                            issued.tenantId().toString(),
                            issued.contextId().toString(),
                            Set.of(issued.tenantId().toString())),
                    traceId);
            AccessContextHolder.set(isolationContext);
            boundIsolation = true;
            chain.doFilter(request, response);
        } catch (FlowException ex) {
            write(response, ex.errorCode().code(), ex.getMessage(), ex.httpStatus(), traceId);
        } catch (DomainException ex) {
            write(response, ex.code(), ex.getMessage(), ex.httpStatus(), traceId);
        } catch (IllegalArgumentException ex) {
            write(response, FlowException.MISSING_CONTEXT.code(), ex.getMessage(), 401, traceId);
        } finally {
            TenantContextHolder.clear();
            if (boundIsolation) {
                AccessContextHolder.clear();
            }
        }
    }

    static boolean isFlowPath(String path) {
        return path.startsWith("/api/v1/process")
                || path.startsWith("/api/v1/todos")
                || path.startsWith("/api/v1/p3/todos")
                || path.startsWith("/api/v1/p3/path");
    }

    private static boolean isPublic(String path) {
        return "/api/v1/process/health".equals(path);
    }

    private static void write(HttpServletResponse response, String code, String message, int status, String traceId)
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
