package one.nexus.p3.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p3.domain.LedgerDefinition;

public interface LedgerRepository {
    void insert(LedgerDefinition ledger);

    void update(LedgerDefinition ledger);

    Optional<LedgerDefinition> findById(UUID tenantId, UUID id);

    Optional<LedgerDefinition> findByCode(UUID tenantId, String ledgerCode);

    Optional<LedgerDefinition> findByPhysicalTable(String physicalTable);

    List<LedgerDefinition> list(UUID tenantId);

    List<LedgerDefinition> listReady();
}
