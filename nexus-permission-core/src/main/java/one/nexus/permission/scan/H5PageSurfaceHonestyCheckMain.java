package one.nexus.permission.scan;

import java.nio.file.Path;
import java.util.List;

/**
 * CLI for CI: {@code java ... H5PageSurfaceHonestyCheckMain --repo <root>}
 * or {@code --source <tree>}.
 */
public final class H5PageSurfaceHonestyCheckMain {
    private H5PageSurfaceHonestyCheckMain() {}

    public static void main(String[] args) throws Exception {
        Path repo = null;
        Path source = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--repo" -> repo = Path.of(args[++i]);
                case "--source" -> source = Path.of(args[++i]);
                default -> throw new IllegalArgumentException("unknown argument: " + args[i]);
            }
        }
        H5PageSurfaceHonestyGate gate = new H5PageSurfaceHonestyGate();
        List<H5PageSurfaceHonestyGate.Violation> violations;
        Path reported;
        if (source != null) {
            reported = source;
            violations = gate.scan(source);
        } else {
            if (repo == null) {
                repo = H5PageSurfaceHonestyGate.findRepoRoot(Path.of("."));
            }
            reported = repo;
            violations = gate.scanProduct(repo);
        }
        if (!violations.isEmpty()) {
            for (H5PageSurfaceHonestyGate.Violation violation : violations) {
                System.err.println(violation.file() + ": " + violation.rule() + " (" + violation.detail() + ")");
            }
            System.exit(1);
        }
        System.out.println("PASS H5 every-page SurfaceGuard honesty gate: " + reported.toAbsolutePath().normalize());
    }
}
