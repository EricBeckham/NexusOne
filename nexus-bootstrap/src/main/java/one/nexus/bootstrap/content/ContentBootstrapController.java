package one.nexus.bootstrap.content;

import one.nexus.bootstrap.content.ContentBootstrapService.BootstrapRequest;
import one.nexus.bootstrap.content.ContentBootstrapService.BootstrapResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** DL-L1: platform-admin, window-gated creation of the minimal demo content (no NEXUS_DEMO_ENABLED needed). */
@RestController
@RequestMapping("/api/v1/platform/content-bootstrap")
public class ContentBootstrapController {
    private final ContentBootstrapService bootstrap;

    public ContentBootstrapController(ContentBootstrapService bootstrap) {
        this.bootstrap = bootstrap;
    }

    @PostMapping
    public BootstrapResult apply(@RequestBody(required = false) BootstrapRequest request) {
        return bootstrap.bootstrap(request);
    }
}
