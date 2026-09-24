package one.nexus.p2.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import one.nexus.p2.domain.Model;
import one.nexus.p2.idp.IdpConfigurationService;
import one.nexus.p2.idp.IdpSecretService;
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
    private final IdpSecretService secrets;

    public IdpConfigurationController(IdpConfigurationService providers, IdpSecretService secrets) {
        this.providers = providers;
        this.secrets = secrets;
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
        secrets.deleteSecretIfConfigured(id);
        providers.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/secret")
    public IdpSecretService.SecretStatus secretStatus(@PathVariable UUID id) {
        return secrets.status(id);
    }

    @PutMapping("/{id}/secret")
    public IdpSecretService.SecretWriteResult writeSecret(
            @PathVariable UUID id, @RequestBody SecretBody body) {
        return secrets.write(id, body == null ? null : body.clientSecret());
    }

    @PostMapping("/{id}/secret/rotate")
    public IdpSecretService.SecretWriteResult rotateSecret(
            @PathVariable UUID id, @RequestBody SecretBody body) {
        return secrets.rotate(id, body == null ? null : body.clientSecret());
    }

    @PostMapping("/{id}/test-connection")
    public IdpSecretService.ConnectionTestResult testConnection(@PathVariable UUID id) {
        return secrets.testConnection(id);
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

    public record SecretBody(String clientSecret) {}
}
