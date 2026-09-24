package one.nexus.p2.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import one.nexus.p2.domain.Model;
import one.nexus.p2.idp.IdpConfigurationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/identity/providers")
public class IdpConfigurationController {
    private final IdpConfigurationService providers;

    public IdpConfigurationController(IdpConfigurationService providers) {
        this.providers = providers;
    }

    @GetMapping
    public List<Model.IdpConfiguration> list() {
        return providers.list();
    }

    @GetMapping("/{id}")
    public Model.IdpConfiguration get(@PathVariable UUID id) {
        return providers.get(id);
    }

    @PostMapping
    public ResponseEntity<Model.IdpConfiguration> create(@RequestBody IdpConfigurationBody body) {
        Model.IdpConfiguration created = providers.create(body.command());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public Model.IdpConfiguration update(@PathVariable UUID id, @RequestBody IdpConfigurationBody body) {
        return providers.update(id, body.command());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        providers.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record IdpConfigurationBody(
            String providerType,
            String displayName,
            String clientId,
            String issuer,
            String status
    ) {
        IdpConfigurationService.Command command() {
            return new IdpConfigurationService.Command(providerType, displayName, clientId, issuer, status);
        }
    }
}
