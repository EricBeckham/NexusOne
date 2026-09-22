package one.nexus.isolation.api;

import java.util.List;

public record RenderedSql(String sql, List<Object> parameters, TableAccessPlan plan) {
    public RenderedSql {
        sql = Actor.requireToken(sql, "sql");
        parameters = List.copyOf(parameters);
        if (plan == null) {
            throw new IllegalArgumentException("plan required");
        }
    }
}
