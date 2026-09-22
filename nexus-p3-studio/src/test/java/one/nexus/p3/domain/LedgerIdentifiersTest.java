package one.nexus.p3.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LedgerIdentifiersTest {
    @Test
    void acceptsP008CharacterClass() {
        assertEquals("orders", LedgerIdentifiers.requireCode("orders"));
        assertEquals("t_alpha_1", LedgerIdentifiers.requireCode("t_alpha_1"));
        assertEquals("l_alpha_orders", LedgerIdentifiers.requirePhysicalTable("l_alpha_orders"));
        assertEquals("a_crm_orders", LedgerIdentifiers.requirePhysicalTable("a_crm_orders"));
    }

    @Test
    void refusesIllegalIdentifiers() {
        assertThrows(P3Exception.class, () -> LedgerIdentifiers.requireCode("Orders"));
        assertThrows(P3Exception.class, () -> LedgerIdentifiers.requireCode("1bad"));
        assertThrows(P3Exception.class, () -> LedgerIdentifiers.requireCode("bad-name"));
        assertThrows(P3Exception.class, () -> LedgerIdentifiers.requireCode("orders;drop"));
        assertThrows(P3Exception.class, () -> LedgerIdentifiers.requireCode("abcdefghijklmnopqrstuvwxyz"));
        assertThrows(P3Exception.class, () -> LedgerIdentifiers.requireFieldCode("tenant_id"));
        assertThrows(P3Exception.class, () -> LedgerIdentifiers.requireFieldCode("ext_data"));
        assertThrows(P3Exception.class, () -> LedgerIdentifiers.requirePhysicalTable("orders"));
        assertThrows(P3Exception.class, () -> LedgerIdentifiers.requirePhysicalTable("l_ALPHA_x"));
    }

    @Test
    void tableNamesStayWithinPostgresLimit() {
        String table = TableNames.custom("contractalpha", "order_header");
        assertEquals("l_contractalpha_order_header", table);
        assertEquals(true, table.length() <= LedgerIdentifiers.MAX_TABLE);
        assertEquals("a_crm_orders", TableNames.shared("crm", "orders"));
    }
}
