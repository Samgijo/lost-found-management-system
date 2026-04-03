import java.util.List;
import java.util.Map;

public class DbInspector {
    public static void main(String[] args) {
        DatabaseManager dm = null;
        try {
            dm = new DatabaseManager();
            System.out.println("Connected to DB (SQLite fallback). Showing table contents:\n");
            try {
                List<Map<String,Object>> listings = dm.runQuery("SELECT * FROM listings");
                System.out.println("--- listings ---");
                if (listings.isEmpty()) System.out.println("(no rows)");
                for (Map<String,Object> r : listings) {
                    System.out.println(r);
                }
            } catch (Exception ex) {
                System.err.println("Failed to query listings: " + ex.getMessage());
            }

            try {
                List<Map<String,Object>> vrs = dm.runQuery("SELECT * FROM verified_reports");
                System.out.println("\n--- verified_reports ---");
                if (vrs.isEmpty()) System.out.println("(no rows)");
                for (Map<String,Object> r : vrs) {
                    System.out.println(r);
                }
            } catch (Exception ex) {
                System.err.println("Failed to query verified_reports: " + ex.getMessage());
            }
        } catch (Exception ex) {
            System.err.println("DbInspector failed: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            if (dm != null) dm.closeConnection();
        }
    }
}
