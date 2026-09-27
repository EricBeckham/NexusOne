package one.nexus.bootstrap.readiness;

import one.nexus.bootstrap.readiness.DemoReadinessService.DemoReadiness;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** DL-L3: platform-only, read-only demo readiness (presence booleans; never values). */
@RestController
@RequestMapping("/api/v1/platform/demo-readiness")
public class DemoReadinessController {
    private final DemoReadinessService readiness;

    public DemoReadinessController(DemoReadinessService readiness) {
        this.readiness = readiness;
    }

    @GetMapping
    public DemoReadiness read() {
        return readiness.read();
    }
}
