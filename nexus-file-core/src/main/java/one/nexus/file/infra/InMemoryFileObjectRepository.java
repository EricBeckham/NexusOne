package one.nexus.file.infra;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import one.nexus.file.FileObject;
import one.nexus.file.FileObjectRepository;

public final class InMemoryFileObjectRepository implements FileObjectRepository {
    private final Map<UUID, FileObject> files = new ConcurrentHashMap<>();

    @Override
    public void insert(FileObject file) {
        files.put(file.fileId(), file);
    }

    @Override
    public Optional<FileObject> find(UUID fileId) {
        return Optional.ofNullable(files.get(fileId));
    }

    @Override
    public void update(FileObject file) {
        files.put(file.fileId(), file);
    }
}
