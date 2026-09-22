package one.nexus.p4.api;

import org.springframework.jdbc.core.JdbcTemplate;

/** Mini-product intentional violation: market API dumps a tenant table. */
public final class RawTenantDump {
    private final JdbcTemplate jdbc;

    public RawTenantDump(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void dump() {
        jdbc.queryForList("SELECT * FROM l_acme_orders");
    }
}
