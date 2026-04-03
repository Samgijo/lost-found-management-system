import java.sql.*;

public class ClearMySQLListings {
    public static void main(String[] args) {
        if (args.length < 5) {
            System.err.println("Usage: ClearMySQLListings <host> <port> <db> <user> <pass>");
            System.exit(2);
        }
        String host = args[0];
        String port = args[1];
        String db = args[2];
        String user = args[3];
        String pass = args[4];

        String url = String.format("jdbc:mysql://%s:%s/%s?serverTimezone=UTC&useSSL=false", host, port, db);
        Connection conn = null;
        try {
            // Print current java.class.path to help debug missing driver issues
            System.out.println("java.class.path=" + System.getProperty("java.class.path"));
            boolean driverLoaded = false;
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
                driverLoaded = true;
            } catch (ClassNotFoundException cnfe) {
                // try legacy driver class name
                try {
                    Class.forName("com.mysql.jdbc.Driver");
                    driverLoaded = true;
                } catch (ClassNotFoundException cnfe2) {
                    System.err.println("MySQL JDBC driver not found on classpath: tried com.mysql.cj.jdbc.Driver and com.mysql.jdbc.Driver");
                    System.err.println("Ensure the MySQL connector JAR is present in lib/ or included on the classpath and try again.");
                    System.err.println("(You can put the jar into the project's lib/ directory or run with -cp pointing to the connector jar.)");
                    System.err.println("java.class.path=" + System.getProperty("java.class.path"));
                    System.exit(3);
                }
            }
            conn = DriverManager.getConnection(url, user, pass);
            System.out.println("Connected to MySQL: " + url);

            try (Statement st = conn.createStatement()) {
                // Pre-delete counts
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*) AS c FROM verified_reports")) {
                    if (rs.next()) System.out.println("verified_reports BEFORE: " + rs.getInt("c"));
                } catch (SQLException ex) {
                    System.out.println("verified_reports table not found or error: " + ex.getMessage());
                }
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*) AS c FROM listings")) {
                    if (rs.next()) System.out.println("listings BEFORE: " + rs.getInt("c"));
                } catch (SQLException ex) {
                    System.out.println("listings table not found or error: " + ex.getMessage());
                }

                // Delete verified_reports first to avoid FK problems
                int delVerified = 0;
                int delListings = 0;
                try {
                    delVerified = st.executeUpdate("DELETE FROM verified_reports");
                } catch (SQLException ex) {
                    System.out.println("Delete verified_reports failed: " + ex.getMessage());
                }
                try {
                    delListings = st.executeUpdate("DELETE FROM listings");
                } catch (SQLException ex) {
                    System.out.println("Delete listings failed: " + ex.getMessage());
                }

                System.out.println("DELETE executed, verified_reports rows affected (driver may return 0 if unknown): " + delVerified);
                System.out.println("DELETE executed, listings rows affected (driver may return 0 if unknown): " + delListings);

                // Post-delete counts
                try (ResultSet rs2 = st.executeQuery("SELECT COUNT(*) AS c FROM verified_reports")) {
                    if (rs2.next()) System.out.println("verified_reports AFTER: " + rs2.getInt("c"));
                } catch (SQLException ex) {
                    System.out.println("verified_reports table not found after delete or error: " + ex.getMessage());
                }
                try (ResultSet rs2 = st.executeQuery("SELECT COUNT(*) AS c FROM listings")) {
                    if (rs2.next()) System.out.println("listings AFTER: " + rs2.getInt("c"));
                } catch (SQLException ex) {
                    System.out.println("listings table not found after delete or error: " + ex.getMessage());
                }
            }

        } catch (SQLException ex) {
            System.err.println("SQL error: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            if (conn != null) try { conn.close(); } catch (Exception ignored) {}
        }
    }
}
