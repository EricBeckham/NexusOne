package one.nexus.file;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileObjectRepository {
    void insert(FileObject file);

    Optional<FileObject> find(UUID fileId);

    void update(FileObject file);

    List<FileObject> listByTenant(UUID tenantId);

    List<FileObject> listAll();
}
