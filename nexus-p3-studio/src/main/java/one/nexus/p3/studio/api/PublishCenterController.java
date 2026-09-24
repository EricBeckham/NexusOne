package one.nexus.p3.studio.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.p3.infra.permission.FormSurfaceGuard;
import one.nexus.p3.studio.form.FormService;
import one.nexus.p3.studio.form.PublishCenterService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/p3/publish-center")
public class PublishCenterController {
    private final PublishCenterService center;
    private final ObjectProvider<FormSurfaceGuard> surfaces;

    public PublishCenterController(FormService forms, ObjectProvider<FormSurfaceGuard> surfaces) {
        this.center = new PublishCenterService(forms);
        this.surfaces = surfaces;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        requireRead();
        return center.list();
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable UUID id) {
        requireRead();
        return center.detail(id);
    }

    private void requireRead() {
        FormSurfaceGuard guard = surfaces.getIfAvailable();
        if (guard != null) {
            guard.requireRead();
        }
    }
}
