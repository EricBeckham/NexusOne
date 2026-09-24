package one.nexus.p3.flow.api;

import java.util.List;
import one.nexus.p3.flow.ProcessDefinitionDraft;
import one.nexus.p3.flow.ProcessDefinitionDraftService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/process/drafts")
@ConditionalOnProperty(name = "nexus.flow.engine")
public class ProcessDefinitionDraftController {
    private final ProcessDefinitionDraftService drafts;

    public ProcessDefinitionDraftController(ProcessDefinitionDraftService drafts) {
        this.drafts = drafts;
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

    public record SaveDraftBody(String name, String bpmnXml) {}
}
