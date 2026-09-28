package one.nexus.p3.instance;

import java.util.Optional;
import java.util.UUID;

/** Portal asks this before offering submit on a FILL entry. Empty means the entry may be submitted. */
@FunctionalInterface
public interface ProcessStartGate {
    Optional<String> blockReason(UUID tenantId, String processKey);
}
