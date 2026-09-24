package one.nexus.p5.connect;

import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Secret subpaths only. The body is write-only and no response record has a plaintext field. */
@RestController
@RequestMapping("/api/v1/p5/credentials/{id}/secret")
public class CredentialSecretController {
    private final CredentialSecretService secrets;

    public CredentialSecretController(CredentialSecretService secrets) {
        this.secrets = secrets;
    }

    @GetMapping
    public CredentialSecretService.SecretStatus status(@PathVariable UUID id) {
        return secrets.status(id);
    }

    @PutMapping
    public CredentialSecretService.SecretWriteResult write(@PathVariable UUID id, @RequestBody(required = false) SecretBody body) {
        return secrets.write(id, body == null ? null : body.secret());
    }

    @PostMapping("/rotate")
    public CredentialSecretService.SecretWriteResult rotate(@PathVariable UUID id, @RequestBody(required = false) SecretBody body) {
        return secrets.rotate(id, body == null ? null : body.secret());
    }

    @DeleteMapping
    public CredentialSecretService.SecretStatus revoke(@PathVariable UUID id) {
        return secrets.revoke(id);
    }

    @PostMapping("/probe")
    public CredentialSecretService.SecretProbe probe(@PathVariable UUID id) {
        return secrets.probe(id);
    }

    public record SecretBody(String secret) {}
}
