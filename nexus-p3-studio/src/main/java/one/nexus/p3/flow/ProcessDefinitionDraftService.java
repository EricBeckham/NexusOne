package one.nexus.p3.flow;

import java.util.List;
import java.util.UUID;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.flow.persist.JdbcProcessDefinitionDraftStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "nexus.flow.engine")
public class ProcessDefinitionDraftService {
    private final JdbcProcessDefinitionDraftStore drafts;

    public ProcessDefinitionDraftService(JdbcProcessDefinitionDraftStore drafts) {
        this.drafts = drafts;
    }

    public List<ProcessDefinitionDraft> list() {
        return drafts.list(requireTenant().tenantId());
    }

    public ProcessDefinitionDraft get(String key) {
        TenantContext context = requireTenant();
        return drafts.find(context.tenantId(), requireKey(key))
                .orElseThrow(() -> FlowException.notFound("process draft not found in current tenant"));
    }

    public ProcessDefinitionDraft save(String key, String name, String bpmnXml) {
        TenantContext context = requireTenant();
        String safeKey = requireKey(key);
        if (name == null || name.isBlank() || name.length() > 160) {
            throw FlowException.badRequest("name is required and must be at most 160 characters");
        }
        if (bpmnXml == null || bpmnXml.isBlank()) {
            throw FlowException.badRequest("bpmn_xml is required");
        }
        return drafts.save(context.tenantId(), context.personId(), safeKey, name.trim(), bpmnXml);
    }

    private static String requireKey(String key) {
        if (key == null || !key.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")) {
            throw FlowException.badRequest("invalid process key");
        }
        return key;
    }

    private static TenantContext requireTenant() {
        TenantContext context = TenantContextHolder.get();
        if (context == null) {
            throw FlowException.missingContext();
        }
        return context;
    }
}
