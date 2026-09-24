package one.nexus.p3.studio.form;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import one.nexus.p3.infra.jdbc.FormTemplateDraftService.FieldView;
import one.nexus.p3.infra.jdbc.FormTemplateDraftValidator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class FormTemplateDraftValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsLegalUniqueFieldsAndSelectOptions() throws Exception {
        var fields = List.of(
                field("subject", "TEXT", null),
                field("category", "SELECT", mapper.readTree("[{\"label\":\"A\",\"value\":\"a\"}]")));

        assertTrue(FormTemplateDraftValidator.validate(fields).isEmpty());
    }

    @Test
    void reportsEmptyDraftDuplicateAndIllegalKeysAndMissingSelectOptions() {
        assertEquals("fields.required", FormTemplateDraftValidator.validate(List.of()).getFirst().code());
        var errors = FormTemplateDraftValidator.validate(List.of(
                field("Bad key", "TEXT", null),
                field("same", "TEXT", null),
                field("same", "SELECT", null)));

        assertEquals(4, errors.size());
        assertTrue(errors.stream().anyMatch(error -> error.code().equals("field.key.invalid")));
        assertEquals(2, errors.stream().filter(error -> error.code().equals("field.key.duplicate")).count());
        assertTrue(errors.stream().anyMatch(error -> error.code().equals("field.options.required")));
    }

    private static FieldView field(String key, String type, tools.jackson.databind.JsonNode options) {
        return new FieldView(UUID.randomUUID(), key, key, type, false, options, 0);
    }
}
