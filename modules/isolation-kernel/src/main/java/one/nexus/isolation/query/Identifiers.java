package one.nexus.isolation.query;

import java.util.regex.Pattern;
import one.nexus.isolation.api.IsolationErrorCode;
import one.nexus.isolation.api.IsolationException;

public final class Identifiers {
    private static final Pattern IDENT = Pattern.compile("[a-z_][a-z0-9_]*");

    private Identifiers() {}

    public static String require(String name) {
        if (name == null || !IDENT.matcher(name).matches()) {
            throw new IsolationException(IsolationErrorCode.INVALID_IDENTIFIER, "missing-trace");
        }
        return name;
    }

    public static String requireTable(String name) {
        if (name == null) {
            throw new IsolationException(IsolationErrorCode.INVALID_IDENTIFIER, "missing-trace");
        }
        String[] parts = name.split("\\.", -1);
        if (parts.length > 2) {
            throw new IsolationException(IsolationErrorCode.INVALID_IDENTIFIER, "missing-trace");
        }
        for (String part : parts) {
            require(part);
        }
        return name;
    }

    public static String quote(String name) {
        return '"' + require(name) + '"';
    }

    public static String quoteTable(String name) {
        String[] parts = requireTable(name).split("\\.");
        return java.util.Arrays.stream(parts).map(Identifiers::quote).collect(java.util.stream.Collectors.joining("."));
    }

    public static String tableAlias(String name) {
        String[] parts = requireTable(name).split("\\.");
        return parts[parts.length - 1];
    }
}
