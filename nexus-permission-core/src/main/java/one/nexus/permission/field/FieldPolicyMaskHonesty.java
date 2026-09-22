package one.nexus.permission.field;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import one.nexus.permission.engine.FieldRule;
import one.nexus.permission.scope.QueryAstPolicyCompiler;

/**
 * Honesty catalog: FieldPolicy mask / second-auth stay <em>beside</em> Query AST.
 *
 * <p>Visibility (hidden drop) is already encoded by {@code QueryAstPolicyCompiler}
 * (#115 / #130 / #134). {@link QueryAstPolicyCompiler.Encoding#leftoverMaskFields()}
 * parks mask + second-auth facts. Merge and apply still run in
 * {@link FieldPolicyProjector}. Export rejects second-auth without grant
 * ({@code P1.M02.021}) and still writes raw cell values when granted.
 *
 * <p>Not a product-wide G1 close. Does not redo #115 / #130 / #134 / #142 / #146.
 * Does not invent a second tenant context. Does not change {@code AuditPort}
 * same-TX joining.
 */
public final class FieldPolicyMaskHonesty {
    public static final String G1_CLOSE = "NOT_CLAIMED";
    public static final String LEFTOVER_SURFACE = "FieldPolicyProjector";
    public static final String AST_LEFTOVER = "leftoverMaskFields";
    public static final String BOUNDARY =
            "mask / second-auth stay beside Query AST on FieldPolicyProjector";

    private FieldPolicyMaskHonesty() {}

    public static Map<String, FieldRule> leftoverBesideAst(QueryAstPolicyCompiler.Encoding encoding) {
        if (encoding == null) {
            return Map.of();
        }
        return encoding.leftoverMaskFields();
    }

    /**
     * Compiled Query AST SQL still selects the raw column. Mask text / functions
     * must not appear — that work stays on {@link FieldPolicyProjector}.
     */
    public static boolean compiledSqlLeavesMaskToProjector(String sql, String column) {
        if (sql == null || column == null || column.isBlank()) {
            return false;
        }
        String quoted = "\"" + column + "\"";
        String lower = sql.toLowerCase(Locale.ROOT);
        return sql.contains(quoted)
                && !sql.contains("***")
                && !lower.contains("mask(")
                && !lower.contains("overlay(");
    }

    public static List<String> verifyReport(Map<String, String> report) {
        List<String> errors = new ArrayList<>();
        if (report == null || report.isEmpty()) {
            errors.add("report required");
            return errors;
        }
        String close = value(report, "g1_close");
        if (isClosedClaim(close)) {
            errors.add("g1_close must stay NOT_CLAIMED");
        } else if (!G1_CLOSE.equals(close)) {
            errors.add("g1_close must be NOT_CLAIMED");
        }
        if (truthy(value(report, "mask_on_ast"))) {
            errors.add("mask_on_ast must stay false; mask is leftover beside Query AST");
        }
        if (truthy(value(report, "second_auth_on_ast"))) {
            errors.add("second_auth_on_ast must stay false; second-auth is leftover beside Query AST");
        }
        String surface = value(report, "leftover_surface");
        if (!LEFTOVER_SURFACE.equals(surface)) {
            errors.add("leftover_surface must be FieldPolicyProjector");
        }
        return List.copyOf(errors);
    }

    public static void requireHonest(Map<String, String> report) {
        List<String> errors = verifyReport(report);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }
    }

    public static Map<String, String> parseReport(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        Map<String, String> report = new LinkedHashMap<>();
        for (String raw : Files.readAllLines(path)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            report.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
        }
        return Map.copyOf(report);
    }

    private static String value(Map<String, String> report, String key) {
        String value = report.get(key);
        return value == null ? "" : value.trim();
    }

    private static boolean isClosedClaim(String close) {
        String normalized = close.toUpperCase(Locale.ROOT);
        return "CLOSED".equals(normalized) || "PASS".equals(normalized) || "TRUE".equals(normalized);
    }

    private static boolean truthy(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return "true".equals(normalized) || "yes".equals(normalized) || "1".equals(normalized);
    }
}
