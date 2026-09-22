package one.nexus.permission.scope;

import java.util.List;

public record BoundSql(String sql, List<Object> parameters) {
    public BoundSql {
        parameters = List.copyOf(parameters == null ? List.of() : parameters);
    }
}
