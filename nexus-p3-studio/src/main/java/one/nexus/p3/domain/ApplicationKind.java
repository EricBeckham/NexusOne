package one.nexus.p3.domain;

import java.util.Locale;

public enum ApplicationKind {
    CUSTOM,
    PACKAGE;

    public static ApplicationKind fromApi(String raw) {
        if (raw == null || raw.isBlank()) {
            return CUSTOM;
        }
        try {
            return ApplicationKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw P3Exception.invalidIdentifier(raw);
        }
    }
}
