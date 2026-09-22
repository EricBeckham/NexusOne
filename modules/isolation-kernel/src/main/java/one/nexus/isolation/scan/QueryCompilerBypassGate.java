package one.nexus.isolation.scan;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Checkstyle / SpotBugs-style static gate: product {@code src/main} must not
 * open raw JDBC / native SQL against tenant tables while skipping
 * {@code QueryCompiler} / {@code IsolationKernel.jdbc()}.
 *
 * <p>Two rules:
 * <ul>
 *   <li>{@code raw-jdbc-outside-allowlist} — JDBC / native-SQL APIs outside the
 *       documented infrastructure prefixes</li>
 *   <li>{@code tenant-table-sql-bypass} — those APIs plus a tenant physical /
 *       Query AST table identifier, outside the compiler / SchemaService /
 *       GuardedLedgerStore files</li>
 * </ul>
 */
public final class QueryCompilerBypassGate {
    public static final String RAW_JDBC = "raw-jdbc-outside-allowlist";
    public static final String TENANT_TABLE_SQL = "tenant-table-sql-bypass";

    static final Map<String, Pattern> JDBC_APIS;

    static {
        Map<String, Pattern> apis = new LinkedHashMap<>();
        apis.put(
                "direct JDBC",
                Pattern.compile(
                        "java\\.sql\\.(Connection|DriverManager|Statement|PreparedStatement)|\\b(prepareStatement|createStatement)\\s*\\("));
        apis.put("Spring JDBC", Pattern.compile("\\b(JdbcTemplate|NamedParameterJdbcTemplate|JdbcClient)\\b"));
        apis.put("JPA native SQL", Pattern.compile("\\bcreateNativeQuery\\s*\\(|\\bnativeQuery\\s*=\\s*true\\b"));
        JDBC_APIS = Map.copyOf(apis);
    }

    static final Pattern MYBATIS_LITERAL = Pattern.compile("\\$\\{[^}]+\\}");
    static final Pattern MYBATIS_MAPPER = Pattern.compile("<mapper\\b");

    /**
     * Tenant physical tables ({@code l_*} / {@code a_*}) and the Query AST
     * names used by kernel tests and IsolatedExport. Identifiers are split so
     * this source file is not itself a tenant-table hit.
     */
    static final Pattern TENANT_TABLE = Pattern.compile(
            "\\b((?:l|a)_[a-z][a-z0-9_]*|ledger_" + "entry|ledger_" + "line|ledger_" + "note)\\b",
            Pattern.CASE_INSENSITIVE);

    static final Pattern TENANT_TABLE_SQL_CONTEXT = Pattern.compile(
            "(?i)\\b(from|into|update|join|table)\\s+[\"']?(?:l|a)_[a-z][a-z0-9_]*"
                    + "|(?i)\\b(from|into|update|join|table)\\s+[\"']?ledger_" + "(entry|line|note)\\b");

    public record Violation(Path file, String rule, String detail) {}

    public List<Violation> scanProduct(Path repoRoot) throws IOException {
        Path root = repoRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root.resolve("modules/isolation-kernel"))
                || !Files.isRegularFile(root.resolve("pom.xml"))) {
            throw new IllegalArgumentException("not a NexusOne repo root: " + root);
        }
        List<Violation> violations = new ArrayList<>();
        for (String module : QueryCompilerBypassAllowlist.PRODUCT_SOURCE_ROOTS) {
            Path main = root.resolve(module).resolve("src/main");
            if (Files.isDirectory(main)) {
                scanTree(main, root, true, violations);
            }
        }
        return List.copyOf(violations);
    }

    /**
     * Scan a fixture tree. {@code applyAllowlist=false} treats every JDBC hit
     * as a violation (used to prove an intentional caller fails the gate).
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
                    && Files.isDirectory(current.resolve("modules/isolation-kernel"))
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
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.endsWith(".java") || name.endsWith(".xml");
                    })
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
        boolean jdbc = usesJdbcApi(path, content);
        boolean tenantSql = TENANT_TABLE_SQL_CONTEXT.matcher(content).find()
                || (jdbc && TENANT_TABLE.matcher(content).find());
        if (!jdbc && !tenantSql) {
            return;
        }
        String relative = relativize(repoRoot, path);
        if (jdbc && (!applyAllowlist || !jdbcAllowed(relative))) {
            violations.add(new Violation(path, RAW_JDBC, firstJdbcDetail(path, content)));
        }
        if (tenantSql && (!applyAllowlist || !tenantTableSqlAllowed(relative))) {
            violations.add(new Violation(path, TENANT_TABLE_SQL, "tenant table SQL outside QueryCompiler path"));
        }
    }

    static boolean usesJdbcApi(Path path, String content) {
        if (JDBC_APIS.values().stream().anyMatch(pattern -> pattern.matcher(content).find())) {
            return true;
        }
        return isMyBatisLiteral(path, content);
    }

    private static boolean isMyBatisLiteral(Path path, String content) {
        String name = path.getFileName().toString();
        return name.endsWith(".xml")
                && MYBATIS_MAPPER.matcher(content).find()
                && MYBATIS_LITERAL.matcher(content).find();
    }

    private static String firstJdbcDetail(Path path, String content) {
        for (Map.Entry<String, Pattern> entry : JDBC_APIS.entrySet()) {
            if (entry.getValue().matcher(content).find()) {
                return entry.getKey();
            }
        }
        if (isMyBatisLiteral(path, content)) {
            return "MyBatis literal substitution";
        }
        return "JDBC";
    }

    static boolean jdbcAllowed(String relative) {
        String normalized = relative.replace('\\', '/');
        for (String prefix : QueryCompilerBypassAllowlist.jdbcPathPrefixes()) {
            if (normalized.equals(prefix) || normalized.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    static boolean tenantTableSqlAllowed(String relative) {
        String normalized = relative.replace('\\', '/');
        for (String allowed : QueryCompilerBypassAllowlist.TENANT_TABLE_SQL_PATHS) {
            if (normalized.equals(allowed)) {
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
