package one.nexus.p2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class KeycloakAdminSupportTest {
    @Test
    void parsesBrowserExecutionIdsAndReplacesRequiredActions() {
        String executions = "["
                + "{\"id\":\"pwd-1\",\"providerId\":\"auth-username-password-form\",\"authenticationConfig\":\"cfg-pwd\"},"
                + "{\"id\":\"otp-1\",\"providerId\":\"auth-otp-form\"}"
                + "]";
        assertEquals(List.of("pwd-1"), KeycloakAdminSupport.executionIds(executions, "auth-username-password-form"));
        assertEquals(List.of("otp-1"), KeycloakAdminSupport.executionIds(executions, "auth-otp-form"));
        assertEquals("cfg-pwd", KeycloakAdminSupport.executionConfigId(executions, "pwd-1"));

        String user = "{\"id\":\"u1\",\"username\":\"tenant-admin\",\"requiredActions\":[]}";
        String updated = KeycloakAdminSupport.replaceRequiredActions(user, List.of("CONFIGURE_TOTP"));
        assertTrue(updated.contains("\"requiredActions\":[\"CONFIGURE_TOTP\"]"), updated);
        assertTrue(KeycloakAdminSupport.replaceJsonString(user, "lastName", "Admin")
                .contains("\"lastName\":\"Admin\""));
    }
}
