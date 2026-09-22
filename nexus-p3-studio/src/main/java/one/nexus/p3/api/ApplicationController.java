package one.nexus.p3.api;

import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import one.nexus.p3.api.ApiModels.ApplicationView;
import one.nexus.p3.api.ApiModels.CreateApplicationRequest;
import one.nexus.p3.application.ApplicationService;
import one.nexus.p3.domain.ApplicationKind;
import one.nexus.p3.infra.permission.CatalogSurfaceGuard;

@RestController
@RequestMapping("/api/v1/p3/applications")
public class ApplicationController {
    private final ApplicationService applications;
    private final ObjectProvider<CatalogSurfaceGuard> surfaces;

    public ApplicationController(
            ApplicationService applications, ObjectProvider<CatalogSurfaceGuard> surfaces) {
        this.applications = applications;
        this.surfaces = surfaces;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationView create(@Valid @RequestBody CreateApplicationRequest request) {
        requireWrite();
        return ApplicationView.of(applications.create(
                request.appCode(), request.displayName(), ApplicationKind.fromApi(request.kind())));
    }

    @GetMapping
    public List<ApplicationView> list() {
        requireRead();
        return applications.list().stream().map(ApplicationView::of).toList();
    }

    @GetMapping("/{id}")
    public ApplicationView get(@PathVariable UUID id) {
        requireRead();
        return ApplicationView.of(applications.get(id));
    }

    private void requireRead() {
        CatalogSurfaceGuard guard = surfaces.getIfAvailable();
        if (guard != null) {
            guard.requireApplicationRead();
        }
    }

    private void requireWrite() {
        CatalogSurfaceGuard guard = surfaces.getIfAvailable();
        if (guard != null) {
            guard.requireApplicationWrite();
        }
    }
}
