import java.sql.*;

public class CopyListingContactToVerifiedContact {
    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.err.println("Usage: CopyListingContactToVerifiedContact <host> <port> <db> <user> <pass>");
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
            String upd = "UPDATE verified_reports vr JOIN listings l ON vr.original_listing_id = l.id SET vr.contact_info = l.contact_info WHERE l.contact_info IS NOT NULL";
            try (PreparedStatement ps = conn.prepareStatement(upd)) {
                int affected = ps.executeUpdate();
                System.out.println("Rows updated with listing contact_info: " + affected);
            }
            try (Statement st = conn.createStatement()) {
                try (ResultSet rs = st.executeQuery("SELECT id, original_listing_id, reporter_email, contact_info FROM verified_reports LIMIT 20")) {
                    System.out.println("--- verified_reports after copying listing contact_info ---");
                    while (rs.next()) {
                        System.out.printf("id=%d original_listing_id=%d reporter_email=%s contact_info=%s%n",
                            rs.getInt("id"), rs.getInt("original_listing_id"), rs.getString("reporter_email"), rs.getString("contact_info"));
                    }
                }
            }
        }
    }
}
