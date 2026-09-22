package one.nexus.p3.studio.meta;

import java.util.Optional;
import java.util.UUID;
import one.nexus.p3.domain.Application;
import one.nexus.p3.port.ApplicationRepository;
import one.nexus.p3.port.FieldRepository;
import one.nexus.p3.port.LedgerRepository;

/**
 * M0-07 binding catalog backed by merged M0-06 {@link LedgerRepository} /
 * {@link FieldRepository}. Returns logical codes only — no physical table or column names.
 */
public final class JdbcLedgerMetadataPort implements LedgerMetadataPort {
    private final LedgerRepository ledgers;
    private final FieldRepository fields;
    private final ApplicationRepository applications;

    public JdbcLedgerMetadataPort(
            LedgerRepository ledgers, FieldRepository fields, ApplicationRepository applications) {
        this.ledgers = ledgers;
        this.fields = fields;
        this.applications = applications;
    }

    @Override
    public Optional<LedgerRef> findLedger(UUID tenantId, String ledgerCode) {
        return ledgers.findByCode(tenantId, ledgerCode).map(def -> {
            String appCode = applications == null
                    ? ""
                    : applications.findById(tenantId, def.applicationId())
                            .map(Application::appCode)
                            .orElse("");
            return new LedgerRef(
                    def.tenantId(), appCode, def.ledgerCode(), def.displayName(), def.ready());
        });
    }

    @Override
    public Optional<FieldRef> findField(UUID tenantId, String ledgerCode, String fieldCode) {
        return ledgers.findByCode(tenantId, ledgerCode)
                .flatMap(def -> fields.findByCode(tenantId, def.id(), fieldCode)
                        .map(field -> new FieldRef(
                                def.ledgerCode(),
                                field.fieldCode(),
                                field.displayName(),
                                field.fieldType().name(),
                                field.required())));
    }
}
