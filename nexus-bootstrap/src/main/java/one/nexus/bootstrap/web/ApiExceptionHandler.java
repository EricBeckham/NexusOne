package one.nexus.bootstrap.web;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import one.nexus.common.error.ApiError;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import one.nexus.common.trace.TraceIds;
import one.nexus.isolation.api.IsolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NexusException.class)
    public ResponseEntity<Map<String, Object>> handleNexus(NexusException ex) {
        return body(ex.httpStatus(), ex.errorCode(), ex.getMessage(), ex.details());
    }

    @ExceptionHandler(IsolationException.class)
    public ResponseEntity<Map<String, Object>> handleIsolation(IsolationException ex) {
        ApiError error = new ApiError(ex.code(), ex.getMessage(), ex.traceId(), Map.of());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error.toJsonMap());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleMissing(NoResourceFoundException ex) {
        return body(404, ErrorCode.API_NOT_FOUND, "Resource not found", Map.of());
    }

    /**
     * Services throw {@link ResponseStatusException} for deliberate 4xx outcomes (e.g. catalog SKU
     * without a product definition). Keep that status and reason instead of the 500 catch-all below.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException ex) {
        int status = ex.getStatusCode().value();
        if (status >= 500) log.error("response status error", ex);
        String reason = ex.getReason();
        String message = reason == null || reason.isBlank()
                ? HttpStatus.valueOf(status).getReasonPhrase()
                : reason;
        return body(status, codeFor(status), message, Map.of());
    }

    private static ErrorCode codeFor(int status) {
        return switch (status) {
            case 401, 403 -> ErrorCode.API_FORBIDDEN;
            case 404 -> ErrorCode.API_NOT_FOUND;
            case 409 -> ErrorCode.API_CONFLICT;
            case 422 -> ErrorCode.API_UNPROCESSABLE;
            default -> status >= 500 ? ErrorCode.API_INTERNAL : ErrorCode.API_BAD_REQUEST;
        };
    }

    /**
     * Database constraint violations get an explicit, value-free message instead of the opaque 500:
     * SQLState plus schema/table/column/constraint names only. The driver message and DETAIL line
     * (which contain row values) are never sent. Unique violations are a caller conflict (409);
     * the rest are server-side defects (500) such as a platform audit row missing tenant_id (I9).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegrity(DataIntegrityViolationException ex) {
        SQLException sql = sqlCause(ex);
        String state = sql == null ? null : sql.getSQLState();
        Map<String, String> names = constraintNames(sql);
        Map<String, Object> details = new LinkedHashMap<>();
        if (state != null) details.put("sql_state", state);
        details.putAll(names);
        if ("23505".equals(state)) {
            log.warn("unique constraint violated: {}", names);
            return body(409, ErrorCode.API_CONFLICT,
                    "unique constraint violated" + suffix(names.get("constraint")), details);
        }
        log.error("data integrity violation", ex);
        String message = switch (state == null ? "" : state) {
            case "23502" -> "not-null constraint violated" + suffix(qualified(names));
            case "23503" -> "foreign key constraint violated" + suffix(names.get("constraint"));
            case "23514" -> "check constraint violated" + suffix(names.get("constraint"));
            default -> "data integrity violation" + suffix(state == null ? null : "SQLState " + state);
        };
        return body(500, ErrorCode.API_DATA_INTEGRITY, message, details);
    }

    private static SQLException sqlCause(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof SQLException sql) return sql;
        }
        return null;
    }

    /** Reads PostgreSQL's structured error fields reflectively; the driver is runtime-scoped. */
    private static Map<String, String> constraintNames(SQLException sql) {
        Map<String, String> names = new LinkedHashMap<>();
        if (sql == null) return names;
        try {
            Object server = sql.getClass().getMethod("getServerErrorMessage").invoke(sql);
            if (server == null) return names;
            for (String field : new String[] {"schema", "table", "column", "constraint"}) {
                Object value = server.getClass()
                        .getMethod("get" + Character.toUpperCase(field.charAt(0)) + field.substring(1))
                        .invoke(server);
                if (value instanceof String text && !text.isBlank()) names.put(field, text);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Not a PostgreSQL error or an older driver: fall back to the SQLState alone.
        }
        return names;
    }

    private static String qualified(Map<String, String> names) {
        String column = names.get("column");
        String table = names.get("table");
        if (table == null) return column;
        String schema = names.get("schema");
        String qualifiedTable = schema == null ? table : schema + "." + table;
        return column == null ? qualifiedTable : qualifiedTable + "." + column;
    }

    private static String suffix(String detail) {
        return detail == null || detail.isBlank() ? "" : ": " + detail;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnknown(Exception ex) {
        log.error("unhandled error", ex);
        return body(500, ErrorCode.API_INTERNAL, "Internal error", Map.of());
    }

    private static ResponseEntity<Map<String, Object>> body(
            int status, ErrorCode code, String message, Map<String, Object> details) {
        ApiError error = ApiError.of(code, message, TraceIds.currentOrUnknown(), details);
        return ResponseEntity.status(HttpStatus.valueOf(status)).body(error.toJsonMap());
    }
}
