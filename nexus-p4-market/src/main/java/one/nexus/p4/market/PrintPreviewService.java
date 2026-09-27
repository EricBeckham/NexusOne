package one.nexus.p4.market;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.ClockPort;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p3.port.ProductApplicationStore;
import one.nexus.p3.port.ProductApplicationStore.ApplicationSnapshot;
import one.nexus.p4.market.MarketModels.FormFieldSnapshot;
import one.nexus.p4.market.MarketModels.ProductDefinition;
import one.nexus.p4.market.PrintMappingModels.FieldSlotMapping;
import one.nexus.p4.market.PrintMappingModels.PrintMappingContract;
import one.nexus.p4.market.PrintMappingModels.ProductPrintMapping;
import one.nexus.p4.market.PrintMappingModels.Slot;
import one.nexus.p4.market.PrintPreviewModels.PreviewField;
import one.nexus.p4.market.PrintPreviewModels.PreviewSection;
import one.nexus.p4.market.PrintPreviewModels.PrintPreview;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * DPO-L5: fill the saved print mapping with values. The platform preview has no application (all values null);
 * the tenant preview reads one application of the caller's own tenant only — another tenant's id is a 404.
 * No PDF (later), no payment fields.
 */
@Service
@ConditionalOnBean({IsolationGuard.class, ClockPort.class})
public class PrintPreviewService {
    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final Map<String, String> SECTION_LABELS = Map.of("header", "单据信息", "form", "申请内容", "approval", "审批");
    private static final List<FormFieldSnapshot> HEADER_FIELDS = List.of(
            new FormFieldSnapshot("application_no", "单号", "TEXT", false),
            new FormFieldSnapshot("applicant", "申请人", "PERSON", false),
            new FormFieldSnapshot("submitted_at", "提交时间", "DATE", false));
    private static final List<FormFieldSnapshot> APPROVAL_FIELDS = List.of(
            new FormFieldSnapshot("opinion", "审批意见", "TEXTAREA", false),
            new FormFieldSnapshot("approver_sign", "审批人签字", "PERSON", false),
            new FormFieldSnapshot("approved_at", "日期", "DATE", false));

    private final IsolationGuard isolation;
    private final MarketRepository market;
    private final PrintMappingService printMapping;
    private final PrintMappingStore mappings;
    private final ObjectProvider<ProductApplicationStore> applications;

    public PrintPreviewService(IsolationGuard isolation, MarketRepository market, PrintMappingService printMapping,
            PrintMappingStore mappings, ObjectProvider<ProductApplicationStore> applications) {
        this.isolation = isolation;
        this.market = market;
        this.printMapping = printMapping;
        this.mappings = mappings;
        this.applications = applications;
    }

