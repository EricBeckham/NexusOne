package one.nexus.bootstrap.isolation.bypass;

import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * Compiled fixture used only to prove the ArchUnit rule fails on an
 * intentional QueryCompiler bypass. Not product code.
 */
public final class RawTenantSqlFixture {
    public void dump(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM l_demo_leave")) {
            statement.executeQuery();
        }
    }
}
