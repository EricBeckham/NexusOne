package one.nexus.isolation.scan;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * CI static gate: business modules must not open JDBC/JPA/MyBatis SQL backdoors.
 * Infrastructure roots (this kernel, later JDBC adapters) are exempt.
 */
public final class SqlBypassScanner {
    public static final Map<String, Pattern> RULES;

    static {
        Map<String, Pattern> rules = new LinkedHashMap<>();
        rules.put("direct JDBC", Pattern.compile("java\\.sql\\.(Connection|DriverManager|Statement|PreparedStatement)|\\b(prepareStatement|createStatement)\\s*\\("));
        rules.put("Spring JDBC", Pattern.compile("\\b(JdbcTemplate|NamedParameterJdbcTemplate|JdbcClient)\\b"));
        rules.put("JPA native SQL", Pattern.compile("\\bcreateNativeQuery\\s*\\(|\\bnativeQuery\\s*=\\s*true\\b"));
        rules.put("MyBatis literal substitution", Pattern.compile("\\$\\{[^}]+\\}"));
        rules.put("client-forged TenantContext", Pattern.compile("\\bnew\\s+TenantContext\\s*\\("));
        rules.put("client-forged PlatformContext", Pattern.compile("\\bnew\\s+PlatformContext\\s*\\("));
        RULES = Map.copyOf(rules);
    }

    public record Violation(Path file, String rule) {}

    public List<Violation> scan(Path sourceRoot, Path infrastructureRoot) throws IOException {
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("SourceRoot missing: " + sourceRoot);
        }
        Path source = sourceRoot.toAbsolutePath().normalize();
        Path infra = infrastructureRoot == null ? null : infrastructureRoot.toAbsolutePath().normalize();
        if (infra != null) {
            if (!Files.isDirectory(infra)) {
                throw new IllegalArgumentException("InfrastructureRoot missing: " + infra);
            }
            if (!infra.startsWith(source)) {
                throw new IllegalArgumentException("InfrastructureRoot must be inside SourceRoot");
            }
        }
        List<Violation> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(source)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.endsWith(".java") || name.endsWith(".xml");
                    })
                    .forEach(path -> {
                        if (infra != null && path.startsWith(infra)) {
                            return;
                        }
                        String content;
                        try {
                            content = Files.readString(path, StandardCharsets.UTF_8);
                        } catch (IOException ex) {
                            throw new IllegalStateException(ex);
                        }
                        RULES.forEach((rule, pattern) -> {
                            if (pattern.matcher(content).find()) {
                                violations.add(new Violation(path, rule));
                            }
                        });
                    });
        }
        return violations;
    }
}
