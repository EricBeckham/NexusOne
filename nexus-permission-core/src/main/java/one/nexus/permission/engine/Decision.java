package one.nexus.permission.engine;

import java.util.Map;
import java.util.Objects;
import one.nexus.common.permission.PermissionDecision;

public record Decision(
        boolean allowed,
        String reason,
        Predicate scope,
        Map<String, FieldRule> fields,
        Long policyVersion
) {
    public Decision {
        Objects.requireNonNull(reason, "reason");
        scope = scope == null ? Predicate.FALSE : scope;
        fields = Map.copyOf(fields == null ? Map.of() : fields);
    }

    public Decision(boolean allowed, String reason) {
        this(allowed, reason, Predicate.FALSE, Map.of(), null);
    }

    public Decision(boolean allowed, String reason, Predicate scope, Map<String, FieldRule> fields, long version) {
        this(allowed, reason, scope, fields, Long.valueOf(version));
    }

    public PermissionDecision outcome() {
        return allowed ? PermissionDecision.ALLOW : PermissionDecision.DENY;
    }
}
