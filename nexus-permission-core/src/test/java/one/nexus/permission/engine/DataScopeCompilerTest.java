package one.nexus.permission.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DataScopeCompilerTest {
    private final Subject subject = new Subject("t1", "u1", Set.of(), Set.of(), Set.of("d1"),
            Map.of("d1", "/d1/", "d2", "/d1/d2/"));
    private final DepartmentCatalog catalog = new DepartmentCatalog(Map.of(
            "d1", "/d1/",
            "d2", "/d1/d2/",
            "d9", "/other/"));

    @Test
    void compilesSelfDeptChildrenSpecifiedAndAll() {
        assertEquals(Predicate.atom("owner_id", "=", "u1"),
                DataScopeCompiler.compile(DataScopeKind.SELF, subject, catalog, List.of()));
        assertEquals(Predicate.atom("department_id", "=", "d1"),
                DataScopeCompiler.compile(DataScopeKind.DEPT, subject, catalog, List.of()));
        assertEquals(Predicate.atom("department_id", "IN", List.of("d1", "d2")),
                DataScopeCompiler.compile(DataScopeKind.DEPT_AND_CHILDREN, subject, catalog, List.of()));
        assertEquals(Predicate.atom("department_id", "=", "d9"),
                DataScopeCompiler.compile(DataScopeKind.SPECIFIED, subject, catalog, List.of("d9")));
        assertEquals(Predicate.TRUE, DataScopeCompiler.compile(DataScopeKind.ALL, subject, catalog, List.of()));
    }
}
