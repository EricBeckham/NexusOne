package one.nexus.p2.api;

import one.nexus.common.trace.TraceIds;
import one.nexus.p2.IdentityConfiguration;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.session.SessionService;
import one.nexus.p2.support.DomainException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;

@Component
public class IdentityAuthFilter extends OncePerRequestFilter {
    public static final String ATTR_IDENTITY = "nexus.identity";
    public static final String HEADER_CONTEXT = "X-Nexus-Context-Id";
    public static final String HEADER_TENANT = "X-Tenant-Id";
    public static final String HEADER_BOOTSTRAP = "X-Nexus-Bootstrap-Token";

    private final IdentityModule module;
    private final IdentityConfiguration.IamProperties properties;

    public IdentityAuthFilter(IdentityModule module, IdentityConfiguration.IamProperties properties) {
        this.module = module;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isIamPath(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String fromMdc = MDC.get(TraceIds.MDC_KEY);
        String traceId = (fromMdc != null && !fromMdc.isBlank())
                ? fromMdc
                : TraceIds.resolve(
                        request.getHeader(TraceIds.HEADER), request.getHeader(TraceIds.TRACEPARENT_HEADER));
        TenantContextHolder.clear();
        try {
            if (request.getHeader(HEADER_TENANT) != null) {
                throw DomainException.badRequest(
                        "P2.IAM.CLIENT_TENANT_REJECTED",
                        "client-supplied tenant_id is rejected; use a server-issued context");
            }
            String path = request.getRequestURI();
            if (isPublic(path)) {
                filterChain.doFilter(request, response);
                return;
            }
            if (path.startsWith("/api/v1/internal/") && bootstrapAuthorized(request)) {
                filterChain.doFilter(request, response);
                return;
            }
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header == null || !header.startsWith("Bearer ")) {
                throw DomainException.unauthorized("missing bearer token");
            }
            SessionService.AuthenticatedIdentity identity = module.sessions().authenticate(header.substring(7));
            request.setAttribute(ATTR_IDENTITY, identity);
            String contextId = request.getHeader(HEADER_CONTEXT);
            if (contextId != null && !contextId.isBlank()) {
                module.sessions().loadContext(UUID.fromString(contextId), identity);
            }
            filterChain.doFilter(request, response);
        } catch (DomainException ex) {
            writeError(response, ex, traceId);
        } catch (IllegalArgumentException ex) {
            writeError(response, DomainException.unauthorized(ex.getMessage()), traceId);
        } finally {
            TenantContextHolder.clear();
        }
    }

    private boolean bootstrapAuthorized(HttpServletRequest request) {
        String expected = properties.platformBootstrapToken();
        if (expected == null || expected.isBlank()) {
            return false;
        }
        String provided = request.getHeader(HEADER_BOOTSTRAP);
        return provided != null && java.security.MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                provided.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    static boolean isIamPath(String path) {
        return "/api/v1/health".equals(path)
                || path.startsWith("/api/v1/auth/")
                || path.startsWith("/api/v1/persons")
                || path.startsWith("/api/v1/departments")
                || path.startsWith("/api/v1/positions")
                || path.startsWith("/api/v1/employments")
                || path.startsWith("/api/v1/memberships")
                || path.startsWith("/api/v1/identity")
                || path.startsWith("/api/v1/internal/");
    }

    private static boolean isPublic(String path) {
        if ("/api/v1/health".equals(path)) {
            return true;
        }
        return path.equals("/api/v1/auth/authorize-url")
                || path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/callback")
                || path.equals("/api/v1/auth/password-reset")
                // AUTH7R1: the access token being renewed is expired by definition; the endpoint checks it itself.
                || path.equals("/api/v1/auth/refresh");
    }

    private static void writeError(HttpServletResponse response, DomainException ex, String traceId) throws IOException {
        response.setStatus(ex.httpStatus());
        response.setContentType("application/json");
        String json = "{\"code\":\"" + ex.code() + "\",\"message\":\"" + escape(ex.getMessage())
                + "\",\"trace_id\":\"" + traceId + "\",\"details\":{}}";
        response.getWriter().write(json);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
