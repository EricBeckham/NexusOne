package one.nexus.p3.studio.form;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import one.nexus.p3.studio.meta.InMemoryLedgerMetadataCatalog;
import org.junit.jupiter.api.Test;

class BindingValidationTest {
    @Test
    void rejectsPhysicalColumnKeys() {
        NexusException ex = assertThrows(
                NexusException.class,
                () -> BindingValidator.parse(List.of(Map.of(
                        "widget_code", "title",
                        "ledger_code", "leave_request",
                        "field_code", "title",
                        "column_name", "c_title"))));
        assertEquals(ErrorCode.FORM_PHYSICAL_BINDING, ex.errorCode());
    }

    @Test
    void rejectsPhysicalTableNameAsLedgerCode() {
        NexusException ex = assertThrows(
                NexusException.class,
                () -> BindingValidator.parse(List.of(Map.of(
                        "widget_code", "title",
                        "ledger_code", "l_acme_leave",
                        "field_code", "title"))));
        assertEquals(ErrorCode.FORM_PHYSICAL_BINDING, ex.errorCode());
    }

    @Test
    void unknownLedgerAndFieldFailValidate() {
        InMemoryLedgerMetadataCatalog catalog = new InMemoryLedgerMetadataCatalog();
        catalog.seedDemoLeaveRequest();
        UUID tenant = UUID.fromString("11111111-1111-4111-8111-111111111111");
        List<FormBinding> unknownLedger = BindingValidator.parse(List.of(Map.of(
                "widget_code", "x",
                "ledger_code", "nope",
                "field_code", "title")));
        NexusException ledger = assertThrows(
                NexusException.class,
                () -> BindingValidator.validateAgainstMetadata(tenant, unknownLedger, catalog));
        assertEquals(ErrorCode.FORM_VALIDATE_FAILED, ledger.errorCode());

        List<FormBinding> unknownField = BindingValidator.parse(List.of(Map.of(
                "widget_code", "x",
                "ledger_code", "leave_request",
                "field_code", "nope")));
        NexusException field = assertThrows(
                NexusException.class,
                () -> BindingValidator.validateAgainstMetadata(tenant, unknownField, catalog));
        assertEquals(ErrorCode.FORM_VALIDATE_FAILED, field.errorCode());
        assertTrue(String.valueOf(field.details().get("errors")).contains("unknown field_code")
                || field.details().toString().contains("unknown field_code"));
    }

    @Test
    void acceptsLogicalCodesFromCatalog() {
        InMemoryLedgerMetadataCatalog catalog = new InMemoryLedgerMetadataCatalog();
        catalog.seedDemoLeaveRequest();
        List<FormBinding> bindings = BindingValidator.parse(List.of(Map.of(
                "widget_code", "title",
                "ledger_code", "leave_request",
                "field_code", "title",
                "required", true,
                "widget_type", "TEXT",
                "ordinal", 0)));
        BindingValidator.validateAgainstMetadata(
                UUID.fromString("11111111-1111-4111-8111-111111111111"), bindings, catalog);
        assertEquals("leave_request", bindings.get(0).ledgerCode());
        assertEquals("title", bindings.get(0).fieldCode());
    }
}
