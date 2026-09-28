package one.nexus.p3.instance;

import java.util.List;
import java.util.UUID;

/** Local pin for one application instance. Tenant overrides are stored separately and are not part of this snapshot. */
public record InstanceSnapshot(
        UUID instanceId,
        long revision,
        String productCode,
        String appCode,
        String version,
        String previousVersion,
        String mode,
        List<String> entries,
        boolean installed
) {}
