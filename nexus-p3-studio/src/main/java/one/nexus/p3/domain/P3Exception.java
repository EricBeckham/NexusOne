package one.nexus.p3.domain;

import java.util.Map;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;

public final class P3Exception extends NexusException {
    public P3Exception(ErrorCode errorCode, int httpStatus, String message, Map<String, Object> details) {
        super(errorCode, httpStatus, message, details);
    }

    public static P3Exception invalidIdentifier(String value) {
        return new P3Exception(
                P3ErrorCodes.INVALID_IDENTIFIER, 400, "illegal identifier", Map.of("value", String.valueOf(value)));
    }

    public static P3Exception reservedField(String value) {
        return new P3Exception(
                P3ErrorCodes.RESERVED_FIELD, 400, "reserved system field name", Map.of("value", value));
    }

    public static P3Exception illegalEvolution(String message, Map<String, Object> details) {
        return new P3Exception(P3ErrorCodes.ILLEGAL_EVOLUTION, 400, message, details);
    }

    public static P3Exception tableConflict(String table) {
        return new P3Exception(
                P3ErrorCodes.TABLE_CONFLICT, 409, "physical table already exists", Map.of("table", table));
    }

    public static P3Exception appNotFound(Object id) {
        return new P3Exception(P3ErrorCodes.APP_NOT_FOUND, 404, "application not found", Map.of("id", String.valueOf(id)));
    }

    public static P3Exception appConflict(String code) {
        return new P3Exception(P3ErrorCodes.APP_CONFLICT, 409, "application code already exists", Map.of("app_code", code));
    }

    public static P3Exception ledgerNotFound(Object id) {
        return new P3Exception(P3ErrorCodes.LEDGER_NOT_FOUND, 404, "ledger not found", Map.of("id", String.valueOf(id)));
    }

    public static P3Exception ledgerConflict(String code) {
        return new P3Exception(P3ErrorCodes.LEDGER_CONFLICT, 409, "ledger code already exists", Map.of("ledger_code", code));
    }

    public static P3Exception ledgerNotReady(Object id) {
        return new P3Exception(
                P3ErrorCodes.LEDGER_NOT_READY, 409, "ledger physical table is not ready", Map.of("id", String.valueOf(id)));
    }

    public static P3Exception recordNotFound(Object id) {
        return new P3Exception(P3ErrorCodes.RECORD_NOT_FOUND, 404, "record not found", Map.of("id", String.valueOf(id)));
    }

    public static P3Exception recordConflict(String message) {
        return new P3Exception(P3ErrorCodes.RECORD_CONFLICT, 409, message, Map.of());
    }

    public static P3Exception queryInvalid(String message) {
        return new P3Exception(P3ErrorCodes.QUERY_INVALID, 400, message, Map.of());
    }

    public static P3Exception missingTenant() {
        return new P3Exception(P3ErrorCodes.MISSING_TENANT, 403, "tenant context is required", Map.of());
    }
}
