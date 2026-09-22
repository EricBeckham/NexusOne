package one.nexus.p3.api;

import java.sql.Connection;
import java.sql.PreparedStatement;

/** Intentional G1 violation: application/API layer opens JDBC and skips QueryCompiler. */
public final class Controller {
    void list(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("select 1")) {
            statement.executeQuery();
        }
    }
}
