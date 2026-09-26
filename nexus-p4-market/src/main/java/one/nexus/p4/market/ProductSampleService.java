package one.nexus.p4.market;

import java.util.List;
import java.util.UUID;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p4.market.MarketModels.FormTemplateVersionSnapshot;
import one.nexus.p4.market.MarketModels.ProductDefinition;
import one.nexus.p4.market.ProductSampleModels.LedgerSample;
import one.nexus.p4.market.ProductSampleModels.SampleColumn;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** PC9+: read-only samples anchored on one product definition; platform SYSTEM_ADMIN like the definition reads. */
@Service
@ConditionalOnBean(IsolationGuard.class)
public class ProductSampleService {
    private final MarketRepository market;
    private final IsolationGuard isolation;

    public ProductSampleService(MarketRepository market, IsolationGuard isolation) {
        this.market = market;
        this.isolation = isolation;
    }

    @Transactional(readOnly = true)
    public LedgerSample ledger(UUID productDefinitionId) {
        isolation.requirePlatformContext("product-sample.ledger", PlatformDuty.SYSTEM_ADMIN);
        ProductDefinition definition = require(productDefinitionId);
        return market.formTemplateVersionSnapshot(definition.formTemplateVersionId())
                .map(form -> new LedgerSample(definition.id(), definition.name(), LedgerSample.SOURCE, form.versionId(),
                        form.templateName(), form.version(), columns(form), List.of(), LedgerSample.NOTE))
                .orElseGet(() -> new LedgerSample(definition.id(), definition.name(), LedgerSample.SOURCE,
                        definition.formTemplateVersionId(), null, null, List.of(), List.of(), LedgerSample.MISSING_NOTE));
    }

    static List<SampleColumn> columns(FormTemplateVersionSnapshot form) {
        return form.fields().stream().map(f -> new SampleColumn(f.key(), f.label(), f.type(), f.required())).toList();
    }

    ProductDefinition require(UUID id) {
        return market.productDefinition(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "product definition not found"));
    }
}