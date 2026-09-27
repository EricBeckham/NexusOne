package one.nexus.p2.session;

import java.util.Optional;
import java.util.UUID;

/**
 * DEMO-U6: human labels for a tenant id (code and display name) so the session can show "星河科技" instead of a UUID.
 * Tenants belong to P1, so the application wires an implementation; without one, labels are simply absent.
 */
@FunctionalInterface
public interface TenantLabelPort {
    record TenantLabel(String code, String name) {}

    Optional<TenantLabel> label(UUID tenantId);

    TenantLabelPort NONE = tenantId -> Optional.empty();
}