import java.sql.*;

public class ClearVerifiedReports {
    public static void main(String[] args) {
        String dbFile = "lostfound.db"; // workspace sqlite fallback
        String url = "jdbc:sqlite:" + dbFile;
        try (Connection c = DriverManager.getConnection(url)) {
            System.out.println("Connected to: " + url);
            try (Statement st = c.createStatement()) {
                // Print counts before
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

                int delVerified = 0;
                int delListings = 0;
                // Attempt to delete verified_reports first
                try {
                    delVerified = st.executeUpdate("DELETE FROM verified_reports");
                } catch (SQLException ex) {
                    System.out.println("Delete verified_reports failed: " + ex.getMessage());
                }
                // Then delete listings
                try {
                    delListings = st.executeUpdate("DELETE FROM listings");
                } catch (SQLException ex) {
                    System.out.println("Delete listings failed: " + ex.getMessage());
                }

                System.out.println("DELETE executed, verified_reports rows affected (driver may return 0 if unknown): " + delVerified);
                System.out.println("DELETE executed, listings rows affected (driver may return 0 if unknown): " + delListings);

                // Print counts after
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
        } catch (Exception ex) {
            System.err.println("Error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
