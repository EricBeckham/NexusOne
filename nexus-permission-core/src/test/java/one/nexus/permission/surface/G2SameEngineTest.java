package one.nexus.permission.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import one.nexus.permission.admin.RoleMatrixCatalog;
import one.nexus.permission.engine.Decision;
import one.nexus.permission.engine.DepartmentCatalog;
import one.nexus.permission.engine.FieldRule;
import one.nexus.permission.engine.PermissionEngine;
import one.nexus.permission.engine.PolicySnapshot;
import one.nexus.permission.engine.Predicate;
import one.nexus.permission.engine.Rule;
import one.nexus.permission.engine.Subject;
import one.nexus.permission.field.FieldPolicyProjector;
import one.nexus.permission.scope.BoundSql;
import one.nexus.permission.scope.PermissionQueryBinder;
import one.nexus.permission.scope.ScopedQuery;
import one.nexus.permission.support.PermissionException;
import org.junit.jupiter.api.Test;

/**
 * G2: role matrix + DataScope + field policy produce one {@link Decision}
 * that list, detail, flow, form studio, form fill, and submit-start must honor. This is
 * not the {@code tenant_member} ALL-scope seed.
 */
class G2SameEngineTest {
    private static final String TENANT = "t-g2";
    private static final Subject SELF_READER = new Subject(
            TENANT, "person-1", Set.of(RoleMatrixCatalog.SELF_READER), Set.of(), Set.of("d1"));
    private static final Subject APPROVER = new Subject(
            TENANT, "person-1", Set.of(RoleMatrixCatalog.APPROVER), Set.of(), Set.of("d1"));

    @Test
    void listDetailAndFlowShareOneDecisionFromRoleMatrix() {
        PolicySnapshot snapshot = snapshotFromCatalog();

        Decision listRead = PermissionEngine.evaluate(
                SELF_READER, TENANT, "ledger", "read", snapshot, List.of(), Predicate.TRUE, DepartmentCatalog.EMPTY);
        Decision detailRead = PermissionEngine.evaluate(
                SELF_READER, TENANT, "ledger", "read", snapshot, List.of(), Predicate.TRUE, DepartmentCatalog.EMPTY);
        Decision flowStart = PermissionEngine.evaluate(
                SELF_READER, TENANT, "process.instance", "start", snapshot);
        Decision formRead = PermissionEngine.evaluate(SELF_READER, TENANT, "form", "read", snapshot);
        Decision formWrite = PermissionEngine.evaluate(SELF_READER, TENANT, "form", "write", snapshot);

        assertEquals(listRead.allowed(), detailRead.allowed());
        assertEquals(listRead.reason(), detailRead.reason());
        assertEquals(listRead.policyVersion(), detailRead.policyVersion());
        assertEquals(listRead.scope(), detailRead.scope());
        assertEquals(listRead.fields(), detailRead.fields());
        assertTrue(listRead.allowed());
        assertEquals("allowed", listRead.reason());
        assertEquals(listRead.scope(), Predicate.atom("owner_id", "=", "person-1"));
        assertFalse(flowStart.allowed());
        assertEquals("no_grant", flowStart.reason());
        PermissionException denied = assertThrows(
                PermissionException.class, () -> SurfaceGuard.requireAllowed(flowStart));
        assertEquals("P2.PERM.0003", denied.errorCode().code());
        assertTrue(formRead.allowed());
        assertFalse(formWrite.allowed());
        assertEquals("no_grant", formWrite.reason());
        PermissionException formDenied = assertThrows(
                PermissionException.class, () -> SurfaceGuard.requireAllowed(formWrite));
        assertEquals("P2.PERM.0003", formDenied.errorCode().code());

        ScopedQuery scoped = PermissionQueryBinder.bind(TENANT, listRead);
        BoundSql count = scoped.count("ledger_row");
        BoundSql list = scoped.list("ledger_row", List.of("id", "title", "salary"), "id", 20, 0);
        BoundSql detail = scoped.detail("ledger_row", List.of("id", "title", "salary"), "id", "r1");
        assertTrue(scoped.whereSql().startsWith("tenant_id = ? AND "));
        assertTrue(scoped.whereSql().contains("owner_id"));
        assertEquals(
                count.sql().substring(count.sql().indexOf("WHERE")),
                list.sql().substring(list.sql().indexOf("WHERE"), list.sql().indexOf(" ORDER")));
        assertEquals(count.parameters(), detail.parameters().subList(0, count.parameters().size()));

        Map<String, Object> projected = FieldPolicyProjector.project(
                Map.of("id", "r1", "title", "leave", "salary", "90000"), listRead.fields());
        assertEquals("leave", projected.get("title"));
        assertFalse(projected.containsKey("salary"));
        assertFalse(FieldPolicyProjector.canWrite("salary", listRead.fields()));
    }

