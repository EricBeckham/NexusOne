package one.nexus.permission.scan;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Checkstyle / SpotBugs-style static gate: product {@code src/main} must not
 * skip {@code SurfaceGuard} / {@code PermissionEngine} for P3 surfaces.
 *
 * <p>Rules:
 * <ul>
 *   <li>{@code raw-engine-bypass} — {@code PermissionEngine.evaluate}/{@code can}
 *       outside the engine / {@code PermissionService}</li>
 *   <li>{@code isolated-export-bypass} — IsolatedExport sink methods outside
 *       {@code AuthorizedExport}</li>
 *   <li>{@code raw-permission-skip} — {@code new Decision(true, …)} outside the
 *       engine and documented {@code tenant_only} leftovers</li>
 *   <li>{@code unguarded-p3-surface} — P3 RestController on a surface path
 *       without a SurfaceGuard token</li>
 * </ul>
 *
 * <p>Does not scan JDBC / QueryCompiler (that is the orthogonal G1 gate).
 */
public final class SurfaceGuardBypassGate {
    public static final String RAW_ENGINE = "raw-engine-bypass";
    public static final String ISOLATED_EXPORT = "isolated-export-bypass";
    public static final String RAW_PERMISSION_SKIP = "raw-permission-skip";
    public static final String UNGUARDED_P3 = "unguarded-p3-surface";

    static final Pattern ENGINE_CALL = Pattern.compile(
            "\\bPermissionEngine\\s*\\.\\s*(evaluate|can)\\s*\\(");

    /**
     * IsolatedExport sink methods only. {@code IsolatedExport.TASK_TYPE} is
     * not a bypass.
     */
    static final Pattern EXPORT_CALL = Pattern.compile(
            "\\bIsolatedExport\\s*\\.\\s*(submit|compile|writeFile|dispatch|cacheStatus|writeProjected)\\s*\\(");

    static final Pattern DECISION_TRUE = Pattern.compile("\\bnew\\s+Decision\\s*\\(\\s*true\\b");

    static final Pattern REST_CONTROLLER = Pattern.compile("@RestController\\b");

    static final Pattern SURFACE_PATH = Pattern.compile(
            "/(records|forms|process|path|todos|dumps|exports)\\b|ledger\\.export|/dump");

    static final Pattern GUARD_TOKEN;

    static {
        String joined = String.join("|", SurfaceGuardBypassAllowlist.GUARD_TOKENS);
        GUARD_TOKEN = Pattern.compile("\\b(" + joined + ")\\b");
    }

    public record Violation(Path file, String rule, String detail) {}

    public List<Violation> scanProduct(Path repoRoot) throws IOException {
        Path root = repoRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root.resolve("nexus-permission-core"))
                || !Files.isRegularFile(root.resolve("pom.xml"))) {
            throw new IllegalArgumentException("not a NexusOne repo root: " + root);
        }
        List<Violation> violations = new ArrayList<>();
        for (String module : SurfaceGuardBypassAllowlist.PRODUCT_SOURCE_ROOTS) {
            Path main = root.resolve(module).resolve("src/main");
            if (Files.isDirectory(main)) {
                scanTree(main, root, true, violations);
            }
        }
        return List.copyOf(violations);
    }

    /**
     * Scan a fixture tree. {@code applyAllowlist=false} treats every hit as a
     * violation (used to prove an intentional caller fails the gate).
     */
    public List<Violation> scan(Path sourceRoot, boolean applyAllowlist) throws IOException {
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("SourceRoot missing: " + sourceRoot);
        }
        Path root = sourceRoot.toAbsolutePath().normalize();
        List<Violation> violations = new ArrayList<>();
        scanTree(root, root, applyAllowlist, violations);
        return List.copyOf(violations);
    }

    public static Path findRepoRoot(Path start) {
        Path current = start.toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("nexus-permission-core"))
                    && Files.isDirectory(current.resolve("nexus-bootstrap"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("NexusOne repo root not found from " + start);
    }

    private void scanTree(Path source, Path repoRoot, boolean applyAllowlist, List<Violation> violations)
            throws IOException {
        try (Stream<Path> files = Files.walk(source)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .forEach(path -> inspect(path, repoRoot, applyAllowlist, violations));
        }
    }

    private void inspect(Path path, Path repoRoot, boolean applyAllowlist, List<Violation> violations) {
        String content;
        try {
            content = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        boolean engine = ENGINE_CALL.matcher(content).find();
        boolean export = EXPORT_CALL.matcher(content).find();
        boolean skip = DECISION_TRUE.matcher(content).find();
        boolean unguardedP3 = REST_CONTROLLER.matcher(content).find()
                && SURFACE_PATH.matcher(content).find()
                && !GUARD_TOKEN.matcher(content).find();
        if (!engine && !export && !skip && !unguardedP3) {
            return;
        }
        String relative = relativize(repoRoot, path);
        if (engine && (!applyAllowlist || !pathAllowed(relative, SurfaceGuardBypassAllowlist.ENGINE_PATHS))) {
            violations.add(new Violation(path, RAW_ENGINE, "PermissionEngine.evaluate/can outside PermissionService"));
        }
        if (export && (!applyAllowlist || !pathAllowed(relative, SurfaceGuardBypassAllowlist.EXPORT_PATHS))) {
            violations.add(new Violation(path, ISOLATED_EXPORT, "IsolatedExport sink outside AuthorizedExport"));
        }
        if (skip && (!applyAllowlist || !pathAllowed(relative, SurfaceGuardBypassAllowlist.DECISION_TRUE_PATHS))) {
            violations.add(new Violation(path, RAW_PERMISSION_SKIP, "new Decision(true) outside engine / tenant_only"));
        }
        if (unguardedP3
                && (!applyAllowlist
                        || !pathAllowed(relative, SurfaceGuardBypassAllowlist.UNGUARDED_P3_LEFTOVER_PATHS))) {
            violations.add(new Violation(path, UNGUARDED_P3, "P3 surface RestController without SurfaceGuard token"));
        }
    }

    static boolean pathAllowed(String relative, List<String> allowed) {
        String normalized = relative.replace('\\', '/');
        for (String path : allowed) {
            if (normalized.equals(path) || normalized.endsWith("/" + path) || normalized.endsWith(path)) {
                return true;
            }
        }
        return false;
    }

    private static String relativize(Path root, Path file) {
        Path normalizedFile = file.toAbsolutePath().normalize();
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (normalizedFile.startsWith(normalizedRoot)) {
            return normalizedRoot.relativize(normalizedFile).toString().replace('\\', '/');
        }
        return normalizedFile.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
    }
}
