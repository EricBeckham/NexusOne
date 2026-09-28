package one.nexus.p3.infra.jdbc;

import java.util.List;
import java.util.UUID;
import one.nexus.p3.portal.HomeModule;
import one.nexus.p3.portal.MenuItem;
import one.nexus.p3.portal.PortalConfigStore;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PLC3. Every statement is scoped by {@code tenant_id}. */
@Repository
@DependsOnDatabaseInitialization
public class JdbcPortalConfigStore implements PortalConfigStore {
    private final JdbcTemplate jdbc;

    public JdbcPortalConfigStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<MenuItem> menu(UUID tenantId) {
        return jdbc.query("""
                SELECT item_id, parent_id, label, entry_key, section, sort_order, hidden
                  FROM business_meta.portal_menu_item
                 WHERE tenant_id = ?
                 ORDER BY sort_order, item_id
                """, (rs, i) -> new MenuItem(
                rs.getObject("item_id", UUID.class),
                rs.getObject("parent_id", UUID.class),
                rs.getString("label"),
                rs.getString("entry_key"),
                rs.getString("section"),
                rs.getInt("sort_order"),
                rs.getBoolean("hidden")), tenantId);
    }

    @Override
    public void replaceMenu(UUID tenantId, List<MenuItem> items) {
        jdbc.update("DELETE FROM business_meta.portal_menu_item WHERE tenant_id = ?", tenantId);
        for (MenuItem item : items) {
            jdbc.update("""
                    INSERT INTO business_meta.portal_menu_item
                        (tenant_id, item_id, parent_id, label, entry_key, section, sort_order, hidden)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, tenantId, item.id(), item.parentId(), item.label(), item.entryKey(), item.section(),
                    item.sort(), item.hidden());
        }
    }

    @Override
    public List<HomeModule> modules(UUID tenantId) {
        return jdbc.query("""
                SELECT module_code, title, sort_order, hidden
                  FROM business_meta.portal_home_module
                 WHERE tenant_id = ?
                 ORDER BY sort_order, module_code
                """, (rs, i) -> new HomeModule(rs.getString("module_code"), rs.getString("title"),
                rs.getInt("sort_order"), rs.getBoolean("hidden")), tenantId);
    }

    @Override
    public void replaceModules(UUID tenantId, List<HomeModule> modules) {
        jdbc.update("DELETE FROM business_meta.portal_home_module WHERE tenant_id = ?", tenantId);
        for (HomeModule module : modules) {
            jdbc.update("""
                    INSERT INTO business_meta.portal_home_module
                        (tenant_id, module_code, title, sort_order, hidden, config)
                    VALUES (?, ?, ?, ?, ?, CAST('{}' AS jsonb))
                    """, tenantId, module.code(), module.title(), module.sort(), module.hidden());
        }
    }
}
