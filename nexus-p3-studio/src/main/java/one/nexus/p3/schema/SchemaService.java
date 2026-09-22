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

    /**
     * Form-publish gate: the bound ledger must already have a SchemaService-owned
     * physical table. Does not {@code CREATE}/{@code ALTER} — ledger create/evolve
     * remain the only DDL paths.
     */
    void confirmPhysicalTable(LedgerDefinition ledger, List<LedgerField> fields);

    /**
     * Form-publish alignment inside the publish transaction.
     * {@code CREATE} when the physical table is missing; {@code EVOLVE} each
     * declared field that is not a physical column. The no-drift path confirms
     * the existing table. Default is confirm-only so memory/HTTP doubles stay
     * on the existing abort shape.
     */
    default void ensurePhysicalTable(LedgerDefinition ledger, List<LedgerField> fields) {
        confirmPhysicalTable(ledger, fields);
    }
}
