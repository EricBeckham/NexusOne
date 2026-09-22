package one.nexus.isolation.scan;

import java.nio.file.Path;
import java.util.List;

/**
 * CLI for CI: {@code java ... QueryCompilerBypassCheckMain --repo <root>}.
 * Exit 1 when product {@code src/main} has a QueryCompiler bypass.
 */
public final class QueryCompilerBypassCheckMain {
    private QueryCompilerBypassCheckMain() {}

    public static void main(String[] args) throws Exception {
        Path repo = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--repo" -> repo = Path.of(args[++i]);
                default -> throw new IllegalArgumentException("unknown argument: " + args[i]);
            }
        }
        if (repo == null) {
            repo = QueryCompilerBypassGate.findRepoRoot(Path.of(System.getProperty("user.dir")));
        }
        List<QueryCompilerBypassGate.Violation> violations = new QueryCompilerBypassGate().scanProduct(repo);
        if (!violations.isEmpty()) {
            for (QueryCompilerBypassGate.Violation violation : violations) {
                System.err.println(violation.file() + ": " + violation.rule() + " (" + violation.detail() + ")");
            }
            System.exit(1);
        }
        System.out.println("PASS QueryCompiler bypass gate: " + repo.toAbsolutePath().normalize());
    }
}
