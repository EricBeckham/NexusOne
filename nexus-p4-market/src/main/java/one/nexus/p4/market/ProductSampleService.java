package one.nexus.p4.market;

import java.util.List;
import java.util.UUID;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p4.market.MarketModels.FormTemplateVersionSnapshot;
import one.nexus.p4.market.MarketModels.ProductDefinition;
import one.nexus.p4.market.ProductSampleModels.LedgerSample;
import one.nexus.p4.market.ProductSampleModels.PrintField;
import one.nexus.p4.market.ProductSampleModels.PrintSample;
import one.nexus.p4.market.ProductSampleModels.PrintSection;
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

    /**
     * PC11: the built-in approval-form layout. Header rows are fields every product application has
     * (number, applicant, submitted at); the approval block appears only when the product binds a process.
     */
    @Transactional(readOnly = true)
    public PrintSample print(UUID productDefinitionId) {
        isolation.requirePlatformContext("product-sample.print", PlatformDuty.SYSTEM_ADMIN);
        ProductDefinition definition = require(productDefinitionId);
        List<PrintField> formFields = market.formTemplateVersionSnapshot(definition.formTemplateVersionId())
                .map(form -> form.fields().stream().map(f -> new PrintField(f.key(), f.label(), f.type())).toList())
                .orElse(List.of());
        List<PrintSection> sections = new java.util.ArrayList<>(List.of(
                new PrintSection("header", "单据信息", List.of(
                        new PrintField("application_no", "单号", "TEXT"),
                        new PrintField("applicant", "申请人", "PERSON"),
                        new PrintField("submitted_at", "提交时间", "DATE"))),
                new PrintSection("form", "申请内容", formFields)));
        if (definition.processDefinitionKey() != null) {
            sections.add(new PrintSection("approval", "审批", List.of(
                    new PrintField("opinion", "审批意见", "TEXTAREA"),
                    new PrintField("approver_sign", "审批人签字", "PERSON"),
                    new PrintField("approved_at", "日期", "DATE"))));
        }
        return new PrintSample(definition.id(), definition.name(), PrintSample.SOURCE, PrintSample.TEMPLATE_CODE,
                PrintSample.TEMPLATE_NAME, "A4", "PORTRAIT", definition.name() + " 申请单", sections, PrintSample.NOTE);
    }

    static List<SampleColumn> columns(FormTemplateVersionSnapshot form) {
        return form.fields().stream().map(f -> new SampleColumn(f.key(), f.label(), f.type(), f.required())).toList();
    }

    ProductDefinition require(UUID id) {
        return market.productDefinition(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "product definition not found"));
    }
}