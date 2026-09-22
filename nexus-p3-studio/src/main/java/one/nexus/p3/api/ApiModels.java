package one.nexus.p3.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import one.nexus.p3.application.LedgerDefinitionService.LedgerBundle;
import one.nexus.p3.domain.Application;
import one.nexus.p3.domain.LedgerDefinition;
import one.nexus.p3.domain.LedgerField;

public final class ApiModels {
    private ApiModels() {}

    public record CreateApplicationRequest(
            @NotBlank String appCode,
            @NotBlank String displayName,
            String kind
    ) {}

    public record CreateLedgerRequest(
            @NotBlank String ledgerCode,
            @NotBlank String displayName,
            String scope,
            List<FieldRequest> fields
    ) {}

    public record FieldRequest(
            @NotBlank String fieldCode,
            @NotBlank String displayName,
            String fieldType,
            boolean required,
            boolean indexed
    ) {}

    public record ApplicationView(
            UUID id,
            UUID tenantId,
            String tenantCode,
            String appCode,
            String displayName,
            String kind,
            String status,
            long version
    ) {
        public static ApplicationView of(Application application) {
            return new ApplicationView(
                    application.id(),
                    application.tenantId(),
                    application.tenantCode(),
                    application.appCode(),
                    application.displayName(),
                    application.kind().name(),
                    application.status().name(),
                    application.version());
        }
    }

    public record FieldView(
            UUID id,
            String fieldCode,
            String displayName,
            String fieldType,
            boolean required,
            boolean indexed,
            int sortOrder
    ) {
        public static FieldView of(LedgerField field) {
            return new FieldView(
                    field.id(),
                    field.fieldCode(),
                    field.displayName(),
                    field.fieldType().name(),
                    field.required(),
                    field.indexed(),
                    field.sortOrder());
        }
    }

    public record LedgerView(
            UUID id,
            UUID tenantId,
            String tenantCode,
            UUID applicationId,
            String ledgerCode,
            String displayName,
            String scope,
            String physicalTable,
            int schemaVersion,
            String status,
            List<FieldView> fields,
            long version
    ) {
        public static LedgerView of(LedgerBundle bundle) {
            LedgerDefinition ledger = bundle.ledger();
            return new LedgerView(
                    ledger.id(),
                    ledger.tenantId(),
                    ledger.tenantCode(),
                    ledger.applicationId(),
                    ledger.ledgerCode(),
                    ledger.displayName(),
                    ledger.scope().name(),
                    ledger.physicalTable(),
                    ledger.schemaVersion(),
                    ledger.status().name(),
                    bundle.fields().stream().map(FieldView::of).toList(),
                    ledger.version());
        }
    }

    public record PageView(List<Map<String, Object>> items, long total, int page, int size) {}

    public record UpdateRecordRequest(Map<String, Object> values, Long version) {}

    public record ErrorBody(String code, String message, String traceId, Map<String, Object> details) {}
}
