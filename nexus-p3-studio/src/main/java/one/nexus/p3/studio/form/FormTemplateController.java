package one.nexus.p3.studio.form;

import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p3.infra.jdbc.FormTemplateDraftService;

@RestController
@ConditionalOnBean(IsolationGuard.class)
@RequestMapping("/api/v1/p1/platform/form-templates")
public class FormTemplateController {
    private final IsolationGuard isolation;
    private final FormTemplateDraftService templates;

    public FormTemplateController(IsolationGuard isolation, FormTemplateDraftService templates) {
        this.isolation = isolation;
        this.templates = templates;
    }

    @GetMapping
    public List<FormTemplateDraftService.TemplateView> list() {
        isolation.requirePlatformContext("form-template.list", PlatformDuty.SYSTEM_ADMIN);
        return templates.list();
    }

    @PostMapping
    public FormTemplateDraftService.TemplateView create(@RequestBody FormTemplateDraftService.TemplateWrite request) {
        isolation.requirePlatformContext("form-template.create", PlatformDuty.SYSTEM_ADMIN);
        return templates.create(request);
    }

    @GetMapping("/{id}")
    public FormTemplateDraftService.TemplateView get(@PathVariable UUID id) {
        isolation.requirePlatformContext("form-template.get", PlatformDuty.SYSTEM_ADMIN);
        return templates.get(id);
    }

    @PutMapping("/{id}")
    public FormTemplateDraftService.TemplateView update(
            @PathVariable UUID id, @RequestBody FormTemplateDraftService.TemplateWrite request) {
        isolation.requirePlatformContext("form-template.update", PlatformDuty.SYSTEM_ADMIN);
        return templates.update(id, request);
    }

    @PostMapping("/{id}/validate")
    public FormTemplateDraftService.ValidationResult validate(@PathVariable UUID id) {
        isolation.requirePlatformContext("form-template.validate", PlatformDuty.SYSTEM_ADMIN);
        return templates.validateDraft(id);
    }

    @PostMapping("/{id}/publish")
    public FormTemplateDraftService.PublishResult publish(@PathVariable UUID id) {
        isolation.requirePlatformContext("form-template.publish", PlatformDuty.SYSTEM_ADMIN);
        return templates.publishDraft(id);
    }
}
