package one.nexus.p3.flow;

import java.util.List;
import java.util.UUID;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.flow.ProcessRecords.ProcessInstanceView;
import one.nexus.p3.flow.ProcessRecords.TodoView;
import one.nexus.p3.flow.persist.JdbcProcessStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "nexus.flow.engine")
public class TodoQueryService {
    private final JdbcProcessStore store;
    private final ApprovalCommentStore comments;

    public TodoQueryService(JdbcProcessStore store, ApprovalCommentStore comments) {
        this.store = store;
        this.comments = comments;
    }

    public List<TodoView> pending() {
        TenantContext tenant = requireTenant();
        return store.pendingFor(tenant.tenantId(), tenant.personId());
    }

    public List<TodoView> done() {
        TenantContext tenant = requireTenant();
        return store.doneBy(tenant.tenantId(), tenant.personId()).stream().map(comments::attach).toList();
    }

    public List<ProcessInstanceView> initiated() {
        TenantContext tenant = requireTenant();
        return store.initiatedBy(tenant.tenantId(), tenant.personId());
    }

    public TodoView get(UUID todoId) {
        TenantContext tenant = requireTenant();
        return comments.attach(store.findTodo(tenant.tenantId(), todoId)
                .orElseThrow(() -> FlowException.notFound("todo not found in current tenant")));
    }

    private static TenantContext requireTenant() {
        TenantContext tenant = TenantContextHolder.get();
        if (tenant == null) {
            throw FlowException.missingContext();
        }
        return tenant;
    }
}
