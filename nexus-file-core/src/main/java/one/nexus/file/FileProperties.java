package one.nexus.file;

import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nexus.files")
public class FileProperties {
    private String persistence = "memory";
    private String storage = "memory";
    private String bucketAlias = "nexus-files";
    private long maxSizeBytes = 20L * 1024 * 1024;
    private Duration signedTtl = Duration.ofSeconds(60);
    private Set<String> allowedMime = Set.of(
            "application/octet-stream",
            "application/pdf",
            "image/png",
            "image/jpeg",
            "text/plain",
            "text/csv");
    private boolean demoContext = false;
    private S3 s3 = new S3();

    public String getPersistence() {
        return persistence;
    }

    public void setPersistence(String persistence) {
        this.persistence = persistence;
    }

    public String getStorage() {
        return storage;
    }

    public void setStorage(String storage) {
        this.storage = storage;
    }

    public String getBucketAlias() {
        return bucketAlias;
    }

    public void setBucketAlias(String bucketAlias) {
        this.bucketAlias = bucketAlias;
    }

    public long getMaxSizeBytes() {
        return maxSizeBytes;
    }

    public void setMaxSizeBytes(long maxSizeBytes) {
        this.maxSizeBytes = maxSizeBytes;
    }

    public Duration getSignedTtl() {
        return signedTtl;
    }

    public void setSignedTtl(Duration signedTtl) {
        this.signedTtl = signedTtl;
    }

    public Set<String> getAllowedMime() {
        return allowedMime;
    }

    public void setAllowedMime(Set<String> allowedMime) {
        this.allowedMime = allowedMime;
    }

    public boolean isDemoContext() {
        return demoContext;
    }

    public void setDemoContext(boolean demoContext) {
        this.demoContext = demoContext;
    }

    public S3 getS3() {
        return s3;
    }

    public void setS3(S3 s3) {
        this.s3 = s3;
    }

    /** Endpoint and bucket only. Access/secret keys are retrieved via SecretPort. */
    public static class S3 {
        private String endpoint = "";
        private String bucket = "nexus-files";

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }
    }
}
