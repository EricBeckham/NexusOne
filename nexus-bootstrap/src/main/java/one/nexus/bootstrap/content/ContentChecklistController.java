package one.nexus.bootstrap.content;

import one.nexus.bootstrap.content.ContentBootstrapService.StepResult;
import one.nexus.bootstrap.content.ContentChecklistService.Checklist;
import one.nexus.bootstrap.content.ContentChecklistService.StepRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** DL-L4: presence checklist (platform admin) and apply-one-step (same authorisation as DL-L1). */
@RestController
@RequestMapping("/api/v1/platform/content-bootstrap")
public class ContentChecklistController {
    private final ContentChecklistService checklist;

    public ContentChecklistController(ContentChecklistService checklist) {
        this.checklist = checklist;
    }

    @GetMapping("/checklist")
    public Checklist checklist() {
        return checklist.checklist();
    }

    @PostMapping("/steps/{key}")
    public StepResult apply(@PathVariable String key, @RequestBody(required = false) StepRequest request) {
        return checklist.apply(key, request);
    }
}
