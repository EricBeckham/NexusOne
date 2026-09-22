package one.nexus.file;

import java.time.Clock;
import one.nexus.crypto.SecretPort;
import one.nexus.file.infra.CountingObjectStorage;
import one.nexus.file.infra.InMemoryFileObjectRepository;
import one.nexus.file.infra.JdbcFileObjectRepository;
import one.nexus.file.infra.MinioObjectStorage;
import one.nexus.file.infra.OwnerOnlyAccessPolicy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration
@EnableConfigurationProperties(FileProperties.class)
public class FileConfiguration {
    @Bean
    @ConditionalOnMissingBean
    ObjectStoragePort objectStoragePort(FileProperties properties, ObjectProvider<SecretPort> secrets) {
        if ("s3".equalsIgnoreCase(properties.getStorage())
                && properties.getS3().getEndpoint() != null
                && !properties.getS3().getEndpoint().isBlank()) {
            SecretPort port = secrets.getIfAvailable();
            if (port == null) {
                throw new IllegalStateException(
                        "S3 storage retrieves credentials only via SecretPort (local+env; no OpenBao/KMS)");
            }
            return MinioObjectStorage.fromSecretPort(properties.getS3().getEndpoint(), port);
        }
        return new CountingObjectStorage();
    }

    @Bean
    @ConditionalOnMissingBean
    FileObjectRepository fileObjectRepository(FileProperties properties, ObjectProvider<JdbcClient> jdbc) {
        if ("jdbc".equalsIgnoreCase(properties.getPersistence()) && jdbc.getIfAvailable() != null) {
            return new JdbcFileObjectRepository(jdbc.getObject());
        }
        return new InMemoryFileObjectRepository();
    }

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock fileClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    FileAccessPolicy fileAccessPolicy() {
        return new OwnerOnlyAccessPolicy();
    }

    @Bean
    FileService fileService(
            ObjectStoragePort storage,
            FileObjectRepository repository,
            FileAccessPolicy policy,
            Clock cacheClock,
            FileProperties properties) {
        return new FileService(
                storage,
                repository,
                policy,
                cacheClock,
                bucket(properties),
                properties.getMaxSizeBytes(),
                properties.getSignedTtl(),
                properties.getAllowedMime());
    }

    @Bean
    @ConditionalOnMissingBean
    FileObjectReconciler fileObjectReconciler(
            ObjectStoragePort storage,
            FileObjectRepository repository,
            FileProperties properties) {
        return new FileObjectReconciler(storage, repository, bucket(properties));
    }

    @Bean
    @ConditionalOnMissingBean
    FileReconcileJob fileReconcileJob(FileObjectReconciler reconciler) {
        return new FileReconcileJob(reconciler);
    }

    private static String bucket(FileProperties properties) {
        return "s3".equalsIgnoreCase(properties.getStorage())
                ? properties.getS3().getBucket()
                : properties.getBucketAlias();
    }
}
