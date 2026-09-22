package one.nexus.permission.scan;

import java.nio.file.Path;
import java.util.List;

/**
 * CLI for CI: {@code java ... OpsPgDumpHonestyCheckMain --repo <root>}
 * or {@code --source <tree> [--no-allowlist]}.
 */
public final class OpsPgDumpHonestyCheckMain {
    private OpsPgDumpHonestyCheckMain() {}

    public static void main(String[] args) throws Exception {
        Path repo = null;
        Path source = null;
        boolean allowlist = true;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--repo" -> repo = Path.of(args[++i]);
                case "--source" -> source = Path.of(args[++i]);
                case "--no-allowlist" -> allowlist = false;
                default -> throw new IllegalArgumentException("unknown argument: " + args[i]);
            }
        }
        OpsPgDumpHonestyGate gate = new OpsPgDumpHonestyGate();
        List<OpsPgDumpHonestyGate.Violation> violations;
        Path reported;
        if (source != null) {
            reported = source;
            violations = gate.scan(source, allowlist);
        } else {
            if (repo == null) {
                repo = OpsPgDumpHonestyGate.findRepoRoot(Path.of("."));
            }
            reported = repo;
            violations = gate.scanProduct(repo);
        }
        if (!violations.isEmpty()) {
            for (OpsPgDumpHonestyGate.Violation violation : violations) {
                System.err.println(violation.file() + ": " + violation.rule() + " (" + violation.detail() + ")");
            }
            System.exit(1);
        }
        System.out.println("PASS ops pg_dump honesty gate: " + reported.toAbsolutePath().normalize());
    }
}
