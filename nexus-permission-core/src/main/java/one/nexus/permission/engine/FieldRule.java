package one.nexus.permission.engine;

import java.util.Objects;

public record FieldRule(boolean visible, boolean editable, Mask mask, boolean secondAuthorization) {
    public FieldRule {
        mask = mask == null ? Mask.NONE : mask;
    }

    public FieldRule(boolean visible, boolean editable) {
        this(visible, editable, Mask.NONE, false);
    }

    public static FieldRule merge(Iterable<FieldRule> rules) {
        boolean visible = false;
        boolean editable = false;
        Mask mask = Mask.NONE;
        boolean second = false;
        for (FieldRule rule : rules) {
            Objects.requireNonNull(rule, "rule");
            visible = visible || rule.visible;
            editable = editable || rule.editable;
            mask = Mask.strictest(mask, rule.mask);
            second = second || rule.secondAuthorization;
        }
        return new FieldRule(visible, visible && editable, mask, second);
    }
}
