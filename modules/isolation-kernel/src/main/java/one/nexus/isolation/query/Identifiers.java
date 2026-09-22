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

    public static String quote(String name) {
        return '"' + require(name) + '"';
    }
}
