package one.nexus.p3.infra.isolation;

import java.io.IOException;
import java.util.UUID;
import one.nexus.common.trace.TraceIds;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.p3.P3Properties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Binds isolation-kernel TenantContext for P3 APIs. Demo headers are test-only
 * ({@code nexus.p3.demo-context=true}); they are not a production tenant source.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 21)
public class P3IsolationFilter extends OncePerRequestFilter {
    public static final String HEADER_DEMO_TENANT = "X-Nexus-Demo-Tenant-Id";
    public static final String HEADER_DEMO_TENANT_CODE = "X-Nexus-Demo-Tenant-Code";
    public static final String HEADER_DEMO_ACTOR = "X-Nexus-Demo-Actor-Id";

    private final IsolationKernel kernel;
    private final P3Properties properties;

    public P3IsolationFilter(IsolationKernel kernel, P3Properties properties) {
        this.kernel = kernel;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/p3");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean boundHere = false;
        try {
            if (AccessContextHolder.currentOrNull() == null && properties.isDemoContext()) {
                String tenant = request.getHeader(HEADER_DEMO_TENANT);
                String tenantCode = request.getHeader(HEADER_DEMO_TENANT_CODE);
                String actor = request.getHeader(HEADER_DEMO_ACTOR);
                if (tenant != null && tenantCode != null && actor != null) {
                    String trace = TraceIds.resolve(
                            request.getHeader(TraceIds.HEADER), request.getHeader(TraceIds.TRACEPARENT_HEADER));
                    P3IsolationSupport.bind(kernel, UUID.fromString(tenant), tenantCode, actor, trace);
                    boundHere = true;
                }
            }
            chain.doFilter(request, response);
        } finally {
            if (boundHere) {
                P3IsolationSupport.clear();
            }
        }
    }
}
