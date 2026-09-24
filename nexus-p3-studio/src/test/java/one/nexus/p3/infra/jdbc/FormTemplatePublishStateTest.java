package one.nexus.p3.infra.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class FormTemplatePublishStateTest {
    @Test
    void publishRequiresValidatedStatus() {
        FormTemplateDraftService.requireValidated("VALIDATED");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> FormTemplateDraftService.requireValidated("DRAFT"));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertThrows(ResponseStatusException.class, () -> FormTemplateDraftService.requireValidated("PUBLISHED"));
    }
}
