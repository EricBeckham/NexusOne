package business;

import org.springframework.jdbc.core.JdbcTemplate;

/** Intentional G1 violation: raw Spring JDBC against a tenant physical table. */
public final class Business {
    JdbcTemplate jdbc;

    void dump() {
        jdbc.query("SELECT id FROM l_demo_leave", (rs, i) -> rs.getString(1));
    }
}
