package one.nexus.file.infra;

import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.Objects;
import one.nexus.crypto.SecretNames;
import one.nexus.crypto.SecretPort;
import one.nexus.file.ObjectStoragePort;

/** P0-06 MinIO Java SDK adapter. FileService must authorize before calling. */
public final class MinioObjectStorage implements ObjectStoragePort {
    private final MinioClient client;

    /**
     * Product path: credentials come only from {@link SecretPort} (S0 local+env).
     * Do not pass access/secret keys from ordinary Spring properties.
     */
    public static MinioObjectStorage fromSecretPort(String endpoint, SecretPort secrets) {
        Objects.requireNonNull(secrets, "secrets");
        return new MinioObjectStorage(
                endpoint,
                secrets.getUtf8(SecretNames.S3_ACCESS_KEY),
                secrets.getUtf8(SecretNames.S3_SECRET_KEY));
    }

    public MinioObjectStorage(String endpoint, String accessKey, String secretKey) {
        this.client = MinioClient.builder()
                .endpoint(Objects.requireNonNull(endpoint, "endpoint"))
                .credentials(accessKey, secretKey)
                .build();
    }

    @Override
    public void put(String bucket, String objectKey, byte[] body, String contentType, long partSize) {
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(body), (long) body.length, partSize)
                    .contentType(contentType)
                    .build());
        } catch (Exception ex) {
            throw new IllegalStateException("S3 put failed", ex);
        }
    }

    @Override
    public byte[] get(String bucket, String objectKey) {
        try (InputStream stream = client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            stream.transferTo(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("S3 get failed", ex);
        }
    }

    @Override
    public String presignGet(String bucket, String objectKey, Duration ttl) {
        try {
            int expiry = (int) Math.max(1, Math.min(ttl.toSeconds(), 7 * 24 * 3600));
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Http.Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(expiry)
                    .build());
        } catch (Exception ex) {
            throw new IllegalStateException("S3 presign failed", ex);
        }
    }

    @Override
    public void delete(String bucket, String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception ex) {
            throw new IllegalStateException("S3 delete failed", ex);
        }
    }
}
