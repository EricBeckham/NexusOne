package one.nexus.p3.instance;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Full instance state returned by {@link AppInstanceQuery#current()}. */
public record AppInstanceState(
        UUID instanceId,
        long revision,
        String status,
        String productCode,
        String appCode,
        String version,
        String mode,
        List<String> entries,
        Instant changedAt
) {
    public boolean installed() {
        return "INSTALLED".equals(status);
    }
}
