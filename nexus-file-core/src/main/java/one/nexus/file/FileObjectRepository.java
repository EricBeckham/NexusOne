package one.nexus.file;

import java.util.Optional;
import java.util.UUID;

public interface FileObjectRepository {
    void insert(FileObject file);

    Optional<FileObject> find(UUID fileId);

    void update(FileObject file);
}
