package one.nexus.isolation.spi;

import java.util.Map;

/**
 * M0-05 FieldPolicy seam used on export. Isolation rejects cross-tenant export
 * before Query AST / JDBC execute; this projector then drops or masks fields
 * before any file / Task sink runs. Not a second tenant context.
 */
@FunctionalInterface
public interface FieldPolicy {
    Map<String, Object> project(Map<String, Object> row);
}
