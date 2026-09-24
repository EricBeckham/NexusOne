package one.nexus.p5.connect;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Metadata only. Secret write, rotate, and revoke stay on {@code /secret}. */
@RestController
@RequestMapping("/api/v1/p5/credentials")
public class CredentialMetadataController {
    private final CredentialMetadataService metadata;

    public CredentialMetadataController(CredentialMetadataService metadata) {
        this.metadata = metadata;
    }

    @GetMapping
    public List<CredentialMetadata> list() {
        return metadata.list();
    }

    @GetMapping("/{id}")
    public CredentialMetadata get(@PathVariable UUID id) {
        return metadata.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CredentialMetadata create(@RequestBody(required = false) CredentialBody body) {
        return metadata.create(body == null ? null : body.name(), body == null ? null : body.provider(), body == null ? null : body.expiresAt());
    }

    @PatchMapping("/{id}")
    public CredentialMetadata update(@PathVariable UUID id, @RequestBody(required = false) CredentialBody body) {
        return metadata.update(id, body == null ? null : body.name(), body == null ? null : body.provider(), body == null ? null : body.expiresAt());
    }

    public record CredentialBody(String name, String provider, Instant expiresAt) {}
}
