import java.sql.*;

public class InspectMySQLListings {
    public static void main(String[] args) {
        if (args.length < 5) {
            System.err.println("Usage: InspectMySQLListings <host> <port> <db> <user> <pass>");
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
            printTableInfo(conn, "listings");
            printSamples(conn, "listings", 10);
            System.out.println();
            printTableInfo(conn, "verified_reports");
            printSamples(conn, "verified_reports", 10);
        } catch (SQLException ex) {
            System.err.println("SQL error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private static void printTableInfo(Connection conn, String table) throws SQLException {
        System.out.println("--- Columns for table: " + table + " ---");
        try (Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery("SHOW COLUMNS FROM " + table)) {
                while (rs.next()) {
                    String field = rs.getString("Field");
                    String type = rs.getString("Type");
                    String isNull = rs.getString("Null");
                    String key = rs.getString("Key");
                    String def = rs.getString("Default");
                    String extra = rs.getString("Extra");
                    System.out.printf("%s | %s | NULL=%s | KEY=%s | DEFAULT=%s | %s%n", field, type, isNull, key, def, extra);
                }
            } catch (SQLException ex) {
                System.out.println("Could not show columns for " + table + ": " + ex.getMessage());
            }
        }
    }

    private static void printSamples(Connection conn, String table, int limit) throws SQLException {
        System.out.println("--- Sample rows from: " + table + " (up to " + limit + ") ---");
        String sql = String.format("SELECT * FROM %s LIMIT %d", table, limit);
        try (Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery(sql)) {
                ResultSetMetaData md = rs.getMetaData();
                int cols = md.getColumnCount();
                int row = 0;
                while (rs.next()) {
                    row++;
                    System.out.println("Row " + row + ":");
                    for (int i = 1; i <= cols; i++) {
                        String label = md.getColumnLabel(i);
                        Object val = rs.getObject(i);
                        System.out.printf("  %s = %s%n", label, val == null ? "NULL" : val.toString());
                    }
                }
                if (row == 0) System.out.println("(no rows)");
            } catch (SQLException ex) {
                System.out.println("Could not read samples from " + table + ": " + ex.getMessage());
            }
        }
    }
}
