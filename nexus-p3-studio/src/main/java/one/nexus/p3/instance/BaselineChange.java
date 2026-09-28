package one.nexus.p3.instance;

/** One path in a baseline diff. {@code kind} is TENANT_ONLY, PLATFORM_ONLY, or CONFLICT. */
public record BaselineChange(String path, String kind, Object tenantValue, Object platformValue) {}
