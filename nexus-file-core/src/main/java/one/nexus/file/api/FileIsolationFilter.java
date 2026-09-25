package one.nexus.file.api;

import java.io.IOException;
import java.util.UUID;
import one.nexus.cache.IsolationSupport;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.trace.TraceIds;
import one.nexus.file.FileObjectReconciler;
import one.nexus.file.FileProperties;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.context.AccessContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Binds isolation-kernel TenantContext for file APIs. Demo headers are test-only
 * ({@code nexus.files.demo-context=true}); they are not a production tenant source.
 * Platform reconcile uses {@code X-Nexus-Bootstrap-Token} when configured.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class FileIsolationFilter extends OncePerRequestFilter {
    public static final String HEADER_DEMO_TENANT = "X-Nexus-Demo-Tenant-Id";
    public static final String HEADER_DEMO_ACTOR = "X-Nexus-Demo-Actor-Id";
    public static final String HEADER_BOOTSTRAP = "X-Nexus-Bootstrap-Token";

    private final IsolationKernel kernel;
    private final FileProperties properties;

    public FileIsolationFilter(IsolationKernel kernel, FileProperties properties) {
        this.kernel = kernel;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/files") && !path.startsWith("/api/v1/internal/files");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean boundHere = false;
        try {
            String path = request.getRequestURI();
            String trace = TraceIds.resolve(
                    request.getHeader(TraceIds.HEADER), request.getHeader(TraceIds.TRACEPARENT_HEADER));
            if (path.startsWith("/api/v1/internal/files")) {
                if (!bootstrapAuthorized(request)) {
                    unauthorized(response, trace);
                    return;
                }
                if (AccessContextHolder.currentOrNull() == null && properties.isDemoContext()) {
                    String actor = request.getHeader(HEADER_DEMO_ACTOR);
                    IsolationSupport.bindPlatform(
                            kernel,
                            FileObjectReconciler.PLATFORM_PATH,
                            actor == null || actor.isBlank() ? "ops-1" : actor,
                            "file-reconcile",
                            trace);
                    boundHere = true;
                }
            } else if (AccessContextHolder.currentOrNull() == null && properties.isDemoContext()) {
                String tenant = request.getHeader(HEADER_DEMO_TENANT);
                String actor = request.getHeader(HEADER_DEMO_ACTOR);
                if (tenant != null && actor != null) {
                    UUID.fromString(tenant);
                    IsolationSupport.bindTenant(kernel, tenant, actor, "mem-" + tenant, trace);
                    boundHere = true;
                }
            }
            chain.doFilter(request, response);
        } finally {
            if (boundHere) {
                AccessContextHolder.clear();
            }
        }
    }

    private boolean bootstrapAuthorized(HttpServletRequest request) {
        String expected = properties.getPlatformBootstrapToken();
        if (expected == null || expected.isBlank()) {
            return AccessContextHolder.currentOrNull() != null;
        }
        String provided = request.getHeader(HEADER_BOOTSTRAP);
        return provided != null && java.security.MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                provided.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void unauthorized(HttpServletResponse response, String trace) throws IOException {
        response.setStatus(401);
        response.setContentType("application/json");
        String safeTrace = trace == null || trace.isBlank() ? "unknown" : trace.replace("\"", "");
        response.getWriter().write("{\"code\":\"" + ErrorCode.API_BAD_REQUEST.code()
                + "\",\"message\":\"bootstrap token required\",\"trace_id\":\"" + safeTrace + "\",\"details\":{}}");
    }
}
