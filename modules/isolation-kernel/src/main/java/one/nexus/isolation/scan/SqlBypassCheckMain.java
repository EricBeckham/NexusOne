package one.nexus.isolation.scan;

import java.nio.file.Path;
import java.util.List;

/** CLI for CI: {@code java ... SqlBypassCheckMain --source <root> [--infra <dir>]} */
public final class SqlBypassCheckMain {
    private SqlBypassCheckMain() {}

    public static void main(String[] args) throws Exception {
        Path source = null;
        Path infra = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--source" -> source = Path.of(args[++i]);
                case "--infra" -> infra = Path.of(args[++i]);
                default -> throw new IllegalArgumentException("unknown argument: " + args[i]);
            }
        }
        if (source == null) {
            throw new IllegalArgumentException("--source is required");
        }
        List<SqlBypassScanner.Violation> violations = new SqlBypassScanner().scan(source, infra);
        if (!violations.isEmpty()) {
            for (SqlBypassScanner.Violation violation : violations) {
                System.err.println(violation.file() + ": " + violation.rule());
            }
            System.exit(1);
        }
        System.out.println("PASS static bypass check: " + source.toAbsolutePath());
    }
}
