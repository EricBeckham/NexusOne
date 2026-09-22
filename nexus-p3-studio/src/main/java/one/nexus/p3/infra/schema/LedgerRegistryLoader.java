package one.nexus.p3.infra.schema;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import one.nexus.p3.port.FieldRepository;
import one.nexus.p3.port.LedgerRepository;
import one.nexus.p3.schema.SchemaService;

/** Re-registers READY physical tables into the isolation-kernel TableRegistry after restart. */
@Component
@Order(40)
public class LedgerRegistryLoader implements ApplicationRunner {
    private final LedgerRepository ledgers;
    private final FieldRepository fields;
    private final SchemaService schemaService;

    public LedgerRegistryLoader(LedgerRepository ledgers, FieldRepository fields, SchemaService schemaService) {
        this.ledgers = ledgers;
        this.fields = fields;
        this.schemaService = schemaService;
    }

    @Override
    public void run(ApplicationArguments args) {
        ledgers.listReady().forEach(ledger ->
                schemaService.registerRuntime(ledger, fields.listByLedger(ledger.tenantId(), ledger.id())));
    }
}
