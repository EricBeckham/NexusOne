package one.nexus.p3.instance;

import java.util.List;
import java.util.UUID;

/** PLC8. One drifted override and the paths that differ from the current platform object. */
public record BaselineDiff(
        UUID instanceId,
        ObjectType objectType,
        String objectCode,
        String baseVersion,
        String platformVersion,
        boolean drift,
        List<BaselineChange> changes
) {}
