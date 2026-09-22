package one.nexus.isolation.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record RenderedSql(String sql, List<Object> parameters, TableAccessPlan plan) {
    public RenderedSql {
        sql = Actor.requireToken(sql, "sql");
        // JDBC bind values may be SQL NULL (optional ledger columns).
        parameters = Collections.unmodifiableList(new ArrayList<>(parameters == null ? List.of() : parameters));
        if (plan == null) {
            throw new IllegalArgumentException("plan required");
        }
    }
}
