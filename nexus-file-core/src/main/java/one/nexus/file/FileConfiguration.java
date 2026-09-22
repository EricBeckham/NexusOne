package one.nexus.file;

import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import one.nexus.file.infra.CountingObjectStorage;
import one.nexus.file.infra.InMemoryFileObjectRepository;
import one.nexus.file.infra.JdbcFileObjectRepository;
import one.nexus.file.infra.MinioObjectStorage;
import one.nexus.file.infra.OwnerOnlyAccessPolicy;

@Configuration
@EnableConfigurationProperties(FileProperties.class)
public class FileConfiguration {
    @Bean
    @ConditionalOnMissingBean
    ObjectStoragePort objectStoragePort(FileProperties properties) {
        if ("s3".equalsIgnoreCase(properties.getStorage())
                && properties.getS3().getEndpoint() != null
                && !properties.getS3().getEndpoint().isBlank()) {
            return new MinioObjectStorage(
                    properties.getS3().getEndpoint(),
                    properties.getS3().getAccessKey(),
                    properties.getS3().getSecretKey());
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
        String bucket = "s3".equalsIgnoreCase(properties.getStorage())
                ? properties.getS3().getBucket()
                : properties.getBucketAlias();
        return new FileService(
                storage,
                repository,
                policy,
                cacheClock,
                bucket,
                properties.getMaxSizeBytes(),
                properties.getSignedTtl(),
                properties.getAllowedMime());
    }
}