    /** Platform: layout of the product with its saved mapping and no data. */
    @Transactional(readOnly = true)
    public PrintPreview forProduct(UUID productDefinitionId) {
        isolation.requirePlatformContext("print-preview.product", PlatformDuty.SYSTEM_ADMIN);
        ProductDefinition definition = market.productDefinition(productDefinitionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "product definition not found"));
        List<FormFieldSnapshot> form = formFields(definition.formTemplateVersionId());
        return build(definition, form, null, null);
    }

    /** Tenant: one application of the caller's own tenant. */
    @Transactional(readOnly = true)
    public PrintPreview forApplication(UUID applicationId) {
        UUID tenantId = currentTenant();
        ProductApplicationStore store = applications.getIfAvailable();
        if (store == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "applications are not available");
        ApplicationSnapshot application = store.findForTenant(tenantId, applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "application not found"));
        UUID productId = application.productDefinitionId();
        if (productId == null && application.installationId() != null) {
            productId = market.installationProductPin(application.installationId())
                    .map(MarketModels.InstallationProductPin::productDefinitionId).orElse(null);
        }
        ProductDefinition definition = productId == null ? null : market.productDefinition(productId).orElse(null);
        if (definition == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "product of the application not found");
        // The application was filled against its own pinned form version, which may differ from today's definition.
        return build(definition, formFields(application.formTemplateVersionId()), application, values(application.valuesJson()));
    }

    private PrintPreview build(ProductDefinition definition, List<FormFieldSnapshot> form, ApplicationSnapshot application,
            Map<String, JsonNode> values) {
        PrintMappingContract contract = printMapping.loadedContract();
        ProductPrintMapping mapping = mappings.find(definition.id()).orElse(null);
        List<FieldSlotMapping> pairs = mapping == null ? contract.defaultFieldMappings() : mapping.mappings();
        Map<String, String> slotByField = new LinkedHashMap<>();
        pairs.forEach(pair -> slotByField.put(pair.fieldKey(), pair.slotId()));
        Map<String, String> sectionBySlot = new LinkedHashMap<>();
        for (Slot slot : contract.builtinTemplate().slots()) sectionBySlot.put(slot.id(), slot.section());
        String spill = contract.mappingRules().unmappedSpillSlotId();

        List<FormFieldSnapshot> all = new ArrayList<>(HEADER_FIELDS);
        all.addAll(form);
        if (definition.processDefinitionKey() != null) all.addAll(APPROVAL_FIELDS);

        Map<String, List<PreviewField>> bySection = new LinkedHashMap<>();
        for (String key : List.of("header", "form", "approval")) bySection.put(key, new ArrayList<>());
        for (FormFieldSnapshot field : all) {
            String slot = slotByField.getOrDefault(field.key(), spill);
            String section = sectionBySlot.getOrDefault(slot, "form");
            String[] value = application == null ? new String[] {null, null} : value(field, application, values);
            bySection.computeIfAbsent(section, s -> new ArrayList<>())
                    .add(new PreviewField(field.key(), field.label(), field.type(), slot, value[0], value[1]));
        }
        List<PreviewSection> sections = new ArrayList<>();
        bySection.forEach((key, fields) -> {
            if (!fields.isEmpty() || !"approval".equals(key)) {
                sections.add(new PreviewSection(key, SECTION_LABELS.getOrDefault(key, key), fields));
            }
        });
        String paper = mapping == null ? contract.builtinTemplate().defaultPaper() : mapping.paper();
        String orientation = mapping == null ? contract.builtinTemplate().defaultOrientation() : mapping.orientation();
        return new PrintPreview(definition.id(), application == null ? null : application.id(),
                application == null ? PrintPreviewModels.SOURCE_NONE : PrintPreviewModels.SOURCE_APPLICATION,
                contract.builtinTemplate().code(), paper, orientation, definition.name() + " 申请单",
                mapping == null ? ProductPrintMapping.SOURCE_DEFAULT : mapping.source(),
                mapping == null ? 0 : mapping.revision(), sections,
                application == null ? PrintPreviewModels.NOTE_NONE : PrintPreviewModels.NOTE_APPLICATION);
    }

    /** {value, note}: exactly one of them is null. */
    private static String[] value(FormFieldSnapshot field, ApplicationSnapshot application, Map<String, JsonNode> values) {
        switch (field.key()) {
            case "application_no":
                return new String[] {application.id().toString(), null};
            case "submitted_at":
                return new String[] {application.createdAt().toString(), null};
            case "applicant":
                return new String[] {null, "申请人姓名暂不解析：申请单只记录人员 id。"};
            case "opinion", "approver_sign", "approved_at":
                return new String[] {null, "审批意见和签字不在本预览中。"};
            default:
                JsonNode node = values.get(field.key());
                if (node == null || node.isNull()) return new String[] {null, "申请单里没有填写这个字段。"};
                return new String[] {node.isValueNode() ? node.asText() : node.toString(), null};
        }
    }

    private List<FormFieldSnapshot> formFields(UUID formTemplateVersionId) {
        return formTemplateVersionId == null ? List.of()
                : market.formTemplateVersionSnapshot(formTemplateVersionId).map(MarketModels.FormTemplateVersionSnapshot::fields)
                        .orElse(List.of());
    }

    private static Map<String, JsonNode> values(String json) {
        Map<String, JsonNode> out = new LinkedHashMap<>();
        if (json == null || json.isBlank()) return out;
        JsonNode root = JSON.readTree(json);
        if (root != null && root.isObject()) root.properties().forEach(entry -> out.put(entry.getKey(), entry.getValue()));
        return out;
    }

    private static UUID currentTenant() {
        AccessContext context = AccessContextHolder.currentOrNull();
        if (!(context instanceof TenantContext tenant)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "tenant context required for an application preview");
        }
        return UUID.fromString(tenant.tenantId());
    }
}
