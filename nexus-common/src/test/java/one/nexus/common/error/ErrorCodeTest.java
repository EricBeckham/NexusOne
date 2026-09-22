package one.nexus.common.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ErrorCodeTest {
    @Test
    void formatsProductModuleNumber() {
        assertEquals("CORE.API.0001", ErrorCode.API_BAD_REQUEST.code());
        assertEquals("CORE.FILE.0001", ErrorCode.FILE_NOT_FOUND.code());
        assertEquals("CORE.FILE.0005", ErrorCode.FILE_MISSING.code());
        assertEquals("CORE.NOTIFY.0002", ErrorCode.NOTIFY_CHANNEL_UNSUPPORTED.code());
        assertEquals("CORE.SECRET.0003", ErrorCode.SECRET_BACKEND_READ_ONLY.code());
        assertEquals("P3.FORM.0004", ErrorCode.FORM_PHYSICAL_BINDING.code());
        assertEquals("P1.TENANT.0001", ErrorCode.of("P1", "TENANT", 1).code());
        assertEquals(ErrorCode.API_NOT_FOUND, ErrorCode.parse("CORE.API.0002"));
    }

    @Test
    void rejectsBadTokens() {
        assertThrows(IllegalArgumentException.class, () -> ErrorCode.of("core", "API", 1));
        assertThrows(IllegalArgumentException.class, () -> ErrorCode.parse("CORE-API-1"));
        assertThrows(IllegalArgumentException.class, () -> ErrorCode.of("CORE", "API", 0));
    }

    @Test
    void apiErrorUsesSnakeCaseTraceField() {
        var error = ApiError.of(ErrorCode.API_NOT_FOUND, "missing", "trace-1", null);
        assertEquals("trace-1", error.toJsonMap().get("trace_id"));
        assertEquals("CORE.API.0002", error.toJsonMap().get("code"));
    }
}
