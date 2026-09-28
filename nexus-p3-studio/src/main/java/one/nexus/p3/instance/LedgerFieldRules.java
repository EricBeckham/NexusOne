package one.nexus.p3.instance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import one.nexus.p3.domain.P3ErrorCodes;
import one.nexus.p3.domain.P3Exception;

/** Tenant customization may add ledger fields. It may not drop or retype a platform field. */
public final class LedgerFieldRules {
    private LedgerFieldRules() {}

    public static void assertAdditive(Map<String, Object> platformContent, Map<String, Object> overrideContent) {
        List<Field> platformFields = fields(platformContent);
        if (platformFields == null) {
            return;
        }
        List<Field> overrideFields = fields(overrideContent);
        if (overrideFields == null) {
            throw invalid("override content must keep platform ledger fields");
        }
        Map<String, String> types = new LinkedHashMap<>();
        for (Field field : overrideFields) {
            types.put(field.code(), field.type());
        }
        List<String> rejected = new ArrayList<>();
        for (Field field : platformFields) {
            String type = types.get(field.code());
            if (type == null || !type.equals(field.type())) {
                rejected.add(field.code());
            }
        }
        if (!rejected.isEmpty()) {
            throw new P3Exception(P3ErrorCodes.INSTANCE_INVALID, 400,
                    "ledger fields may only be added", Map.of("fields", List.copyOf(rejected)));
        }
    }

    private static List<Field> fields(Map<String, Object> content) {
        if (content == null || !content.containsKey("fields")) {
            return null;
        }
        Object raw = content.get("fields");
        if (!(raw instanceof List<?> list)) {
            throw invalid("fields must be a list");
        }
        List<Field> fields = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                throw invalid("each ledger field needs a code and type");
            }
            Object code = map.get("code");
            Object type = map.get("type");
            if (!(code instanceof String codeText) || codeText.isBlank()
                    || !(type instanceof String typeText) || typeText.isBlank()) {
                throw invalid("each ledger field needs a code and type");
            }
            fields.add(new Field(codeText, typeText));
        }
        return fields;
    }

    private static P3Exception invalid(String message) {
        return new P3Exception(P3ErrorCodes.INSTANCE_INVALID, 400, message, Map.of());
    }

    private record Field(String code, String type) {}
}
