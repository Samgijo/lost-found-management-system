import java.sql.*;

public class AlterVerifiedReportsAddContact {
    public static void main(String[] args) {
        if (args.length < 5) {
            System.err.println("Usage: AlterVerifiedReportsAddContact <host> <port> <db> <user> <pass>");
            System.exit(2);
        }
        String host = args[0];
        String port = args[1];
        String db = args[2];
        String user = args[3];
        String pass = args[4];
        String url = String.format("jdbc:mysql://%s:%s/%s?serverTimezone=UTC&useSSL=false", host, port, db);
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC driver not found on classpath: " + e.getMessage());
            System.exit(3);
        }
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            System.out.println("Connected to MySQL: " + url);
            try (Statement st = conn.createStatement()) {
                // Add column if not exists (MySQL 8+ supports IF NOT EXISTS in ALTER TABLE ADD)
                String sql = "ALTER TABLE verified_reports ADD COLUMN IF NOT EXISTS contact_info VARCHAR(255)";
                try {
                    st.execute(sql);
                    System.out.println("Executed: " + sql);
                } catch (SQLException ex) {
                    // If server doesn't support IF NOT EXISTS in ALTER, try to detect existence then add
                    System.out.println("ALTER with IF NOT EXISTS failed or not supported: " + ex.getMessage());
                    // Check columns
                    try (ResultSet rs = conn.getMetaData().getColumns(null, null, "verified_reports", null)) {
                        boolean found = false;
                        while (rs.next()) {
                            String col = rs.getString("COLUMN_NAME");
                            if (col != null && col.equalsIgnoreCase("contact_info")) { found = true; break; }
                        }
                        if (!found) {
                            String sql2 = "ALTER TABLE verified_reports ADD COLUMN contact_info VARCHAR(255)";
                            st.execute(sql2);
                            System.out.println("Executed fallback: " + sql2);
                        } else {
                            System.out.println("Column contact_info already exists, nothing to do.");
                        }
                    }
                }
                // Print final columns
                try (ResultSet rs = st.executeQuery("SHOW COLUMNS FROM verified_reports")) {
                    System.out.println("--- verified_reports columns after change ---");
                    while (rs.next()) {
                        System.out.printf("%s | %s | NULL=%s | KEY=%s | DEFAULT=%s | %s%n",
                            rs.getString("Field"), rs.getString("Type"), rs.getString("Null"), rs.getString("Key"), rs.getString("Default"), rs.getString("Extra"));
                    }
                }
            }
        } catch (SQLException ex) {
            System.err.println("SQL error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
