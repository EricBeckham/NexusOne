package one.nexus.p1.api;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import one.nexus.p1.api.ApiModels.ApplyTenantRegistrationRequest;
import one.nexus.p1.api.ApiModels.ReviewRegistrationRequest;
import one.nexus.p1.api.ApiModels.TenantRegistrationView;
import one.nexus.p1.application.TenantRegistrationService;
import one.nexus.p1.application.TenantRegistrationService.ApplyCommand;
import one.nexus.p1.domain.RegistrationStatus;

@RestController
@RequestMapping("/api/v1/p1/tenant-registrations")
public class TenantRegistrationController {

    private final TenantRegistrationService registrations;

    public TenantRegistrationController(TenantRegistrationService registrations) {
        this.registrations = registrations;
    }

    /** Public apply — org name + admin contact. No payment. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantRegistrationView apply(@Valid @RequestBody ApplyTenantRegistrationRequest request) {
        return TenantRegistrationView.of(registrations.apply(new ApplyCommand(
                request.orgName(),
                request.adminDisplayName(),
                request.adminEmail(),
                request.contactNote())));
    }

    @GetMapping
    public List<TenantRegistrationView> list(@RequestParam(required = false) String status) {
        RegistrationStatus filter = status == null || status.isBlank()
                ? null
                : RegistrationStatus.valueOf(status.trim().toUpperCase());
        return registrations.list(filter).stream().map(TenantRegistrationView::of).toList();
    }

    @GetMapping("/{id}")
    public TenantRegistrationView get(@PathVariable UUID id) {
        return TenantRegistrationView.of(registrations.get(id));
    }

    @PostMapping("/{id}/approve")
    public TenantRegistrationView approve(
            @PathVariable UUID id, @RequestBody(required = false) ReviewRegistrationRequest body) {
        return TenantRegistrationView.of(registrations.approve(id, body == null ? null : body.note()));
    }

    @PostMapping("/{id}/reject")
    public TenantRegistrationView reject(
            @PathVariable UUID id, @RequestBody(required = false) ReviewRegistrationRequest body) {
        return TenantRegistrationView.of(registrations.reject(id, body == null ? null : body.note()));
    }
}