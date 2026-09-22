package one.nexus.p3.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p3.domain.LedgerField;

public interface FieldRepository {
    void insert(LedgerField field);

    List<LedgerField> listByLedger(UUID tenantId, UUID ledgerId);

    Optional<LedgerField> findByCode(UUID tenantId, UUID ledgerId, String fieldCode);
}
