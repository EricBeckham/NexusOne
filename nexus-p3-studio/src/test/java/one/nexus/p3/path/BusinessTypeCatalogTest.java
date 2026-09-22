package one.nexus.p3.path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;
import one.nexus.p3.domain.P3Exception;
import one.nexus.p3.path.BusinessTypeCatalog.FieldSpec;
import one.nexus.p3.path.BusinessTypeCatalog.TypeSpec;
import org.junit.jupiter.api.Test;

class BusinessTypeCatalogTest {
    @Test
    void threeDistinctRealBusinessClasses() {
        assertEquals(3, BusinessTypeCatalog.all().size());
        TypeSpec leave = BusinessTypeCatalog.require(BusinessTypeCatalog.LEAVE);
        TypeSpec expense = BusinessTypeCatalog.require(BusinessTypeCatalog.EXPENSE);
        TypeSpec travel = BusinessTypeCatalog.require(BusinessTypeCatalog.TRAVEL);
        assertEquals("请假", leave.displayName());
        assertEquals("费用报销", expense.displayName());
        assertEquals("出差申请", travel.displayName());
        assertEquals(Set.of("leave", "expense", "travel"), BusinessTypeCatalog.all().stream()
                .map(TypeSpec::className)
                .collect(Collectors.toSet()));
        Set<String> leaveFields = codes(leave);
        Set<String> expenseFields = codes(expense);
        Set<String> travelFields = codes(travel);
        assertTrue(leaveFields.containsAll(Set.of("title", "days", "applicant")));
        assertTrue(expenseFields.containsAll(Set.of("amount", "category", "payee")));
        assertTrue(travelFields.containsAll(Set.of("destination", "purpose")));
        assertNotEquals(leaveFields, expenseFields);
        assertNotEquals(leaveFields, travelFields);
        assertNotEquals(expenseFields, travelFields);
        assertThrows(P3Exception.class, () -> BusinessTypeCatalog.require("unknown"));
    }

    private static Set<String> codes(TypeSpec type) {
        return type.fields().stream().map(FieldSpec::fieldCode).collect(Collectors.toSet());
    }
}
