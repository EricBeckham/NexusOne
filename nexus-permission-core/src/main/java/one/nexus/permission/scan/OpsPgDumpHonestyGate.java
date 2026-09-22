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
import one.nexus.permission.surface.OpsPhysicalDumpCatalog;

/**
 * Static honesty gate: ops-level {@code pg_dump} stays out of the product
 * {@code SurfaceGuard} path.
 *
 * <p>Product dump / raw export is {@code AuthorizedExport.writeProjected}
 * (#137). Product Java must not execute {@code pg_dump}. The documented
 * exception is {@code DryRunRecoveryPort} probing {@code --version} only.
 * Ops scripts may run {@code pg_dump} and must not claim SurfaceGuard /
 * AuthorizedExport.
 *
 * <p>Does not redo #137 dump HTTP, #144 catalog / {@code tenant_only}, or
 * #150 todo HTTP. Does not scan JDBC (orthogonal G1 gate). Does not change
 * AuditPort.
 */
public final class OpsPgDumpHonestyGate {
    public static final String OPS_PG_DUMP_IN_PRODUCT = "ops-pg-dump-in-product";
    public static final String OPS_SCRIPT_CLAIMS_SURFACE = "ops-pg-dump-claims-surfaceguard";
    public static final String OPS_SCRIPT_MISSING = "ops-pg-dump-script-missing";

    static final Pattern PG_DUMP_LITERAL = Pattern.compile("\"pg_dump\"|'pg_dump'");

    static final Pattern PROCESS_SPAWN = Pattern.compile(
            "\\bnew\\s+ProcessBuilder\\b|Runtime\\.getRuntime\\(\\)\\.exec\\b");

    static final Pattern VERSION_ONLY = Pattern.compile(
            "new\\s+ProcessBuilder\\s*\\(\\s*command\\s*,\\s*\"--version\"\\s*\\)");

    static final Pattern PROCESS_PG_DUMP_INLINE = Pattern.compile(
            "(?:new\\s+ProcessBuilder|Runtime\\.getRuntime\\(\\)\\.exec)\\s*\\([^;]{0,400}pg_dump",
            Pattern.DOTALL);

    public record Violation(Path file, String rule, String detail) {}

    public List<Violation> scanProduct(Path repoRoot) throws IOException {
        Path root = requireRepo(repoRoot);
        List<Violation> violations = new ArrayList<>();
        for (String module : SurfaceGuardBypassAllowlist.PRODUCT_SOURCE_ROOTS) {
            Path main = root.resolve(module).resolve("src/main");
            if (Files.isDirectory(main)) {
                scanJavaTree(main, root, true, violations);
            }
        }
        scanOpsScripts(root, violations);
        return List.copyOf(violations);
    }

    /**
     * Scan a fixture tree. {@code applyAllowlist=false} treats every
     * {@code pg_dump} process spawn as a violation.
     */
    public List<Violation> scan(Path sourceRoot, boolean applyAllowlist) throws IOException {
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("SourceRoot missing: " + sourceRoot);
        }
        Path root = sourceRoot.toAbsolutePath().normalize();
        List<Violation> violations = new ArrayList<>();
        scanJavaTree(root, root, applyAllowlist, violations);
        return List.copyOf(violations);
    }

    public static Path findRepoRoot(Path start) {
        return SurfaceGuardBypassGate.findRepoRoot(start);
    }

    private static Path requireRepo(Path repoRoot) {
        Path root = repoRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root.resolve("nexus-permission-core"))
                || !Files.isRegularFile(root.resolve("pom.xml"))) {
            throw new IllegalArgumentException("not a NexusOne repo root: " + root);
        }
        return root;
    }

    private void scanJavaTree(Path source, Path repoRoot, boolean applyAllowlist, List<Violation> violations)
            throws IOException {
        try (Stream<Path> files = Files.walk(source)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .forEach(path -> inspectJava(path, repoRoot, applyAllowlist, violations));
        }
    }

    private void inspectJava(Path path, Path repoRoot, boolean applyAllowlist, List<Violation> violations) {
        String content = read(path);
        boolean spawn = PROCESS_SPAWN.matcher(content).find();
        boolean dumpLiteral = PG_DUMP_LITERAL.matcher(content).find();
        boolean inline = PROCESS_PG_DUMP_INLINE.matcher(content).find();
        if (!inline && !(spawn && dumpLiteral)) {
            return;
        }
        String relative = relativize(repoRoot, path);
        boolean versionOnly = VERSION_ONLY.matcher(content).find();
        int builders = count(content, "new ProcessBuilder");
        boolean allowedProbe = applyAllowlist
                && pathAllowed(relative, OpsPhysicalDumpCatalog.ALLOWED_PRODUCT_PROBE_PATHS)
                && versionOnly
                && builders == 1
                && !inline;
        if (!allowedProbe) {
            violations.add(new Violation(
                    path,
                    OPS_PG_DUMP_IN_PRODUCT,
                    "product Java must not execute pg_dump; ops dump is out of SurfaceGuard"));
        }
    }

    private void scanOpsScripts(Path root, List<Violation> violations) throws IOException {
        for (String relative : OpsPhysicalDumpCatalog.OPS_SCRIPT_PATHS) {
            Path file = root.resolve(relative);
            if (!Files.isRegularFile(file)) {
                violations.add(new Violation(file, OPS_SCRIPT_MISSING, "documented ops pg_dump script is missing"));
                continue;
            }
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (!text.contains("pg_dump")) {
                violations.add(new Violation(file, OPS_SCRIPT_MISSING, "ops script must remain a pg_dump path"));
            }
            if (text.contains("SurfaceGuard") || text.contains("AuthorizedExport")) {
                violations.add(new Violation(
                        file,
                        OPS_SCRIPT_CLAIMS_SURFACE,
                        "ops pg_dump must not claim SurfaceGuard / AuthorizedExport"));
            }
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

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static int count(String text, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int at = text.indexOf(needle, from);
            if (at < 0) {
                return count;
            }
            count++;
            from = at + needle.length();
        }
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
