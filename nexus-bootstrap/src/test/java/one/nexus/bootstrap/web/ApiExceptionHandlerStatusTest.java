package one.nexus.bootstrap.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** H6: deliberate ResponseStatusException outcomes keep their status and reason instead of 500 Internal error. */
class ApiExceptionHandlerStatusTest {
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void keepsStatusAndReasonForCatalogSkuWithoutDefinition() throws Exception {
        mvc.perform(get("/throw/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CORE.API.0002"))
                .andExpect(jsonPath("$.message").value("catalog SKU has no product definition"))
                .andExpect(jsonPath("$.trace_id").exists());
    }

    @Test
    void mapsConflictForbiddenAndUnprocessable() throws Exception {
        mvc.perform(get("/throw/409")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CORE.API.0003"));
        mvc.perform(get("/throw/403")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CORE.API.0004"));
        mvc.perform(get("/throw/422")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CORE.API.0005"));
    }

    @Test
    void fallsBackToTheReasonPhraseWhenNoReasonIsGiven() throws Exception {
        mvc.perform(get("/throw/bare"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CORE.API.0001"))
                .andExpect(jsonPath("$.message").value("Bad Request"));
    }

    @Test
    void notNullViolationNamesTheColumnWithoutRowValues() throws Exception {
        String body = mvc.perform(get("/throw/not-null"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("CORE.API.0006"))
                .andExpect(jsonPath("$.message").value("not-null constraint violated: platform.audit_event.tenant_id"))
                .andExpect(jsonPath("$.details.sql_state").value("23502"))
                .andExpect(jsonPath("$.details.column").value("tenant_id"))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("secret-row-value"), body);
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("Failing row"), body);
    }

    @Test
    void uniqueViolationIsAConflictNamingTheConstraint() throws Exception {
        mvc.perform(get("/throw/unique"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CORE.API.0003"))
                .andExpect(jsonPath("$.message").value("unique constraint violated: form_template_code_key"))
                .andExpect(jsonPath("$.details.constraint").value("form_template_code_key"));
    }

    @Test
    void integrityViolationWithoutDriverDetailsFallsBackToSqlState() throws Exception {
        mvc.perform(get("/throw/plain-integrity"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("CORE.API.0006"))
                .andExpect(jsonPath("$.message").value("data integrity violation"));
    }

    private static org.springframework.dao.DataIntegrityViolationException postgres(String fields) {
        var server = new org.postgresql.util.ServerErrorMessage(fields);
        return new org.springframework.dao.DataIntegrityViolationException("PreparedStatementCallback; SQL [...]",
                new org.postgresql.util.PSQLException(server));
    }

    @Test
    void unexpectedExceptionsStayOpaque500() throws Exception {
        mvc.perform(get("/throw/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal error"));
    }

    @RestController
    static class ThrowingController {
        @GetMapping("/throw/{kind}")
        String fail(@PathVariable String kind) throws Exception {
            throw switch (kind) {
                case "404" -> new ResponseStatusException(HttpStatus.NOT_FOUND, "catalog SKU has no product definition");
                case "409" -> new ResponseStatusException(HttpStatus.CONFLICT, "tenant already has an active subscription for this SKU");
                case "403" -> new ResponseStatusException(HttpStatus.FORBIDDEN, "tenant administrator role is required");
                case "422" -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "a PERSON field is required for the approver");
                case "bare" -> new ResponseStatusException(HttpStatus.BAD_REQUEST);
                case "not-null" -> postgres("SERROR\0C23502\0Mnull value in column \"tenant_id\" violates not-null constraint\0"
                        + "DFailing row contains (secret-row-value).\0splatform\0taudit_event\0ctenant_id\0");
                case "unique" -> postgres("SERROR\0C23505\0Mduplicate key value violates unique constraint\0"
                        + "DKey (code)=(secret-row-value) already exists.\0splatform\0tform_template\0nform_template_code_key\0");
                case "plain-integrity" -> new org.springframework.dao.DataIntegrityViolationException("no sql cause");
                default -> new IllegalArgumentException("secret internal detail");
            };
        }
    }
}
