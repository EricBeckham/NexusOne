package one.nexus.bootstrap.content;

import one.nexus.bootstrap.content.DemoRemokeService.RemokePlan;
import one.nexus.bootstrap.content.DemoRemokeService.RemokeRequest;
import one.nexus.bootstrap.content.DemoRemokeService.RemokeResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DL-L2: controlled demo-host remoke (see docs/engineering/runbooks/demo-host-remoke-discipline.md, DL-X3).
 * Plan first, then execute with the confirmation token. Never exposed through scripts/demo/reset.ps1.
 */
@RestController
@RequestMapping("/api/v1/platform/demo-remoke")
public class DemoRemokeController {
    private final DemoRemokeService remoke;

    public DemoRemokeController(DemoRemokeService remoke) {
        this.remoke = remoke;
    }

    @PostMapping("/plan")
    public RemokePlan plan() {
        return remoke.plan();
    }

    @PostMapping("/execute")
    public RemokeResult execute(@RequestBody(required = false) RemokeRequest request) {
        return remoke.execute(request);
    }
}
