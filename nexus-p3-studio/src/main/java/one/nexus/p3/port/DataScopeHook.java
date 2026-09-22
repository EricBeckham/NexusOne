package one.nexus.p3.port;

import java.util.Map;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.field.FieldPolicyProjector;
import one.nexus.permission.scope.ScopedQuery;

/**
 * Optional M0-05 DataScope seam for list/detail/total/pagination. Implementations
 * must keep {@code tenant_id} as the first conjunct. Write checks reuse the same
 * {@link Decision} as reads.
 */
public interface DataScopeHook {
    ScopedRead bind(String tenantId, String resource, String action);

    record ScopedRead(ScopedQuery query, Decision decision) {}

    default Map<String, Object> project(Map<String, Object> row, Decision decision) {
        return row;
    }

    default Decision require(String tenantId, String resource, String action) {
        return bind(tenantId, resource, action).decision();
    }

    default void requireWritableFields(Decision decision, Iterable<String> fieldNames) {
        FieldPolicyProjector.assertWritable(decision == null ? Map.of() : decision.fields(), fieldNames);
    }
}
