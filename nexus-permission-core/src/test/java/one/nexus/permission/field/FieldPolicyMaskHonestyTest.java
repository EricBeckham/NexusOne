package one.nexus.permission.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.TableDescriptor;
import one.nexus.isolation.api.TableScope;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.engine.FieldRule;
import one.nexus.permission.engine.Mask;
import one.nexus.permission.engine.Predicate;
import one.nexus.permission.scope.QueryAstPolicyCompiler;
import org.junit.jupiter.api.Test;

/**
 * Honesty leftover: mask / second-auth stay on leftoverMaskFields +
 * FieldPolicyProjector. A passing test means the leftover is still open,
 * not that product G1 is closed.
 */
class FieldPolicyMaskHonestyTest {
    private final TableDescriptor table = new TableDescriptor(
            "ledger_entry",
            TableScope.TENANT,
            Set.of("id", "tenant_id", "title", "owner_id", "phone", "secret"),
            "p3",
            1);

    @Test
    void leftoverMaskFieldsStayBesideAstAndHonestyGateRejectsClose() throws Exception {
        assertEquals("NOT_CLAIMED", FieldPolicyMaskHonesty.G1_CLOSE);
        assertEquals("FieldPolicyProjector", FieldPolicyMaskHonesty.LEFTOVER_SURFACE);
        assertTrue(FieldPolicyMaskHonesty.BOUNDARY.contains("beside Query AST"));

        Decision self = new Decision(
                true,
                "self",
                Predicate.atom("owner_id", "=", "u1"),
                Map.of(
                        "title", new FieldRule(true, true),
                        "phone", new FieldRule(true, false, Mask.PARTIAL, true),
                        "secret", new FieldRule(false, false)),
                1L);
        QueryAstPolicyCompiler.Encoding encoded = QueryAstPolicyCompiler.encode(
                QueryAst.select("ledger_entry", List.of("id", "title", "phone", "secret"), Map.of()),
                self,
                table);
        Map<String, FieldRule> leftover = FieldPolicyMaskHonesty.leftoverBesideAst(encoded);
        assertEquals(Mask.PARTIAL, leftover.get("phone").mask());
        assertTrue(leftover.get("phone").secondAuthorization());
        assertFalse(leftover.containsKey("secret"));
        assertEquals(List.of("id", "title", "phone"), encoded.query().projection());

        assertTrue(FieldPolicyMaskHonesty.compiledSqlLeavesMaskToProjector(
                "SELECT \"id\", \"title\", \"phone\" FROM \"ledger_entry\" WHERE \"tenant_id\"::text = ?",
                "phone"));
        assertFalse(FieldPolicyMaskHonesty.compiledSqlLeavesMaskToProjector(
                "SELECT \"id\", mask(\"phone\") FROM \"ledger_entry\"",
                "phone"));
        assertFalse(FieldPolicyMaskHonesty.compiledSqlLeavesMaskToProjector(
                "SELECT \"id\", '***' FROM \"ledger_entry\"",
                "phone"));

        FieldPolicyMaskHonesty.requireHonest(FieldPolicyMaskHonesty.parseReport(fixture("honest-leftover.env")));
        assertThrows(IllegalArgumentException.class, () ->
                FieldPolicyMaskHonesty.requireHonest(FieldPolicyMaskHonesty.parseReport(fixture("fake-g1-close.env"))));
        assertThrows(IllegalArgumentException.class, () ->
                FieldPolicyMaskHonesty.requireHonest(FieldPolicyMaskHonesty.parseReport(fixture("fake-mask-on-ast.env"))));
    }

    private static Path fixture(String name) {
        Path module = Path.of("scripts/g1-fieldpolicy-mask-second-auth/fixtures", name);
        if (module.toFile().isFile()) {
            return module;
        }
        return Path.of("../scripts/g1-fieldpolicy-mask-second-auth/fixtures", name);
    }
}
