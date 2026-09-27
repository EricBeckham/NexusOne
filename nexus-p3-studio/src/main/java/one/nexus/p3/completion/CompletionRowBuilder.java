package one.nexus.p3.completion;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import one.nexus.p3.completion.CompletionAction.FieldMapping;
import one.nexus.p3.completion.CompletionAction.SourceKind;
import one.nexus.p3.completion.CompletionAction.SystemValue;
import one.nexus.p3.domain.FieldType;
import one.nexus.p3.domain.LedgerField;
import one.nexus.p3.domain.SystemColumns;

/**
 * FA3: validates a field mapping against the source / target ledgers and builds the row to insert.
 * Pure — no I/O — so the same rules serve config validation, preview and execution (FA4).
 */
public final class CompletionRowBuilder {
    private CompletionRowBuilder() {}

    /** Facts about one finished approval. */
    public record Completion(UUID sourceRecordId, String initiator, String lastApprover, Instant completedAt,
            String outcome) {}

    /** Returns problem descriptions (field code + reason); empty when the mapping is valid. */
    public static List<String> validate(List<FieldMapping> mapping, List<LedgerField> sourceFields,
            List<LedgerField> targetFields) {
        List<String> problems = new ArrayList<>();
        Map<String, LedgerField> source = byCode(sourceFields);
        Map<String, LedgerField> target = byCode(targetFields);
        Set<String> mapped = new java.util.HashSet<>();
        for (FieldMapping item : mapping == null ? List.<FieldMapping>of() : mapping) {
            String code = item == null ? null : item.target();
            if (code == null || code.isBlank()) {
                problems.add("mapping: target field is required");
                continue;
            }
            if (SystemColumns.ALL.contains(code)) {
                problems.add(code + ": system column cannot be mapped");
                continue;
            }
            LedgerField to = target.get(code);
            if (to == null) {
                problems.add(code + ": not a field of the target ledger");
                continue;
            }
            if (!mapped.add(code)) {
                problems.add(code + ": mapped twice");
                continue;
            }
            if (item.kind() == null) {
                problems.add(code + ": source kind is required");
                continue;
            }
            switch (item.kind()) {
                case FIELD -> {
                    LedgerField from = source.get(item.value());
                    if (from == null) {
                        problems.add(code + ": source field " + item.value() + " does not exist");
                    } else if (!compatible(from.fieldType(), to.fieldType())) {
                        problems.add(code + ": " + from.fieldType() + " cannot fill " + to.fieldType());
                    }
                }
                case CONST -> {
                    if (item.value() == null || !parses(to.fieldType(), item.value())) {
                        problems.add(code + ": constant is not a valid " + to.fieldType());
                    }
                }
                case SYSTEM -> {
                    SystemValue value = systemValue(item.value());
                    if (value == null) {
                        problems.add(code + ": unknown system value " + item.value());
                    } else if (!compatible(systemType(value), to.fieldType())) {
                        problems.add(code + ": " + value + " cannot fill " + to.fieldType());
                    }
                }
            }
        }
        for (LedgerField field : targetFields) {
            if (field.required() && field.deletedAt() == null && !SystemColumns.ALL.contains(field.fieldCode())
                    && !mapped.contains(field.fieldCode())
                    && !"approval_status".equals(field.fieldCode())) {
                problems.add(field.fieldCode() + ": required target field is not mapped");
            }
        }
        return problems;
    }

    /** Builds the business values of the target row (the ledger store coerces them to the column type). */
    public static Map<String, Object> build(List<FieldMapping> mapping, Map<String, Object> sourceRow,
            Completion completion) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (FieldMapping item : mapping) {
            Object value = switch (item.kind()) {
                case FIELD -> normalize(sourceRow == null ? null : sourceRow.get(item.value()));
                case CONST -> item.value();
                case SYSTEM -> system(systemValue(item.value()), completion);
            };
            row.put(item.target(), value);
        }
        return row;
    }

    static SystemValue systemValue(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return SystemValue.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static Object system(SystemValue value, Completion completion) {
        if (value == null || completion == null) {
            return null;
        }
        return switch (value) {
            case SOURCE_RECORD_ID -> completion.sourceRecordId() == null ? null : completion.sourceRecordId().toString();
            case INITIATOR -> completion.initiator();
            case LAST_APPROVER -> completion.lastApprover();
            case COMPLETED_AT -> completion.completedAt() == null ? null : completion.completedAt().toString();
            case OUTCOME -> completion.outcome();
        };
    }

    private static FieldType systemType(SystemValue value) {
        return value == SystemValue.COMPLETED_AT ? FieldType.TIMESTAMPTZ : FieldType.TEXT;
    }

    /** TEXT takes anything; other types need the same type (INTEGER may fill NUMERIC). */
    static boolean compatible(FieldType from, FieldType to) {
        return to == FieldType.TEXT || from == to || (from == FieldType.INTEGER && to == FieldType.NUMERIC);
    }

    static boolean parses(FieldType type, String raw) {
        try {
            switch (type) {
                case TEXT -> { return true; }
                case NUMERIC -> new BigDecimal(raw.trim());
                case INTEGER -> Long.parseLong(raw.trim());
                case BOOLEAN -> {
                    String value = raw.trim().toLowerCase(Locale.ROOT);
                    return value.equals("true") || value.equals("false");
                }
                case TIMESTAMPTZ -> Instant.parse(raw.trim());
            }
            return true;
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private static Object normalize(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().toString();
        }
        if (value instanceof OffsetDateTime time) {
            return time.toInstant().toString();
        }
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        return value;
    }

    private static Map<String, LedgerField> byCode(List<LedgerField> fields) {
        return (fields == null ? List.<LedgerField>of() : fields).stream()
                .filter(field -> field.deletedAt() == null)
                .collect(Collectors.toMap(LedgerField::fieldCode, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }
}
