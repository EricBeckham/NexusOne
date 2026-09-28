package one.nexus.permission.api;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.LinkedHashMap;
import java.util.Map;
import one.nexus.p2.IdentityModule;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.MemberAccessMode;
import one.nexus.permission.admin.TenantMemberGrantSeeder;
import one.nexus.permission.position.MemberAccessConversionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PLA5: the member-access switch for the current tenant. {@code GET} reports the running mode; {@code POST /convert}
 * binds the member roles to occupied positions (dry run by default) and, in POSITION mode, retires per-person seeds.
 */
@RestController
@RequestMapping("/api/v1/permission/member-access")
public class MemberAccessController {
    private final PermissionModule permissions;
    private final IdentityModule identity;
    private final ObjectProvider<TenantMemberGrantSeeder> seeder;
    private final PermissionAdminGuard guard;

    public MemberAccessController(PermissionModule permissions, IdentityModule identity,
            ObjectProvider<TenantMemberGrantSeeder> seeder, PermissionAdminGuard guard) {
        this.permissions = permissions;
        this.identity = identity;
        this.seeder = seeder;
        this.guard = guard;
    }

    @GetMapping
    public Map<String, Object> mode() {
        guard.requireRead();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", runningMode().name());
        return out;
    }

    @PostMapping("/convert")
    public Map<String, Object> convert(@RequestBody(required = false) ConvertBody body) {
        guard.requireWrite(PermissionAdminGuard.ASSIGNMENT);
        boolean dryRun = body == null || body.dryRun() == null || body.dryRun();
        var result = new MemberAccessConversionService(permissions, identity.repository(), seeder.getIfAvailable(), runningMode())
                .convert(dryRun);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", result.mode());
        out.put("dry_run", result.dryRun());
        out.put("member_positions", result.memberPositions());
        out.put("designer_positions", result.designerPositions());
        out.put("bindings_added", result.bindingsAdded());
        out.put("seed_assignments_removed", result.seedAssignmentsRemoved());
        out.put("persons_without_position", result.personsWithoutPosition());
        return out;
    }

    private MemberAccessMode runningMode() {
        return permissions.entries().mode();
    }

    /** Defaults to a dry run; send {@code "dry_run": false} to apply. */
    public record ConvertBody(@JsonAlias({"dry_run", "dryRun"}) Boolean dryRun) {}
}
