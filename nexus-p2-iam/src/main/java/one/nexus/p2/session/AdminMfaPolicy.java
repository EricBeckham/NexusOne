package one.nexus.p2.session;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Product policy: platform and tenant admins require MFA by default
 * (ARCH-CMP-001 / P1-M05-G01-F006 / P2-M09-G04-F005).
 */
public final class AdminMfaPolicy {
    public static final UUID DEFAULT_PLATFORM_TENANT =
            UUID.fromString("01900000-0000-7000-8000-000000000001");
    public static final List<String> DEFAULT_ADMIN_POSITION_CODES = List.of(
            "TENANT_ADMIN", "PLATFORM_ADMIN", "demo_tenant_admin", "demo_platform_ops",
            "SYSTEM_ADMIN", "SECURITY_ADMIN", "AUDIT_ADMIN");

    private final boolean adminRequired;
    private final UUID platformTenantId;
    private final Set<String> adminPositionCodes;

    public AdminMfaPolicy(boolean adminRequired, UUID platformTenantId, List<String> adminPositionCodes) {
        this.adminRequired = adminRequired;
        this.platformTenantId = platformTenantId == null ? DEFAULT_PLATFORM_TENANT : platformTenantId;
        List<String> codes = adminPositionCodes == null || adminPositionCodes.isEmpty()
                ? DEFAULT_ADMIN_POSITION_CODES
                : adminPositionCodes;
        Set<String> normalized = new LinkedHashSet<>();
        for (String code : codes) {
            if (code != null && !code.isBlank()) {
                normalized.add(code.trim().toUpperCase(Locale.ROOT));
            }
        }
        this.adminPositionCodes = Set.copyOf(normalized);
    }

    public static AdminMfaPolicy forced() {
        return new AdminMfaPolicy(true, DEFAULT_PLATFORM_TENANT, DEFAULT_ADMIN_POSITION_CODES);
    }

    public static AdminMfaPolicy disabled() {
        return new AdminMfaPolicy(false, DEFAULT_PLATFORM_TENANT, DEFAULT_ADMIN_POSITION_CODES);
    }

    public boolean adminRequired() {
        return adminRequired;
    }

    public UUID platformTenantId() {
        return platformTenantId;
    }

    public Set<String> adminPositionCodes() {
        return adminPositionCodes;
    }

    public boolean isAdminPosition(String code) {
        return code != null && adminPositionCodes.contains(code.trim().toUpperCase(Locale.ROOT));
    }
}
