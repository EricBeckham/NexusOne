package one.nexus.bootstrap.web;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import one.nexus.permission.admin.PlatformDutyCatalog;

/**
 * PV2: which products a signed-in person may enter, per docs/planning/PV1-三类用户产品可见性与权限规划.md §4.
 * Navigation only — every product still enforces its own server-side decision.
 */
public final class ProductVisibility {
    /** Tenant-admin position codes, same test as {@code MarketService} / {@code AdminMfaPolicy}. */
    public static final Set<String> TENANT_ADMIN_POSITIONS = Set.of("tenant_admin", "demo_tenant_admin");
    /** Runtime entitlement key that opens P5 for a business tenant. */
    public static final String CONNECT_ENTITLEMENT = "connect";

    /** Facts about the current issued context. {@code platformTenant} is false when there is no context at all. */
    public record Facts(boolean hasContext, boolean platformTenant, Set<String> platformDuties, boolean tenantAdmin,
            boolean connectEntitled) {}

    private ProductVisibility() {}

    public static boolean isTenantAdminPosition(String positionCode) {
        return positionCode != null && TENANT_ADMIN_POSITIONS.contains(positionCode.toLowerCase(Locale.ROOT));
    }

    /** Ordered product ids (p1..p5). */
    public static List<String> visible(Facts facts) {
        if (!facts.hasContext()) {
            return List.of();
        }
        Set<String> out = new TreeSet<>();
        if (facts.platformTenant()) {
            Set<String> duties = facts.platformDuties() == null ? Set.of() : facts.platformDuties();
            if (duties.contains(PlatformDutyCatalog.SYSTEM_ADMIN)) {
                out.addAll(List.of("p1", "p2", "p4"));
            }
            if (duties.contains(PlatformDutyCatalog.SECURITY_ADMIN)) {
                out.add("p2");
            }
            if (duties.contains(PlatformDutyCatalog.AUDIT_ADMIN)) {
                out.add("p1");
            }
            return List.copyOf(out);
        }
        out.add("p3");
        if (facts.tenantAdmin()) {
            out.addAll(List.of("p2", "p4"));
            if (facts.connectEntitled()) {
                out.add("p5");
            }
        }
        return List.copyOf(out);
    }
}
