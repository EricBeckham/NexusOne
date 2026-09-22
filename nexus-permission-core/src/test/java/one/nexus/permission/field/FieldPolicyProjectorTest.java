package one.nexus.permission.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import one.nexus.permission.engine.FieldRule;
import one.nexus.permission.engine.Mask;
import org.junit.jupiter.api.Test;

class FieldPolicyProjectorTest {
    @Test
    void dropsInvisibleMasksSensitiveAndRequiresSecondAuth() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("title", "Alpha");
        row.put("phone", "13812345678");
        row.put("hidden", "secret");
        Map<String, FieldRule> fields = Map.of(
                "title", new FieldRule(true, true),
                "phone", new FieldRule(true, false, Mask.PARTIAL, true),
                "hidden", new FieldRule(false, false));
        Map<String, Object> projected = FieldPolicyProjector.project(row, fields, false);
        assertEquals("Alpha", projected.get("title"));
        assertEquals("***", projected.get("phone"));
        assertFalse(projected.containsKey("hidden"));
        assertEquals("138****78", FieldPolicyProjector.project(row, fields, true).get("phone"));
        assertTrue(FieldPolicyProjector.canEdit("title", fields));
        assertFalse(FieldPolicyProjector.canEdit("phone", fields));
        assertTrue(FieldPolicyProjector.canWrite("title", fields));
        assertFalse(FieldPolicyProjector.canWrite("phone", fields));
        assertTrue(FieldPolicyProjector.canWrite("title", Map.of()));
        FieldPolicyProjector.assertWritable(fields, List.of("title"));
        org.junit.jupiter.api.Assertions.assertThrows(
                one.nexus.permission.support.PermissionException.class,
                () -> FieldPolicyProjector.assertWritable(fields, List.of("hidden")));
    }
}
