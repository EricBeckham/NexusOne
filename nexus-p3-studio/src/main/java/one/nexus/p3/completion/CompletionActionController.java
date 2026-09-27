package one.nexus.p3.completion;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.p3.infra.permission.CompletionActionSurfaceGuard;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** FA3: post-completion action config. Read = {@code process.action:read}, change = {@code process.action:write}. */
@RestController
@RequestMapping("/api/v1/p3/completion-actions")
public class CompletionActionController {
    private final CompletionActionService actions;
    private final ObjectProvider<CompletionActionSurfaceGuard> surfaces;

    public CompletionActionController(CompletionActionService actions,
            ObjectProvider<CompletionActionSurfaceGuard> surfaces) {
        this.actions = actions;
        this.surfaces = surfaces;
    }

    @GetMapping
    public List<CompletionAction> list() {
        requireRead();
        return actions.list();
    }

    @GetMapping("/{id}")
    public CompletionAction get(@PathVariable UUID id) {
        requireRead();
        return actions.get(id);
    }

    @PostMapping
    public CompletionAction create(@RequestBody Body body) {
        requireWrite();
        return actions.create(body.draft());
    }

    @PutMapping("/{id}")
    public CompletionAction update(@PathVariable UUID id, @RequestBody Body body) {
        requireWrite();
        return actions.update(id, body.version() == null ? -1 : body.version(), body.draft());
    }

    @PostMapping("/{id}/preview")
    public Map<String, Object> preview(@PathVariable UUID id, @RequestBody PreviewBody body) {
        requireRead();
        return Map.of("row", actions.preview(id, body.sourceRecordId(), body.outcome()));
    }

    private void requireRead() {
        CompletionActionSurfaceGuard guard = surfaces.getIfAvailable();
        if (guard != null) {
            guard.requireRead();
        }
    }

    private void requireWrite() {
        CompletionActionSurfaceGuard guard = surfaces.getIfAvailable();
        if (guard != null) {
            guard.requireWrite();
        }
    }

    public record Body(String name, String sourceLedgerCode, String processKey, CompletionAction.Trigger trigger,
            String targetLedgerCode, List<CompletionAction.FieldMapping> fieldMapping, Boolean enabled, Long version) {
        CompletionActionService.Draft draft() {
            return new CompletionActionService.Draft(name, sourceLedgerCode, processKey, trigger, targetLedgerCode,
                    fieldMapping, enabled);
        }
    }

    public record PreviewBody(UUID sourceRecordId, String outcome) {}
}
