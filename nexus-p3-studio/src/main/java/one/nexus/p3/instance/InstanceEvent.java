package one.nexus.p3.instance;

import java.util.List;
import java.util.UUID;

/** One {@code p4.app_instance} payload. {@code eventId} is the outbox row id and stays the same on redelivery. */
public record InstanceEvent(
        UUID eventId,
        UUID instanceId,
        long revision,
        String productCode,
        String appCode,
        String version,
        String previousVersion,
        String mode,
        List<String> entries
) {
    public enum Kind {
        INSTALLED,
        UPGRADED,
        UNINSTALLED
    }

    public Kind kind() {
        if (previousVersion != null) {
            return Kind.UPGRADED;
        }
        if (mode == null && entries.isEmpty()) {
            return Kind.UNINSTALLED;
        }
        return Kind.INSTALLED;
    }
}
