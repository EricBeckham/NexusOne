package one.nexus.bootstrap.demo;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.PlatformDutyCatalog;
import one.nexus.permission.persist.PolicyRepository;
import org.springframework.core.env.Environment;

/**
 * DEMO-U2: installs {@link PlatformDutyCatalog} on the platform tenant and assigns the platform-ops
 * person to {@code platform_system_admin}. Fail-closed: the startup task does nothing unless
 * {@code NEXUS_PLATFORM_DUTY_BOOTSTRAP_ENABLED=true}, and it refuses to start if
 * {@code NEXUS_P1_DEMO_CONTEXT_ENABLED} is also true. Does not grant tenant physical export.
 */
public final class PlatformDutyBootstrap {
    public static final String ENABLED = "nexus.platform-duty-bootstrap.enabled";
    public static final String ENABLED_ENV = "NEXUS_PLATFORM_DUTY_BOOTSTRAP_ENABLED";
    public static final String DEMO_CONTEXT = "nexus.p1.demo-context.enabled";
    public static final String DEMO_CONTEXT_ENV = "NEXUS_P1_DEMO_CONTEXT_ENABLED";

    /** Same person row as the demo platform operator ({@code P2DemoSeedConfiguration}). */
    public static final UUID PLATFORM_OPS_PERSON_ID =
            UUID.fromString("01900000-0000-7000-8000-000000000222");

    private PlatformDutyBootstrap() {}

    public static boolean enabled(Environment env) {
        return truthy(env.getProperty(ENABLED, "false"));
    }

    /**
     * When the bootstrap window is open, demo context must stay closed. Names only.
     * A closed window does not inspect demo context.
     */
    public static void refuseDemoContext(Environment env) {
        if (!enabled(env)) {
            return;
        }
        if (truthy(env.getProperty(DEMO_CONTEXT, "false"))) {
            throw new IllegalStateException(ENABLED_ENV + " must not be combined with " + DEMO_CONTEXT_ENV
                    + "; leave demo context false");
        }
    }

    public record Result(boolean catalogInstalled, boolean systemAdminAssigned, int assignmentCount) {}

    public static Result install(PermissionModule permissions, UUID platformTenantId) {
        TenantContext previous = TenantContextHolder.get();
        try {
            TenantContextHolder.set(new TenantContext(
                    UUID.randomUUID(),
                    platformTenantId,
                    null,
                    PLATFORM_OPS_PERSON_ID,
                    "platform-duty-bootstrap",
                    Instant.now(),
                    Instant.now().plusSeconds(60)));
            var view = permissions.duties().install();
            PolicyRepository.RoleRecord system = permissions.repository()
                    .findRoleByCode(platformTenantId, PlatformDutyCatalog.SYSTEM_ADMIN)
                    .orElseThrow(() -> new IllegalStateException("platform_system_admin was not installed"));
            String personId = PLATFORM_OPS_PERSON_ID.toString();
            boolean assigned = permissions.repository().listAssignments(platformTenantId).stream()
                    .anyMatch(item -> "user".equals(item.principalType())
                            && personId.equals(item.principalId())
                            && system.id().equals(item.roleId()));
            if (!assigned) {
                permissions.admin().assign("user", personId, system.id());
            }
            int count = (int) permissions.repository().listAssignments(platformTenantId).stream()
                    .filter(item -> "user".equals(item.principalType())
                            && personId.equals(item.principalId())
                            && PlatformDutyCatalog.SYSTEM_ADMIN.equals(item.roleCode()))
                    .count();
            return new Result(view.catalogInstalled(), true, count);
        } finally {
            if (previous == null) {
                TenantContextHolder.clear();
            } else {
                TenantContextHolder.set(previous);
            }
        }
    }

    private static boolean truthy(String raw) {
        return raw != null && "true".equals(raw.trim().toLowerCase(Locale.ROOT));
    }
}