    @Test
    void deptManagerMasksSalaryAndApproverSharesFlowDecision() {
        PolicySnapshot snapshot = snapshotFromCatalog();
        Subject manager = new Subject(
                TENANT, "person-2", Set.of(RoleMatrixCatalog.DEPT_MANAGER), Set.of(), Set.of("d1"));

        Decision read = PermissionEngine.evaluate(manager, TENANT, "ledger", "read", snapshot);
        Decision write = PermissionEngine.evaluate(manager, TENANT, "ledger", "write", snapshot);
        Decision approveDenied = PermissionEngine.evaluate(manager, TENANT, "process.task", "approve", snapshot);
        Decision approve = PermissionEngine.evaluate(APPROVER, TENANT, "process.task", "approve", snapshot);
        Subject designer = new Subject(
                TENANT, "person-3", Set.of(RoleMatrixCatalog.FORM_DESIGNER), Set.of(), Set.of("d1"));
        Decision designerRead = PermissionEngine.evaluate(designer, TENANT, "form", "read", snapshot);
        Decision designerWrite = PermissionEngine.evaluate(designer, TENANT, "form", "write", snapshot);
        Decision designerLedger = PermissionEngine.evaluate(designer, TENANT, "ledger", "read", snapshot);

        assertTrue(read.allowed());
        assertTrue(write.allowed());
        assertEquals(read.scope(), write.scope());
        assertEquals(Predicate.atom("department_id", "=", "d1"), read.scope());
        FieldRule salary = read.fields().get(RoleMatrixCatalog.SALARY_FIELD);
        assertTrue(salary.visible());
        assertFalse(salary.editable());
        Map<String, Object> projected = FieldPolicyProjector.project(
                Map.of("salary", "13812345678", "title", "ok"), read.fields());
        assertEquals("138****78", projected.get("salary"));
        FieldPolicyProjector.assertWritable(write.fields(), List.of("title"));
        assertThrows(
                PermissionException.class,
                () -> FieldPolicyProjector.assertWritable(write.fields(), List.of("salary")));

        assertFalse(approveDenied.allowed());
        assertEquals("no_grant", approveDenied.reason());
        assertTrue(approve.allowed());
        SurfaceGuard.requireAllowed(approve);
        assertEquals("PermissionEngine", SurfaceGuard.asApi(approve).get("engine"));
        assertTrue(designerRead.allowed());
        assertTrue(designerWrite.allowed());
        SurfaceGuard.requireAllowed(designerWrite);
        assertFalse(designerLedger.allowed());
        assertEquals("no_grant", designerLedger.reason());
    }

    private static PolicySnapshot snapshotFromCatalog() {
        List<Rule> grants = RoleMatrixCatalog.exampleCells().stream()
                .filter(cell -> "GRANT".equalsIgnoreCase(cell.effect()))
                .map(cell -> Rule.scoped(
                        "role",
                        cell.roleCode(),
                        cell.resource(),
                        cell.action(),
                        cell.scopeKind(),
                        List.of(),
                        cell.fields()))
                .toList();
        return new PolicySnapshot(TENANT, 7, grants, List.of(), Map.of(), java.time.Instant.parse("2026-09-22T00:00:00Z"));
    }
}
