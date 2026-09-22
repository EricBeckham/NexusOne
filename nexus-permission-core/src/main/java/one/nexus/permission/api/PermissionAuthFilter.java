package one.nexus.permission.api;

import java.io.IOException;
import java.util.UUID;
import one.nexus.common.trace.TraceIds;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.session.SessionService;
import one.nexus.p2.support.DomainException;
import one.nexus.permission.support.PermissionException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Binds the M0-04 issued TenantContext. Does not accept client tenant_id.
 */
@Component
public class PermissionAuthFilter extends OncePerRequestFilter {
    public static final String HEADER_CONTEXT = "X-Nexus-Context-Id";
    public static final String HEADER_TENANT = "X-Tenant-Id";

    private final IdentityModule identity;

    public PermissionAuthFilter(IdentityModule identity) {
        this.identity = identity;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isPermissionPath(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String fromMdc = MDC.get(TraceIds.MDC_KEY);
        String traceId = (fromMdc != null && !fromMdc.isBlank())
                ? fromMdc
                : TraceIds.resolve(request.getHeader(TraceIds.HEADER), request.getHeader(TraceIds.TRACEPARENT_HEADER));
        TenantContextHolder.clear();
        try {
            if (request.getHeader(HEADER_TENANT) != null) {
                throw PermissionException.clientTenantRejected();
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
            if (contextId != null && !contextId.isBlank()) {
                identity.sessions().loadContext(UUID.fromString(contextId), session.claims().subject());
            }
            chain.doFilter(request, response);
        } catch (PermissionException ex) {
            write(response, ex.errorCode().code(), ex.getMessage(), ex.httpStatus(), traceId);
        } catch (DomainException ex) {
            write(response, ex.code(), ex.getMessage(), ex.httpStatus(), traceId);
        } catch (IllegalArgumentException ex) {
            write(response, "P2.PERM.0009", ex.getMessage(), 401, traceId);
        } finally {
            TenantContextHolder.clear();
        }
    }

    static boolean isPermissionPath(String path) {
        return path.startsWith("/api/v1/permission");
    }

    private static boolean isPublic(String path) {
        return "/api/v1/permission/health".equals(path);
    }

    private static void write(HttpServletResponse response, String code, String message, int status, String traceId)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        String json = "{\"code\":\"" + escape(code) + "\",\"message\":\"" + escape(message)
                + "\",\"trace_id\":\"" + escape(traceId) + "\",\"details\":{}}";
        response.getWriter().write(json);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
