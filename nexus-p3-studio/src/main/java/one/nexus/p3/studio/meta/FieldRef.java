package one.nexus.p3.studio.meta;

/**
 * Logical field from M0-06. {@code fieldCode} is the bind target for form widgets.
 * Physical column names are intentionally absent.
 */
public record FieldRef(String ledgerCode, String fieldCode, String name, String valueType, boolean required) {}
