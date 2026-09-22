package one.nexus.p3.studio.meta;

/**
 * M0-06 metadata contract consumed by M0-07 form bind/validate.
 *
 * <p>M0-06 is the source of truth for application / ledger / field codes and for
 * SchemaService physical tables. This port exposes <strong>logical codes only</strong>.
 * Form bindings must never receive physical table or column names.
 *
 * <p>When M0-06 {@link one.nexus.p3.port.LedgerRepository} / {@link one.nexus.p3.port.FieldRepository}
 * beans are present, {@link JdbcLedgerMetadataPort} is the default. Otherwise
 * {@link InMemoryLedgerMetadataCatalog} is the {@code @ConditionalOnMissingBean} test double.
 * See {@code docs/m0-07-form-publish.md}.
 */
public interface LedgerMetadataPort {
    java.util.Optional<LedgerRef> findLedger(java.util.UUID tenantId, String ledgerCode);

    java.util.Optional<FieldRef> findField(java.util.UUID tenantId, String ledgerCode, String fieldCode);

    default boolean ledgerExists(java.util.UUID tenantId, String ledgerCode) {
        return findLedger(tenantId, ledgerCode).isPresent();
    }

    default boolean fieldExists(java.util.UUID tenantId, String ledgerCode, String fieldCode) {
        return findField(tenantId, ledgerCode, fieldCode).isPresent();
    }
}
