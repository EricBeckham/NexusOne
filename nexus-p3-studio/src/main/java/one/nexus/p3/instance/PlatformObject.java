package one.nexus.p3.instance;

import java.util.Map;

/** Platform version of one object, as published for an instance. Content is opaque to the resolver. */
public record PlatformObject(String version, Map<String, Object> content) {}
