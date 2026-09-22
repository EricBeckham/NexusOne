package one.nexus.p3.path;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.p3.application.ApplicationService;
import one.nexus.p3.application.LedgerDefinitionService;
import one.nexus.p3.application.LedgerDefinitionService.FieldDraft;
import one.nexus.p3.application.LedgerDefinitionService.LedgerBundle;
import one.nexus.p3.domain.Application;
import one.nexus.p3.domain.ApplicationKind;
import one.nexus.p3.domain.LedgerScope;
import one.nexus.p3.path.BusinessTypeCatalog.FieldSpec;
import one.nexus.p3.path.BusinessTypeCatalog.TypeSpec;
import one.nexus.p3.studio.form.FormBinding;
import one.nexus.p3.studio.form.FormRecord;
import one.nexus.p3.studio.form.FormService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provisions one catalog type onto the existing M0-06/07 stack
 * (application + ledger + form). Does not add a second approval engine.
 */
@Service
public class BusinessTypeProvisioner {
    private final ApplicationService applications;
    private final LedgerDefinitionService ledgers;
    private final FormService forms;

    public BusinessTypeProvisioner(
            ApplicationService applications,
            LedgerDefinitionService ledgers,
            FormService forms) {
        this.applications = applications;
        this.ledgers = ledgers;
        this.forms = forms;
    }

    @Transactional
    public Provisioned provision(String typeCode, String suffix, boolean publish) {
        TypeSpec type = BusinessTypeCatalog.require(typeCode);
        String token = sanitize(suffix);
        Application application = applications.create(
                unique(type.appCode(), token),
                type.appName(),
                ApplicationKind.CUSTOM);
        List<FieldDraft> drafts = new ArrayList<>();
        for (FieldSpec field : type.fields()) {
            drafts.add(new FieldDraft(field.fieldCode(), field.displayName(), field.fieldType(), field.required(), false));
        }
        FieldSpec status = type.approvalStatus();
        drafts.add(new FieldDraft(status.fieldCode(), status.displayName(), status.fieldType(), false, false));
        LedgerBundle ledger = ledgers.create(
                application.id(),
                unique(type.ledgerCode(), token),
                type.ledgerName(),
                LedgerScope.TENANT,
                drafts);
        List<FormBinding> bindings = new ArrayList<>();
        int ordinal = 0;
        for (FieldSpec field : type.fields()) {
            bindings.add(new FormBinding(
                    field.fieldCode(),
                    ledger.ledger().ledgerCode(),
                    field.fieldCode(),
                    field.required(),
                    false,
                    field.widgetType(),
                    ordinal++));
        }
        FormRecord form = forms.create(
                application.appCode(),
                unique(type.formCode(), token),
                type.formName(),
                type.description(),
                bindings);
        if (publish) {
            forms.validate(form.formId());
            form = forms.publish(form.formId(), "g7 " + type.code());
        }
        return new Provisioned(type, application, ledger, form);
    }

    public Map<String, Object> toJson(Provisioned provisioned) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("type_code", provisioned.type().code());
        json.put("display_name", provisioned.type().displayName());
        json.put("class_name", provisioned.type().className());
        json.put("application_id", provisioned.application().id().toString());
        json.put("app_code", provisioned.application().appCode());
        json.put("ledger_id", provisioned.ledger().ledger().id().toString());
        json.put("ledger_code", provisioned.ledger().ledger().ledgerCode());
        json.put("physical_table", provisioned.ledger().ledger().physicalTable());
        json.put("form_id", provisioned.form().formId().toString());
        json.put("form_code", provisioned.form().formCode());
        json.put("draft_status", provisioned.form().draftStatus().name());
        json.put("running_version", provisioned.form().runningVersion());
        json.put("sample_values", provisioned.type().sampleValues());
        return json;
    }

    public static Map<String, Object> typeJson(TypeSpec type) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("code", type.code());
        json.put("display_name", type.displayName());
        json.put("class_name", type.className());
        json.put("description", type.description());
        json.put("ledger_code", type.ledgerCode());
        json.put("form_code", type.formCode());
        json.put("fields", type.fields().stream().map(field -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("field_code", field.fieldCode());
            row.put("display_name", field.displayName());
            row.put("field_type", field.fieldType().name());
            row.put("required", field.required());
            row.put("widget_type", field.widgetType().name());
            return row;
        }).toList());
        json.put("sample_values", type.sampleValues());
        return json;
    }

    private static String sanitize(String suffix) {
        if (suffix == null || suffix.isBlank()) {
            return Long.toString(System.nanoTime(), 36);
        }
        return suffix.trim().toLowerCase().replaceAll("[^a-z0-9_]", "");
    }

    private static String unique(String base, String token) {
        String candidate = base + token;
        if (candidate.length() <= 24) {
            return candidate;
        }
        String trimmed = token.length() > 8 ? token.substring(token.length() - 8) : token;
        candidate = base + trimmed;
        return candidate.length() <= 24 ? candidate : candidate.substring(0, 24);
    }

    public record Provisioned(
            TypeSpec type,
            Application application,
            LedgerBundle ledger,
            FormRecord form) {
        public UUID formId() {
            return form.formId();
        }

        public UUID ledgerId() {
            return ledger.ledger().id();
        }

        public String physicalTable() {
            return ledger.ledger().physicalTable();
        }
    }
}
