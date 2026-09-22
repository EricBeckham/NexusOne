package poc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public final class DbProbe {
    private DbProbe() { }

    public static void main(String[] args) throws Exception {
        String url = System.getenv("P0_10_DB_URL");
        String user = System.getenv("P0_10_DB_USER");
        String password = System.getenv("P0_10_DB_PASSWORD");
        if (url == null || !url.startsWith("jdbc:postgresql://") || user == null || user.isBlank()) {
            throw new IllegalArgumentException("Set P0_10_DB_URL (jdbc:postgresql://...) and P0_10_DB_USER");
        }
        try (Connection c = DriverManager.getConnection(url, user, password == null ? "" : password);
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT current_database(), current_user, current_setting('server_version')")) {
            if (!r.next()) throw new IllegalStateException("PostgreSQL returned no version row");
            System.out.println("PASS: PostgreSQL JDBC connection; database=" + r.getString(1)
                    + ", user=" + r.getString(2) + ", version=" + r.getString(3));
        }
    }
}
