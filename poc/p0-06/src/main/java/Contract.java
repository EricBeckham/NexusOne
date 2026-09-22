import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.regex.Pattern;

public final class Contract {
    private Contract() {}
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    public interface Storage {
        void put(String bucket, String key, byte[] bytes, String contentType, long partSize) throws Exception;
        byte[] get(String bucket, String key) throws Exception;
        String presignGet(String bucket, String key) throws Exception;
        void delete(String bucket, String key) throws Exception;
    }
    public static final class MinioStorage implements Storage {
        private final MinioClient client;
        public MinioStorage(String endpoint, String accessKey, String secretKey) {
            client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        }
        public void put(String bucket, String key, byte[] bytes, String contentType, long partSize) throws Exception {
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(key)
                .stream(new ByteArrayInputStream(bytes), Long.valueOf(bytes.length), Long.valueOf(partSize)).contentType(contentType).build());
        }
        public byte[] get(String bucket, String key) throws Exception {
            try (InputStream stream = client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192]; int count;
                while ((count = stream.read(buffer)) != -1) out.write(buffer, 0, count);
                return out.toByteArray();
            }
        }
        public String presignGet(String bucket, String key) throws Exception {
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Http.Method.GET)
                .bucket(bucket).object(key).expiry(60).build());
        }
        public void delete(String bucket, String key) throws Exception {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        }
    }
    public static final class FileRef {
        public final String tenantId, bucket, objectKey, ownerSubject, resourceId;
        public FileRef(String tenantId, String bucket, String objectKey, String ownerSubject, String resourceId) {
            this.tenantId = segment(tenantId); this.bucket = bucket; this.objectKey = objectKey;
            this.ownerSubject = segment(ownerSubject); this.resourceId = segment(resourceId);
        }
    }
    public interface Permission { boolean mayRead(String subject, String resourceId); }
    public static final class FileService {
        private final Storage storage;
        private final String environment;
        private final Clock clock;
        public FileService(Storage storage, String environment, Clock clock) {
            this.storage = storage; this.environment = segment(environment); this.clock = clock;
        }
        public String key(String tenantId, String businessType, UUID id) {
            LocalDate now = LocalDate.now(clock.withZone(ZoneOffset.UTC));
            return environment + "/" + segment(tenantId) + "/" + segment(businessType)
                + "/" + now.getYear() + "/" + String.format("%02d", now.getMonthValue()) + "/" + id;
        }
        public String signDownload(String tenantId, String subject, FileRef file, Permission permission) throws Exception {
            segment(tenantId); segment(subject);
            if (file == null || !tenantId.equals(file.tenantId)) throw new SecurityException("file tenant mismatch");
            if (!file.objectKey.startsWith(environment + "/" + tenantId + "/")) throw new SecurityException("object prefix mismatch");
            if (!subject.equals(file.ownerSubject)) throw new SecurityException("file ownership mismatch");
            if (!permission.mayRead(subject, file.resourceId)) throw new SecurityException("resource permission denied");
            return storage.presignGet(file.bucket, file.objectKey);
        }
    }
    private static String segment(String value) {
        if (value == null || !SEGMENT.matcher(value).matches()) throw new SecurityException("invalid object key segment");
        return value;
    }
}
