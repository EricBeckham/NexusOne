package one.nexus.file.api;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import one.nexus.common.file.FileRef;
import one.nexus.file.FileObject;
import one.nexus.file.FileService;
import one.nexus.file.SignedUrl;
import one.nexus.isolation.context.AccessContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {
    private final FileService files;

    public FileController(FileService files) {
        this.files = files;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> upload(@Valid @RequestBody UploadRequest request) {
        byte[] body = decode(request.contentBase64());
        FileRef ref = files.upload(request.businessType(), request.fileName(), request.contentType(), body, request.resourceId());
        return Map.of(
                "file_id", ref.fileId().toString(),
                "tenant_id", ref.tenantId().toString(),
                "object_key", ref.objectKey());
    }

    @GetMapping("/{fileId}")
    public Map<String, Object> get(@PathVariable UUID fileId) {
        FileObject file = files.metadata(fileId);
        return Map.of(
                "file_id", file.fileId().toString(),
                "tenant_id", file.tenantId().toString(),
                "object_key", file.objectKey(),
                "content_type", file.contentType(),
                "size_bytes", file.sizeBytes(),
                "checksum_sha256", file.checksumSha256(),
                "status", file.status().name(),
                "owner_subject", file.ownerSubject());
    }

    @PostMapping("/{fileId}/signed-url")
    public Map<String, Object> sign(@PathVariable UUID fileId, @RequestBody(required = false) SignRequest request) {
        String subject = request != null && request.subject() != null
                ? request.subject()
                : AccessContextHolder.current().actor().actorId();
        SignedUrl signed = files.signDownload(fileId, subject);
        return Map.of(
                "file_id", signed.fileId().toString(),
                "url", signed.url(),
                "expires_at", signed.expiresAt().toString());
    }

    @DeleteMapping("/{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID fileId) {
        files.delete(fileId);
    }

    private static byte[] decode(String base64) {
        if (base64 == null || base64.isBlank()) {
            return "empty".getBytes(StandardCharsets.UTF_8);
        }
        return Base64.getDecoder().decode(base64);
    }

    public record UploadRequest(
            @NotBlank String businessType,
            String fileName,
            String contentType,
            @NotBlank String contentBase64,
            String resourceId) {}

    public record SignRequest(String subject) {}
}
