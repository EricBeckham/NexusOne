package one.nexus.permission.entry;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.PermissionService;
import one.nexus.permission.support.PermissionException;

/**
 * PLA6 / PLX1 C5: which of the tenant's installed entries the current member may {@code read}. The caller passes the
 * candidate entry keys ({@code fn:{app}:{form}}); this service only filters them through the permission engine
 * (position roles, personal exception grants, explicit deny wins). It keeps no entry catalogue of its own.
 * <p>
 * The portal only uses the result for display; every entry's own API still checks {@code fn.*} itself.
 */
public final class EntryAccessService {
    public static final String ACTION = "read";
    private static final Pattern CODE = Pattern.compile("[a-z][a-z0-9_]{0,23}");

    private final PermissionService permissions;
    private volatile UUID platformTenantId;

    public EntryAccessService(PermissionService permissions, UUID platformTenantId) {
        this.permissions = permissions;
        this.platformTenantId = platformTenantId;
    }

    /** The platform tenant gets no business entries (platform features go through PV / AUTH6). */
    public void usePlatformTenant(UUID platformTenantId) {
        this.platformTenantId = platformTenantId;
    }

    /** Readable subset of {@code candidateKeys}, in candidate order, without duplicates; malformed keys are dropped. */
    public Set<String> readableEntries(Collection<String> candidateKeys) {
        TenantContext context = TenantContextHolder.get();
        if (context == null) {
            throw PermissionException.missingContext();
        }
        Set<String> readable = new LinkedHashSet<>();
        if (candidateKeys == null || context.tenantId().equals(platformTenantId)) {
            return readable;
        }
        for (String key : new LinkedHashSet<>(candidateKeys)) {
            String resource = resourceOf(key);
            if (resource != null && permissions.decide(resource, ACTION).allowed()) {
                readable.add(key);
            }
        }
        return readable;
    }

    /** {@code fn:{app}:{form}} → {@code fn.{app}.{form}}; null when the key is not a well-formed entry key. */
    public static String resourceOf(String key) {
        if (key == null) {
            return null;
        }
        String[] parts = key.split(":", -1);
        if (parts.length != 3 || !"fn".equals(parts[0])
                || !CODE.matcher(parts[1]).matches() || !CODE.matcher(parts[2]).matches()) {
            return null;
        }
        return "fn." + parts[1] + "." + parts[2];
    }
}
