package one.nexus.permission.scope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import one.nexus.permission.engine.Predicate;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.Test;

class PredicateSqlCompilerTest {
    @Test
    void compilesGoldenP002Scope() {
        Predicate scope = Predicate.allOf(
                Predicate.anyOf(
                        Predicate.atom("owner_id", "=", "u1"),
                        Predicate.atom("department_id", "=", "d1"),
                        Predicate.atom("id", "IN", List.of("r1"))),
                Predicate.atom("status", "=", "active"));
        BoundSql sql = PredicateSqlCompiler.compile(scope);
        assertEquals("((owner_id = ? OR department_id = ? OR id = ?) AND status = ?)", sql.sql());
        assertEquals(List.of("u1", "d1", "r1", "active"), sql.parameters());
    }

    @Test
    void rejectsUnknownColumn() {
        assertThrows(PermissionException.class,
                () -> PredicateSqlCompiler.compile(Predicate.atom("drop_table", "=", "x")));
    }
}
