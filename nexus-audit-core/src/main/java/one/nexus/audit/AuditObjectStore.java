package one.nexus.audit;

import java.util.List;

/**
 * Narrow object-store sink used by the G4 cold archive. Product wires
 * {@code ObjectStoragePort} / MinIO Fake ({@code CountingObjectStorage});
 * tests may use an in-memory map. Keys are tenant or platform prefixes.
 */
public interface AuditObjectStore {
    void put(String objectKey, byte[] body);

    byte[] get(String objectKey);

    boolean exists(String objectKey);

    List<String> list(String prefix);
}
