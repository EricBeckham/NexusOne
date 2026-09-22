package one.nexus.p3.domain;

import java.util.Locale;

public enum FieldType {
    TEXT,
    NUMERIC,
    INTEGER,
    BOOLEAN,
    TIMESTAMPTZ;

    public String sqlType() {
        return switch (this) {
            case TEXT -> "text";
            case NUMERIC -> "numeric";
            case INTEGER -> "bigint";
            case BOOLEAN -> "boolean";
            case TIMESTAMPTZ -> "timestamptz";
        };
    }

    public static FieldType fromApi(String raw) {
        if (raw == null || raw.isBlank()) {
            throw P3Exception.invalidIdentifier("field_type");
        }
        try {
            return FieldType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw P3Exception.illegalEvolution("unsupported field type", java.util.Map.of("field_type", raw));
        }
    }
}
