package one.nexus.p3.schema;

import java.util.List;
import one.nexus.p3.domain.LedgerDefinition;
import one.nexus.p3.domain.LedgerField;
import one.nexus.p3.domain.SchemaAst;

/**
 * Sole owner of dynamic ledger DDL. Business code must not issue CREATE/ALTER
 * through JDBC, and Flyway must not manage {@code business_data.l_*} / {@code a_*}.
 */
public interface SchemaService {
    LedgerDefinition createPhysicalTable(LedgerDefinition ledger, List<LedgerField> fields);

    LedgerDefinition evolvePhysicalTable(LedgerDefinition ledger, List<LedgerField> currentFields, LedgerField added);

    SchemaAst astOf(LedgerDefinition ledger, List<LedgerField> fields);

    void registerRuntime(LedgerDefinition ledger, List<LedgerField> fields);
}
