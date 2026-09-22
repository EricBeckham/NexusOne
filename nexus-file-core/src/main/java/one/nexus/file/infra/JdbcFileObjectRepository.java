package one.nexus.file.infra;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import one.nexus.file.FileObject;
import one.nexus.file.FileObjectRepository;
import one.nexus.file.FileStatus;

public final class JdbcFileObjectRepository implements FileObjectRepository {
    private final JdbcClient jdbc;

    public JdbcFileObjectRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(FileObject file) {
        jdbc.sql("""
                        INSERT INTO files.file_object (
                          file_id, tenant_id, bucket_alias, object_key, original_name, size_bytes,
                          content_type, checksum_sha256, status, owner_subject, resource_id,
                          created_at, created_by, version)
                        VALUES (
                          :file_id, :tenant_id, :bucket_alias, :object_key, :original_name, :size_bytes,
                          :content_type, :checksum_sha256, :status, :owner_subject, :resource_id,
                          :created_at, :created_by, 1)
                        """)
                .param("file_id", file.fileId())
                .param("tenant_id", file.tenantId())
                .param("bucket_alias", file.bucketAlias())
                .param("object_key", file.objectKey())
                .param("original_name", file.originalName())
                .param("size_bytes", file.sizeBytes())
                .param("content_type", file.contentType())
                .param("checksum_sha256", file.checksumSha256())
                .param("status", file.status().name())
                .param("owner_subject", file.ownerSubject())
                .param("resource_id", file.resourceId())
                .param("created_at", file.createdAt())
                .param("created_by", file.createdBy())
                .update();
    }

    @Override
    public Optional<FileObject> find(UUID fileId) {
        return jdbc.sql("""
                        SELECT file_id, tenant_id, bucket_alias, object_key, original_name, size_bytes,
                               content_type, checksum_sha256, status, owner_subject, resource_id,
                               created_at, created_by
                          FROM files.file_object
                         WHERE file_id = :file_id
                        """)
                .param("file_id", fileId)
                .query((rs, row) -> new FileObject(
                        rs.getObject("file_id", UUID.class),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getString("bucket_alias"),
                        rs.getString("object_key"),
                        rs.getString("original_name"),
                        rs.getLong("size_bytes"),
                        rs.getString("content_type"),
                        rs.getString("checksum_sha256"),
                        FileStatus.valueOf(rs.getString("status")),
                        rs.getString("owner_subject"),
                        rs.getString("resource_id"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getString("created_by")))
                .optional();
    }

    @Override
    public void update(FileObject file) {
        jdbc.sql("""
                        UPDATE files.file_object
                           SET status = :status, version = version + 1
                         WHERE file_id = :file_id
                        """)
                .param("status", file.status().name())
                .param("file_id", file.fileId())
                .update();
    }
}
