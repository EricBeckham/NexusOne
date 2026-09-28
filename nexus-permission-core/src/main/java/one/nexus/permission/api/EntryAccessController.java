package one.nexus.permission.api;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import one.nexus.permission.PermissionModule;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * PLA6 / PLX1 C5 over HTTP (operations and debugging; the portal uses the Java port). Answers only for the calling
 * member, so it needs no admin guard: {@code GET /api/v1/permission/entries/readable?keys=fn:leave:leave_apply&keys=...}.
 */
@RestController
@RequestMapping("/api/v1/permission/entries")
public class EntryAccessController {
    private final PermissionModule permissions;

    public EntryAccessController(PermissionModule permissions) {
        this.permissions = permissions;
    }

    @GetMapping("/readable")
    public Map<String, Object> readable(@RequestParam(name = "keys", required = false) List<String> keys) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("entries", List.copyOf(permissions.entries().readableEntries(keys == null ? List.of() : keys)));
        body.put("computed_at", Instant.now().toString());
        return body;
    }
}
