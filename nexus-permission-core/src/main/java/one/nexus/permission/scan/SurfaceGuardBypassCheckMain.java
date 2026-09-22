package one.nexus.permission.scan;

import java.nio.file.Path;
import java.util.List;

/**
 * CLI for CI: {@code java ... SurfaceGuardBypassCheckMain --repo <root>}
 * or {@code --source <tree> [--no-allowlist]}.
 */
public final class SurfaceGuardBypassCheckMain {
    private SurfaceGuardBypassCheckMain() {}

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
        SurfaceGuardBypassGate gate = new SurfaceGuardBypassGate();
        List<SurfaceGuardBypassGate.Violation> violations;
        Path reported;
        if (source != null) {
            reported = source;
            violations = gate.scan(source, allowlist);
        } else {
            if (repo == null) {
                repo = SurfaceGuardBypassGate.findRepoRoot(Path.of("."));
            }
            reported = repo;
            violations = gate.scanProduct(repo);
        }
        if (!violations.isEmpty()) {
            for (SurfaceGuardBypassGate.Violation violation : violations) {
                System.err.println(violation.file() + ": " + violation.rule() + " (" + violation.detail() + ")");
            }
            System.exit(1);
        }
        System.out.println("PASS SurfaceGuard bypass gate: " + reported.toAbsolutePath().normalize());
    }
}
