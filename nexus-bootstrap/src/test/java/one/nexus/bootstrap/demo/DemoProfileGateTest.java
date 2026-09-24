package one.nexus.bootstrap.demo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DemoProfileGateTest {
    @Test
    void staysOffUnlessTheValueIsExactlyTrue() {
        assertFalse(DemoProfileGate.enabled(null));
        assertFalse(DemoProfileGate.enabled(""));
        assertFalse(DemoProfileGate.enabled("false"));
        assertTrue(DemoProfileGate.enabled("true"));
    }

    @Test
    void allowsLocalDemoAndRefusesProductionShapedTargets() {
        DemoProfileGate.assertAllowed(false, "production", "jdbc:postgresql://db.example:5432/nexusone");
        DemoProfileGate.assertAllowed(true, "local", "jdbc:postgresql://127.0.0.1:5432/nexusone");
        assertThrows(IllegalStateException.class,
                () -> DemoProfileGate.assertAllowed(true, "production", "jdbc:postgresql://127.0.0.1:5432/nexusone"));
        assertThrows(IllegalStateException.class,
                () -> DemoProfileGate.assertAllowed(true, "local", "jdbc:postgresql://db.internal:5432/nexusone"));
    }

    @Test
    void shipsTheSwitchOffAndDocumentsTheWarning() throws Exception {
        Path root = repoRoot();
        String app = Files.readString(root.resolve("nexus-bootstrap/src/main/resources/application.yaml"));
        String demo = Files.readString(root.resolve("nexus-bootstrap/src/main/resources/application-demo.yaml"));
        String doc = Files.readString(root.resolve("docs/engineering/demo-profile.md"));
        String enable = Files.readString(root.resolve("scripts/demo/enable.ps1"));
        String cleanup = Files.readString(root.resolve("scripts/demo/cleanup.sh"));

        assertTrue(app.contains("enabled: ${NEXUS_DEMO_ENABLED:false}"));
        assertFalse(app.contains("profiles:\n    active: demo"));
        assertTrue(demo.contains("禁止连生产"));
        assertTrue(demo.contains("enabled: true"));
        assertTrue(doc.contains("禁止连生产"));
        assertTrue(doc.contains("默认关闭"));
        assertTrue(doc.contains("清理"));
        assertTrue(enable.contains("禁止连生产"));
        assertTrue(cleanup.contains("没有业务行"));
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            if (Files.isRegularFile(dir.resolve("nexus-bootstrap/src/main/resources/application.yaml"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("repo root not found from " + Path.of("").toAbsolutePath());
    }
}
