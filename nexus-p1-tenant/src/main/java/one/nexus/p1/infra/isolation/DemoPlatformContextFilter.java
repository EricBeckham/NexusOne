package one.nexus.p1.infra.isolation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import one.nexus.p1.config.P1Properties;
import one.nexus.p1.domain.Actor;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.IsolationGuard.IsolationSnapshot;
import one.nexus.p1.port.PlatformContext;

/**
 * Temporary demonstration entry. Production must use M0-02 server-issued context and must
 * strip these headers at the edge (ARCH-024). Enabled only when
 * {@code NEXUS_P1_DEMO_CONTEXT_ENABLED=true}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class DemoPlatformContextFilter extends OncePerRequestFilter {

    public static final String ACTOR_HEADER = "X-Nexus-Demo-Actor-Id";
    public static final String ACTOR_TENANT_HEADER = "X-Nexus-Demo-Actor-Tenant-Id";
    public static final String REASON_HEADER = "X-Nexus-Demo-Reason";
    public static final String TRACE_HEADER = "X-Nexus-Trace-Id";
    public static final String TENANT_HEADER = "X-Nexus-Demo-Tenant-Id";

    private final P1Properties properties;
    private final ObjectMapper mapper;

    public DemoPlatformContextFilter(P1Properties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String trace = header(request, TRACE_HEADER);
        if (trace == null || trace.isBlank()) {
            trace = UUID.randomUUID().toString();
        }
        MDC.put("trace_id", trace);
        request.setAttribute("trace_id", trace);
        try {
            if (properties.getDemoContext().isEnabled() && !bindDemoContext(request, response, trace)) {
                return;
            }
            filterChain.doFilter(request, response);
        } finally {
            IsolationContextHolder.clear();
            MDC.remove("trace_id");
        }
    }

    /** @return false if the response was already written */
    private boolean bindDemoContext(HttpServletRequest request, HttpServletResponse response, String trace)
            throws IOException {
        String actorHeader = header(request, ACTOR_HEADER);
        if (actorHeader == null) {
            return true;
        }
        UUID actorId;
        try {
            actorId = UUID.fromString(actorHeader);
        } catch (IllegalArgumentException ex) {
            writeError(request, response, trace, "P1.CONTEXT.001", "invalid demo actor id");
            return false;
        }
        String reason = header(request, REASON_HEADER);
        UUID actorTenant;
        UUID tenantId;
        try {
            actorTenant = parseUuid(header(request, ACTOR_TENANT_HEADER), properties.getPlatformTenant().getId());
            tenantId = parseUuid(header(request, TENANT_HEADER), null);
        } catch (IllegalArgumentException ex) {
            writeError(request, response, trace, "P1.CONTEXT.001", "invalid demo tenant id");
            return false;
        }
        Actor actor = new Actor(actorId, actorTenant, "demo-actor", Set.of(PlatformDuty.ALL_DEMO));
        PlatformContext platform = (reason == null || reason.isBlank())
                ? PlatformContext.inactive()
                : new PlatformContext(true, reason, actor, trace, actorTenant);
        IsolationContextHolder.set(new IsolationSnapshot(actor, platform, tenantId, trace));
        return true;
    }

    private void writeError(
            HttpServletRequest request, HttpServletResponse response, String trace, String code, String message)
            throws IOException {
        response.setStatus(400);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(response.getWriter(), Map.of(
                "code", code,
                "message", message,
                "trace_id", trace,
                "details", Map.of("path", request.getRequestURI())));
    }

    private static UUID parseUuid(String value, UUID fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return UUID.fromString(value);
    }

    private static String header(HttpServletRequest request, String name) {
        return request.getHeader(name);
    }
}
