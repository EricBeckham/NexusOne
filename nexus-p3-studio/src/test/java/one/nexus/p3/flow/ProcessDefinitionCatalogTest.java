package one.nexus.p3.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProcessDefinitionCatalogTest {
    private final ProcessDefinitionCatalog catalog = new ProcessDefinitionCatalog();

    @Test
    void readsSequentialApprovalFromTheClasspath() {
        Map<String, Object> definition = catalog.get("nexusSequentialApproval");
        assertEquals("Nexus sequential approval", definition.get("name"));
        assertEquals("classpath", definition.get("source"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) definition.get("nodes");
        assertTrue(nodes.stream().anyMatch(node ->
                "approveTask".equals(node.get("id")) && "userTask".equals(node.get("type"))));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> flows = (List<Map<String, Object>>) definition.get("flows");
        assertTrue(flows.stream().anyMatch(flow ->
                "toApproved".equals(flow.get("id"))
                        && "${outcome == 'APPROVE'}".equals(flow.get("condition"))));
        assertTrue(flows.stream().anyMatch(flow ->
                "toApprove".equals(flow.get("id")) && flow.get("condition") == null));
        assertNull(catalog.list().stream()
                .filter(row -> "nexusSequentialApproval".equals(row.get("key")))
                .findFirst()
                .orElseThrow()
                .get("tenant_id"));
    }

    @Test
    void unknownKeyIsNotFound() {
        FlowException missing = assertThrows(FlowException.class, () -> catalog.get("missing"));
        assertEquals(404, missing.httpStatus());
    }
}
