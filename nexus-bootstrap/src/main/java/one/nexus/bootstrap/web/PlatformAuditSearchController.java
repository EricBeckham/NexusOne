package one.nexus.bootstrap.web;

import java.time.Instant;
import java.util.UUID;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.IsolationGuard;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/audit/events")
public class PlatformAuditSearchController {
    private final IsolationGuard isolation;
    private final PlatformAuditSearchService search;

    public PlatformAuditSearchController(IsolationGuard isolation, PlatformAuditSearchService search) {
        this.isolation = isolation;
        this.search = search;
    }

    @GetMapping
    public PlatformAuditSearchService.Result list(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) UUID actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resource,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String traceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        isolation.requirePlatformContext("audit.events.search", PlatformDuty.AUDIT_ADMIN);
        try {
            return search.search(new PlatformAuditSearchService.Filter(
                    from, to, tenantId, actor, action, resource, result, traceId, page, size));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }
}
