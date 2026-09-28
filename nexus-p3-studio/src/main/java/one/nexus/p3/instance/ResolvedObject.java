package one.nexus.p3.instance;

import java.util.Map;

/**
 * PLX1 C6 resolution. {@code drift} is true when a tenant override is still based on an older platform version.
 */
public record ResolvedObject(
        ObjectType objectType,
        String objectCode,
        ResolutionSource source,
        String baseVersion,
        String platformVersion,
        boolean drift,
        Map<String, Object> content
) {}
