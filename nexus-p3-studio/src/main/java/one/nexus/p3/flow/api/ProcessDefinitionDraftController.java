package one.nexus.p3.flow.api;

import java.util.List;
import one.nexus.p3.flow.ProcessDefinitionDraft;
import one.nexus.p3.flow.ProcessDefinitionDraftService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/process/drafts")
@ConditionalOnProperty(name = "nexus.flow.engine")
public class ProcessDefinitionDraftController {
    private final ProcessDefinitionDraftService drafts;
    private final one.nexus.p3.flow.ProcessDefinitionPublishService publishing;

    public ProcessDefinitionDraftController(
            ProcessDefinitionDraftService drafts,
            one.nexus.p3.flow.ProcessDefinitionPublishService publishing
    ) {
        this.drafts = drafts;
        this.publishing = publishing;
    }

    @GetMapping
    public List<ProcessDefinitionDraft> list() {
        return drafts.list();
    }

    @GetMapping("/{key}")
    public ProcessDefinitionDraft get(@PathVariable String key) {
        return drafts.get(key);
    }

    @PutMapping("/{key}")
    public ProcessDefinitionDraft save(@PathVariable String key, @RequestBody SaveDraftBody body) {
        return drafts.save(key, body.name(), body.bpmnXml());
    }

    @PostMapping("/{key}/publish")
    public one.nexus.p3.flow.ProcessDefinitionPublishService.PublishedDefinition publish(@PathVariable String key) {
        return publishing.publish(key);
    }

    @GetMapping("/{key}/publication")
    public one.nexus.p3.flow.ProcessDefinitionPublishService.PublishedDefinition publication(@PathVariable String key) {
        return publishing.get(key);
    }

    public record SaveDraftBody(String name, String bpmnXml) {}
}
