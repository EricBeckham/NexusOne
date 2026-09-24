package one.nexus.p3.infra.jdbc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Deterministic validation rules for persisted platform template drafts. */
public final class FormTemplateDraftValidator {
    private static final Pattern FIELD_KEY = Pattern.compile("[a-z][a-z0-9_]{0,79}");

    private FormTemplateDraftValidator() { }

    public static List<FormTemplateDraftService.ValidationError> validate(
            List<FormTemplateDraftService.FieldView> fields) {
        List<FormTemplateDraftService.ValidationError> errors = new ArrayList<>();
        if (fields == null || fields.isEmpty()) {
            errors.add(new FormTemplateDraftService.ValidationError(null, null, "fields.required", "至少需要一个字段"));
            return List.copyOf(errors);
        }
        Map<String, Integer> counts = new HashMap<>();
        for (var field : fields) counts.merge(field.key(), 1, Integer::sum);
        for (var field : fields) {
            if (!FIELD_KEY.matcher(field.key()).matches()) {
                errors.add(new FormTemplateDraftService.ValidationError(field.id(), field.key(),
                        "field.key.invalid", "字段 key 须以小写字母开头，只能包含小写字母、数字和下划线"));
            }
            if (counts.getOrDefault(field.key(), 0) > 1) {
                errors.add(new FormTemplateDraftService.ValidationError(field.id(), field.key(),
                        "field.key.duplicate", "字段 key 不可重复"));
            }
            if ("SELECT".equals(field.type())
                    && (field.options() == null || !field.options().isArray() || field.options().isEmpty())) {
                errors.add(new FormTemplateDraftService.ValidationError(field.id(), field.key(),
                        "field.options.required", "单选字段至少需要一个选项"));
            }
        }
        return List.copyOf(errors);
    }
}
