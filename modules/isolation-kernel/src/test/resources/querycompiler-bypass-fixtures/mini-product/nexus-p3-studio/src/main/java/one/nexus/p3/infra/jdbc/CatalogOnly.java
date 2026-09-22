package one.nexus.p3.infra.jdbc;

import org.springframework.jdbc.core.simple.JdbcClient;

/** Allowlisted catalog JDBC — platform table only, no tenant physical SQL. */
public final class CatalogOnly {
    private final JdbcClient jdbc;

    public CatalogOnly(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long count() {
        return jdbc.sql("SELECT count(*) FROM p3.ledger_definition").query(Long.class).single();
    }
}
