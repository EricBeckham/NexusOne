package one.nexus.p3.flow.api;

import java.util.List;
import one.nexus.p3.flow.ProcessDefinitionDraft;
import one.nexus.p3.flow.ProcessDefinitionDraftService;
import one.nexus.p3.flow.ProcessDefinitionPublishService;
import one.nexus.p3.infra.permission.ProcessDefinitionSurfaceGuard;
import org.springframework.beans.factory.ObjectProvider;
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
    private final ProcessDefinitionPublishService publishing;
    private final ObjectProvider<ProcessDefinitionSurfaceGuard> surfaces;

    public ProcessDefinitionDraftController(
            ProcessDefinitionDraftService drafts,
            ProcessDefinitionPublishService publishing,
            ObjectProvider<ProcessDefinitionSurfaceGuard> surfaces
    ) {
        this.drafts = drafts;
        this.publishing = publishing;
        this.surfaces = surfaces;
    }

    @GetMapping
    public List<ProcessDefinitionDraft> list() {
        requireRead();
        return drafts.list();
    }

    @GetMapping("/{key}")
    public ProcessDefinitionDraft get(@PathVariable String key) {
        requireRead();
        return drafts.get(key);
    }

    @PutMapping("/{key}")
    public ProcessDefinitionDraft save(@PathVariable String key, @RequestBody SaveDraftBody body) {
        requireWrite();
        return drafts.save(key, body.name(), body.bpmnXml());
    }

    @PostMapping("/{key}/publish")
    public ProcessDefinitionPublishService.PublishedDefinition publish(@PathVariable String key) {
        requireWrite();
        return publishing.publish(key);
    }

    @GetMapping("/{key}/publication")
    public ProcessDefinitionPublishService.PublishedDefinition publication(@PathVariable String key) {
        requireRead();
        return publishing.get(key);
    }

    private void requireRead() {
        ProcessDefinitionSurfaceGuard guard = surfaces.getIfAvailable();
        if (guard != null) {
            guard.requireRead();
        }
    }

    private void requireWrite() {
        ProcessDefinitionSurfaceGuard guard = surfaces.getIfAvailable();
        if (guard != null) {
            guard.requireWrite();
        }
    }

    public record SaveDraftBody(String name, String bpmnXml) {}
}
