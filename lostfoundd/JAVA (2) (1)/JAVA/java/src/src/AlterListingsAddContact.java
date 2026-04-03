import java.sql.*;

public class AlterListingsAddContact {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            // operate on local SQLite
            System.out.println("Operating on local SQLite lostfound.db");
            Class.forName("org.sqlite.JDBC");
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:lostfound.db")) {
                addContactIfMissing(conn, "listings");
            }
            return;
        }
        if (args.length < 5) {
            System.err.println("Usage: AlterListingsAddContact <host> <port> <db> <user> <pass>");
            System.exit(2);
        }
        String host = args[0];
        String port = args[1];
        String db = args[2];
        String user = args[3];
        String pass = args[4];
        String url = String.format("jdbc:mysql://%s:%s/%s?serverTimezone=UTC&useSSL=false", host, port, db);
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            addContactIfMissing(conn, "listings");
        }
    }

    private static void addContactIfMissing(Connection conn, String table) throws SQLException {
        DatabaseMetaData md = conn.getMetaData();
        try (ResultSet rs = md.getColumns(null, null, table, null)) {
            boolean found = false;
            while (rs.next()) {
                String col = rs.getString("COLUMN_NAME");
                if (col != null && col.equalsIgnoreCase("contact_info")) { found = true; break; }
            }
            if (found) {
                System.out.println("Table " + table + " already has contact_info column");
                return;
            }
        }
        try (Statement st = conn.createStatement()) {
            String sql = "ALTER TABLE " + table + " ADD COLUMN contact_info VARCHAR(255)";
            st.execute(sql);
            System.out.println("Added contact_info column to " + table);
        } catch (SQLException ex) {
            System.err.println("Failed to add contact_info to " + table + ": " + ex.getMessage());
            throw ex;
        }
    }
}
