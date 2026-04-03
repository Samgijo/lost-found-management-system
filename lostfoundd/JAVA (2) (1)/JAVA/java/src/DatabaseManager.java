import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Simple DatabaseManager implemented to provide the minimal API the GUI expects.
 * Supports MySQL (primary) and SQLite (fallback) connections. This file replaces the
 * previous misplaced MainApplication content so the app can compile against a real
 * DatabaseManager source.
 */
public class DatabaseManager {
    private Connection conn;
    private boolean isMySQL = false;

    public static class User {
        public String username;
        public String email;
        public String password;
        public User() {}
        public User(String u, String e, String p) { username = u; email = e; password = p; }
    }

    // Normalize verified_reports.status to 'verified' and remove or mark corresponding listings
    private void ensureVerifiedReportsStatusCanonicalAndCleanup() throws SQLException {
        if (conn == null) return;
        Set<String> vrCols = getTableColumns("verified_reports");
        if (vrCols.contains("status")) {
            // Set all non-empty statuses that are like 'resolved'/'verified' to 'verified'
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("UPDATE verified_reports SET status = 'verified' WHERE lower(status) = 'resolved'");
                st.executeUpdate("UPDATE verified_reports SET status = 'verified' WHERE lower(status) = 'verified'");
            } catch (SQLException ex) {
                // ignore
            }
        }

        // Remove any listings that still show verified/resolved in listings if they have been migrated
        Set<String> listCols = getTableColumns("listings");
        String statusCol = findFirstPresent(listCols, "status", "state");
        if (statusCol == null) return;
        // Try to delete listings where status is verified, but guard for FK constraints with try/catch
        String delSql = "DELETE FROM listings WHERE lower(" + statusCol + ") = 'verified' OR lower(" + statusCol + ") = 'resolved'";
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(delSql);
        } catch (SQLException ex) {
            // If delete fails (FKs), mark them explicitly as 'verified' to ensure UI ignores them
            try (Statement st2 = conn.createStatement()) {
                st2.executeUpdate("UPDATE listings SET " + statusCol + " = 'verified' WHERE lower(" + statusCol + ") = 'resolved'");
                st2.executeUpdate("UPDATE listings SET " + statusCol + " = 'verified' WHERE lower(" + statusCol + ") = 'verified'");
            } catch (SQLException ex2) {
                // ignore
            }
        }
        // Finally, ensure no listings rows contain 'verified'/'resolved' in the status column by clearing them
        try {
            removeVerifiedStatusFromListings();
        } catch (SQLException ex) {
            // non-fatal
        }
    }

    // Clear any 'verified' or 'resolved' values present in listings.status by setting to NULL (or empty string fallback)
    private void removeVerifiedStatusFromListings() throws SQLException {
        if (conn == null) return;
        Set<String> listCols = getTableColumns("listings");
        String statusCol = findFirstPresent(listCols, "status", "state");
        if (statusCol == null) return;
        String updNull = "UPDATE listings SET " + statusCol + " = NULL WHERE lower(" + statusCol + ") = 'verified' OR lower(" + statusCol + ") = 'resolved'";
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(updNull);
            return;
        } catch (SQLException ex) {
            // try empty-string fallback for databases that don't accept NULL in that column
        }
        String updEmpty = "UPDATE listings SET " + statusCol + " = '' WHERE lower(" + statusCol + ") = 'verified' OR lower(" + statusCol + ") = 'resolved'";
        try (Statement st2 = conn.createStatement()) {
            st2.executeUpdate(updEmpty);
        } catch (SQLException ex2) {
            // nothing we can do
        }
    }

    public static class Listing {
        public int id;
        public String email;
        public String name;
        public String category;
        public String description;
        public String date;
        public String location;
        public String status;
        public String contactInfo;
        public String imagePath;
    }

    public static class VerifiedReport {
        public int id;
        public int listingId;
        public String itemName;
        public String status;
        public String email;
        public String dateTime;
        public String resolvedAt;
        public String contactInfo;
        public String adminNotes;
    }

    public static class DashboardStats {
        public int pendingReports;
        public int resolvedCases;
        public int totalUsers;
    }

    // MySQL constructor
    public DatabaseManager(String host, int port, String dbName, String user, String password) {
        // Attempt to dynamically load any JDBC driver jars placed in ./lib
        try { dynamicLoadDrivers(); } catch (Exception ignore) {}
        // Allow an environment variable to force MySQL-only mode (no SQLite fallback)
        boolean mysqlOnly = "1".equals(System.getenv("LOSTFOUND_MYSQL_ONLY"));
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            // Allow public key retrieval in case the server requires it (common on some MySQL installs)
            String url = String.format("jdbc:mysql://%s:%d/%s?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true", host, port, dbName);
            try {
                conn = DriverManager.getConnection(url, user, password);
                isMySQL = true;
                // Ensure MySQL schema/tables exist so the application can run fully on MySQL
                try { ensureMySQLSchema(); } catch (SQLException se) { System.err.println("Warning: failed to ensure MySQL schema: " + se.getMessage()); }
            } catch (SQLException sqle) {
                // If connection failed because the database doesn't exist, try creating it and reconnecting.
                System.err.println("Initial MySQL connect failed: " + sqle.getMessage());
                try {
                    if (createDatabaseIfMissing(host, port, dbName, user, password)) {
                        // attempt reconnect
                        conn = DriverManager.getConnection(url, user, password);
                        isMySQL = true;
                        try { ensureMySQLSchema(); } catch (SQLException se) { System.err.println("Warning: failed to ensure MySQL schema after create: " + se.getMessage()); }
                    } else {
                        throw sqle;
                    }
                } catch (SQLException inner) {
                    throw inner;
                }
            }
        } catch (ClassNotFoundException cnfe) {
            if (mysqlOnly) {
                throw new RuntimeException("MySQL JDBC driver not found and mysql-only mode is enabled", cnfe);
            }
            // MySQL driver unavailable — fall back to SQLite so the application still has a DB.
            System.err.println("MySQL JDBC driver not found, falling back to SQLite: " + cnfe.getMessage());
            System.err.println("Tip: place the MySQL Connector/J jar (for example mysql-connector-java-8.0.33.jar) into the ./lib folder and restart the app.");
            tryFallbackSQLite();
        } catch (Exception ex) {
            if (mysqlOnly) {
                throw new RuntimeException("Could not connect to MySQL and mysql-only mode is enabled", ex);
            }
            System.err.println("Warning: could not connect to MySQL: " + ex.getMessage());
            tryFallbackSQLite();
        }
    }

    // Try to dynamically add jars from ./lib to the system classloader so Driver classes can be found
    private void dynamicLoadDrivers() {
        try {
            File lib = new File("lib");
            if (!lib.exists() || !lib.isDirectory()) return;
            File[] jars = lib.listFiles((d, name) -> name.toLowerCase().endsWith(".jar"));
            if (jars == null || jars.length == 0) return;
            // Use URLClassLoader to add jars to the system classloader
            URL[] urls = new URL[jars.length];
            for (int i = 0; i < jars.length; i++) urls[i] = jars[i].toURI().toURL();
            URLClassLoader sys = (URLClassLoader) ClassLoader.getSystemClassLoader();
            // reflectively invoke addURL
            java.lang.reflect.Method m = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            m.setAccessible(true);
            for (URL u : urls) m.invoke(sys, u);
        } catch (Throwable t) {
            // ignore — dynamic loading is best-effort
        }
    }

    // Try to connect to the MySQL server (without specifying a database) and create the database if missing.
    private boolean createDatabaseIfMissing(String host, int port, String dbName, String user, String password) {
    // Add allowPublicKeyRetrieval to server URL to allow initial DB creation on some servers
    String serverUrl = String.format("jdbc:mysql://%s:%d/?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true", host, port);
        try (Connection c = DriverManager.getConnection(serverUrl, user, password)) {
            String sql = "CREATE DATABASE IF NOT EXISTS `" + dbName + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci";
            try (Statement st = c.createStatement()) {
                st.execute(sql);
                System.out.println("Created or confirmed existence of database: " + dbName);
                return true;
            }
        } catch (SQLException ex) {
            System.err.println("Failed to create database '" + dbName + "': " + ex.getMessage());
            return false;
        }
    }

    // Create tables in MySQL if they don't exist. Uses types appropriate for MySQL.
    private void ensureMySQLSchema() throws SQLException {
        if (conn == null) return;
        String usersSql = "CREATE TABLE IF NOT EXISTS users (" +
                "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                "username VARCHAR(255), " +
                "email VARCHAR(255) UNIQUE, " +
                "password VARCHAR(255)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

    // Use canonical column names for MySQL so the schema contains full listing details.
    // We choose names that are explicit (reporter_email, item_name, report_date) but
    // DatabaseManager.insert/select routines are flexible and will adapt to other names.
    String listingsSql = "CREATE TABLE IF NOT EXISTS listings (" +
        "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
        "reporter_email VARCHAR(255), " +
        "item_name VARCHAR(255), " +
        "category VARCHAR(100), " +
        "description TEXT, " +
        "report_date DATETIME, " +
        "location VARCHAR(255), " +
        "status VARCHAR(50), " +
        "contact_info VARCHAR(255), " +
        "image_path VARCHAR(255)" +
        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

    String verifiedSql = "CREATE TABLE IF NOT EXISTS verified_reports (" +
        "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
        "listing_id INT, " +
        "item_name VARCHAR(255), " +
        "status VARCHAR(50), " +
        "email VARCHAR(255), " +
        "contact_info VARCHAR(255), " +
        "datetime DATETIME, " +
        "resolved_at DATETIME, " +
        "admin_notes TEXT" +
        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        try (Statement st = conn.createStatement()) {
            st.execute(usersSql);
            st.execute(listingsSql);
            st.execute(verifiedSql);
        }
        // After ensuring schema, perform a sync pass: any listings already marked Verified
        // should be moved to the verified_reports table. This uses the same verifyListing
        // logic so behavior is consistent.
        try {
            moveVerifiedListingsToReports();
            // Ensure verified_reports has a contact_info column and populate missing contact values
            try { ensureVerifiedReportsContactColumnAndSync(); } catch (SQLException se) { System.err.println("Warning: failed to ensure/sync contact_info: " + se.getMessage()); }
            // Ensure verified_reports.status is canonical and remove any verified entries still in listings
            try { ensureVerifiedReportsStatusCanonicalAndCleanup(); } catch (SQLException se) { System.err.println("Warning: failed to canonicalize verified_reports status: " + se.getMessage()); }
        } catch (Exception ex) {
            System.err.println("Warning: failed to move pre-verified listings to verified_reports: " + ex.getMessage());
        }
    }

    // Default: use SQLite file-based DB
    public DatabaseManager() {
        tryFallbackSQLite();
    }

    private void tryFallbackSQLite() {
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection("jdbc:sqlite:lostfound.db");
            isMySQL = false;
            // Ensure SQLite has the expected schema so fallback testing works
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT, email TEXT UNIQUE, password TEXT)");
                st.execute("CREATE TABLE IF NOT EXISTS listings (id INTEGER PRIMARY KEY AUTOINCREMENT, reporter_email TEXT, item_name TEXT, category TEXT, description TEXT, report_date TEXT, location TEXT, status TEXT, contact_info TEXT, image_path TEXT)");
                // use common variant of verified_reports that some MySQL DBs have
                st.execute("CREATE TABLE IF NOT EXISTS verified_reports (id INTEGER PRIMARY KEY AUTOINCREMENT, original_listing_id INTEGER, item_name TEXT, status TEXT, reporter_email TEXT, contact_info TEXT, verification_date TEXT, resolved_at TEXT, admin_notes TEXT)");
            } catch (SQLException ex) {
                System.err.println("Warning: failed to ensure SQLite schema: " + ex.getMessage());
            }
            // Ensure contact column exists and sync values from listings if needed
            try { ensureVerifiedReportsContactColumnAndSync(); } catch (SQLException se) { System.err.println("Warning: failed to ensure/sync contact_info (sqlite): " + se.getMessage()); }
            // Also normalize status and cleanup listings in the SQLite fallback path
            try { ensureVerifiedReportsStatusCanonicalAndCleanup(); } catch (SQLException se) { System.err.println("Warning: failed to canonicalize verified_reports status (sqlite): " + se.getMessage()); }
        } catch (Exception ex) {
            throw new RuntimeException("No database available", ex);
        }
    }

    public void closeConnection() {
        if (conn != null) try { conn.close(); } catch (Exception ignored) {}
    }

    public List<User> getAllUsers() throws SQLException {
        List<User> out = new ArrayList<>();
        String sql = "SELECT username, email, password FROM users";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    User u = new User();
                    u.username = rs.getString("username");
                    u.email = rs.getString("email");
                    u.password = rs.getString("password");
                    out.add(u);
                }
            }
        }
        return out;
    }

    public void registerUser(String username, String email, String password) throws SQLException {
        String sql = "INSERT INTO users(username, email, password) VALUES(?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, email);
            ps.setString(3, password);
            ps.executeUpdate();
        }

    }


    public List<Listing> getAllListings() throws SQLException {
        List<Listing> out = new ArrayList<>();
        // Construct a SELECT that aliases available columns to the expected in-memory names.
        Set<String> cols = getTableColumns("listings");
        // Map desired logical names to candidate DB columns (lowercased in 'cols')
        String idCol = findFirstPresent(cols, "id");
        String emailCol = findFirstPresent(cols, "reporter_email", "email", "reporteremail");
        String nameCol = findFirstPresent(cols, "item_name", "name", "itemname");
        String catCol = findFirstPresent(cols, "category");
        String descCol = findFirstPresent(cols, "description", "desc");
        String dateCol = findFirstPresent(cols, "report_date", "date", "datetime");
        String locCol = findFirstPresent(cols, "location");
        String statusCol = findFirstPresent(cols, "status");
        String contactCol = findFirstPresent(cols, "contact_info", "contactinfo");
        String imgCol = findFirstPresent(cols, "image_path", "imagepath");

        StringBuilder sel = new StringBuilder();
        sel.append("SELECT ");
        sel.append(idCol != null ? idCol : "id");
        sel.append(" AS id");
        sel.append(", ");
        sel.append(emailCol != null ? emailCol + " AS email" : "NULL AS email");
        sel.append(", ");
        sel.append(nameCol != null ? nameCol + " AS name" : "NULL AS name");
        sel.append(", ");
        sel.append(catCol != null ? catCol + " AS category" : "NULL AS category");
        sel.append(", ");
        sel.append(descCol != null ? descCol + " AS description" : "NULL AS description");
        sel.append(", ");
        sel.append(dateCol != null ? dateCol + " AS date" : "NULL AS date");
        sel.append(", ");
        sel.append(locCol != null ? locCol + " AS location" : "NULL AS location");
        sel.append(", ");
        sel.append(statusCol != null ? statusCol + " AS status" : "NULL AS status");
        sel.append(", ");
        sel.append(contactCol != null ? contactCol + " AS contact_info" : "NULL AS contact_info");
        sel.append(", ");
        sel.append(imgCol != null ? imgCol + " AS image_path" : "NULL AS image_path");
    sel.append(" FROM listings");

    String sql = sel.toString();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Listing l = new Listing();
                    l.id = rs.getInt("id");

                    // Map the aliased result columns to the Listing structure used by the UI
                    l.email = rs.getString("email");
                    l.name = rs.getString("name");
                    l.category = rs.getString("category");
                    l.description = rs.getString("description");
                    l.date = rs.getString("date");
                    l.location = rs.getString("location");
                    l.status = rs.getString("status");
                    l.contactInfo = rs.getString("contact_info");
                    l.imagePath = rs.getString("image_path");
                    out.add(l);
                }
            }
        }
        return out;
    }

    public void addListing(String reporterEmail, String itemName, String category, String description, String reportedAt, String location, String status, String contactInfo, String imagePath) throws SQLException {
        // Build INSERT dynamically from the actual table columns to support schema differences (MySQL vs SQLite)
        Set<String> cols = getTableColumns("listings");
        if (cols.isEmpty()) {
            throw new SQLException("No columns found for listings table");
        }

        // Candidate column names mapped to values
        java.util.LinkedHashMap<String, Object> cand = new java.util.LinkedHashMap<>();
        // reporter email
        String repCol = findFirstPresent(cols, "reporter_email", "email", "reporteremail");
        if (repCol != null) cand.put(repCol, reporterEmail);
        // item name
        String itemCol = findFirstPresent(cols, "item_name", "name", "itemname");
        if (itemCol != null) cand.put(itemCol, itemName);
        // category
        String catCol = findFirstPresent(cols, "category");
        if (catCol != null) cand.put(catCol, category);
        // description
        String descCol = findFirstPresent(cols, "description");
        if (descCol != null) cand.put(descCol, description);
        // date / report_date
        String dateCol = findFirstPresent(cols, "report_date", "date", "datetime");
        if (dateCol != null) cand.put(dateCol, reportedAt);
        // location
        String locCol = findFirstPresent(cols, "location");
        if (locCol != null) cand.put(locCol, location);
        // status
        String statusCol = findFirstPresent(cols, "status");
        if (statusCol != null) cand.put(statusCol, status);
        // contact_info
        String contactCol = findFirstPresent(cols, "contact_info", "contactinfo");
        if (contactCol != null) cand.put(contactCol, contactInfo);
        // image_path
        String imgCol = findFirstPresent(cols, "image_path", "imagepath");
        if (imgCol != null) cand.put(imgCol, imagePath);

        if (cand.isEmpty()) {
            throw new SQLException("No supported columns found for listings insert");
        }

        StringBuilder colList = new StringBuilder();
        StringBuilder ph = new StringBuilder();
        for (String c : cand.keySet()) {
            if (colList.length() > 0) { colList.append(", "); ph.append(", "); }
            colList.append(c);
            ph.append("?");
        }
        String sql = "INSERT INTO listings(" + colList.toString() + ") VALUES(" + ph.toString() + ")";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            // iterate entries so we know the column name for each value (to handle dates specially)
            for (java.util.Map.Entry<String, Object> e : cand.entrySet()) {
                String col = e.getKey();
                Object v = e.getValue();
                if (v == null) {
                    // if this is a datetime-like column, set SQL TIMESTAMP NULL, otherwise VARCHAR NULL
                    if (col.contains("date") || col.contains("datetime")) ps.setNull(i++, Types.TIMESTAMP);
                    else ps.setNull(i++, Types.VARCHAR);
                    continue;
                }
                // Handle date/time normalization for common UI formats (e.g., "2/9/2025 02:00 AM")
                if ((col.contains("date") || col.contains("datetime")) && v instanceof String) {
                    String raw = ((String) v).trim();
                    java.util.Date parsed = null;
                    // Try several common input formats used by the UI
                    String[] patterns = new String[]{
                        "M/d/yyyy hh:mm a",
                        "M/d/yyyy h:mm a",
                        "MM/dd/yyyy hh:mm a",
                        "MM/dd/yyyy h:mm a",
                        "M/d/yyyy HH:mm",
                        "yyyy-MM-dd HH:mm:ss",
                        "yyyy-MM-dd'T'HH:mm:ss",
                        "yyyy-MM-dd"
                    };
                    for (String p : patterns) {
                        try {
                            java.text.SimpleDateFormat in = new java.text.SimpleDateFormat(p);
                            in.setLenient(false);
                            parsed = in.parse(raw);
                            break;
                        } catch (Exception parseEx) {
                            // try next
                        }
                    }
                    if (parsed != null) {
                        java.sql.Timestamp ts = new java.sql.Timestamp(parsed.getTime());
                        ps.setTimestamp(i++, ts);
                    } else {
                        // Couldn't parse: attempt to parse a simple date, or fall back to binding the raw string
                        try {
                            java.text.SimpleDateFormat alt = new java.text.SimpleDateFormat("M/d/yyyy");
                            alt.setLenient(true);
                            parsed = alt.parse(raw);
                            java.sql.Timestamp ts = new java.sql.Timestamp(parsed.getTime());
                            ps.setTimestamp(i++, ts);
                        } catch (Exception ex2) {
                            // As a last resort, bind the raw string (may still fail on strict DATETIME columns)
                            ps.setString(i++, raw);
                        }
                    }
                } else {
                    // default: bind as string
                    ps.setString(i++, v.toString());
                }
            }
            ps.executeUpdate();
        }

    }

    public List<VerifiedReport> getVerifiedReports() throws SQLException {
        List<VerifiedReport> out = new ArrayList<>();
        // Query all columns and map flexibly to support different schemas (MySQL vs SQLite variations)
        String sql = "SELECT * FROM verified_reports";
        Set<String> cols = getTableColumns("verified_reports");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    VerifiedReport r = new VerifiedReport();
                    if (cols.contains("id")) r.id = rs.getInt("id");
                    String listingCol = findFirstPresent(cols, "listing_id", "listingid", "listing");
                    if (listingCol != null) r.listingId = rs.getInt(listingCol);
                    String itemCol = findFirstPresent(cols, "item_name", "itemname", "item");
                    if (itemCol != null) r.itemName = rs.getString(itemCol);
                    if (cols.contains("status")) r.status = rs.getString("status");
                    String emailCol = findFirstPresent(cols, "email", "reporter_email", "reporteremail");
                    if (emailCol != null) r.email = rs.getString(emailCol);
                    String dtCol = findFirstPresent(cols, "datetime", "date", "resolved_at", "report_date");
                    if (dtCol != null) r.dateTime = rs.getString(dtCol);
                    String resCol = findFirstPresent(cols, "resolved_at", "resolvedat");
                    if (resCol != null) r.resolvedAt = rs.getString(resCol);
                    String contactCol = findFirstPresent(cols, "contact_info", "reporter_contact", "reporter_contact_info");
                    if (contactCol != null) r.contactInfo = rs.getString(contactCol);
                    String notesCol = findFirstPresent(cols, "admin_notes", "adminnotes", "notes");
                    if (notesCol != null) r.adminNotes = rs.getString(notesCol);
                    out.add(r);
                }
            }
        }
        return out;
    }

    // Return a set of lower-cased column names for the given table present in the current connection
    private Set<String> getTableColumns(String tableName) throws SQLException {
        Set<String> s = new HashSet<>();
        if (conn == null) return s;
        DatabaseMetaData md = conn.getMetaData();
        try (ResultSet rs = md.getColumns(null, null, tableName, null)) {
            while (rs.next()) {
                String col = rs.getString("COLUMN_NAME");
                if (col != null) s.add(col.toLowerCase());
            }
        }
        return s;
    }

    // Find the first candidate (already lower-cased) that exists in the set of columns
    private String findFirstPresent(Set<String> colsLower, String... candidates) {
        for (String c : candidates) {
            if (colsLower.contains(c.toLowerCase())) return c.toLowerCase();
        }
        return null;
    }

    // Mark a listing as verified: copy data to verified_reports and remove from listings
    public void verifyListing(int listingId, String adminNotes) throws SQLException {
        // Avoid creating duplicate verified_reports rows for the same listing
        if (isListingAlreadyVerified(listingId)) {
            // Already verified — update admin notes if possible then return
            try {
                Set<String> vrCols = getTableColumns("verified_reports");
                String notesCol = findFirstPresent(vrCols, "admin_notes", "adminnotes", "notes");
                String idCol = findFirstPresent(vrCols, "original_listing_id", "listing_id", "id");
                if (notesCol != null && idCol != null) {
                    String upd = "UPDATE verified_reports SET " + notesCol + " = ? WHERE " + idCol + " = ?";
                    try (PreparedStatement ups = conn.prepareStatement(upd)) {
                        ups.setString(1, adminNotes);
                        ups.setInt(2, listingId);
                        ups.executeUpdate();
                    }
                }
            } catch (SQLException ex) {
                // ignore update failures — nothing critical
            }
            return;
        }
        // Read listing generically (support both SQLite and MySQL column names)
        String sel = "SELECT * FROM listings WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sel)) {
            ps.setInt(1, listingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    // discover available listing columns
                    Set<String> listingCols = getTableColumns("listings");
                    String itemNameCol = findFirstPresent(listingCols, "item_name", "name", "itemname");
                    String statusCol = findFirstPresent(listingCols, "status");
                    String emailCol = findFirstPresent(listingCols, "reporter_email", "email", "reporteremail");
                    String dateCol = findFirstPresent(listingCols, "date", "report_date", "datetime");

                    String itemName = itemNameCol != null ? rs.getString(itemNameCol) : null;
                    String status = statusCol != null ? rs.getString(statusCol) : null;
                    String email = emailCol != null ? rs.getString(emailCol) : null;
                    String contactInfo = null;
                    String contactCol = findFirstPresent(listingCols, "contact_info", "contactinfo", "reporter_contact", "reportercontact");
                    if (contactCol != null) contactInfo = rs.getString(contactCol);
                    String datetime = dateCol != null ? rs.getString(dateCol) : null;

                    // prepare insertion into verified_reports using only columns that exist
                    Set<String> vrCols = getTableColumns("verified_reports");
                    StringBuilder colList = new StringBuilder();
                    StringBuilder ph = new StringBuilder();
                    java.util.List<Object> values = new java.util.ArrayList<>();

                    // Some schemas expect an 'original_listing_id' column - populate it if present
                    if (vrCols.contains("original_listing_id")) {
                        colList.append("original_listing_id"); ph.append("?"); values.add(listingId);
                    }

                    // reporter_email field (common in canonical schema)
                    if (vrCols.contains("reporter_email") && email != null) {
                        if (colList.length() > 0) { colList.append(", "); ph.append(", "); }
                        colList.append("reporter_email"); ph.append("?"); values.add(email);
                    }

                    // verification_date or datetime: prefer verification_date
                    boolean addedVerDate = false;
                    if (vrCols.contains("verification_date")) {
                        if (colList.length() > 0) { colList.append(", "); ph.append(", "); }
                        colList.append("verification_date"); ph.append("?"); values.add(new java.sql.Timestamp(System.currentTimeMillis()));
                        addedVerDate = true;
                    }

                    // desired mapping: listing_id, item_name, status, email, datetime, resolved_at, admin_notes
                    String[] desired = new String[]{"listing_id", "item_name", "status", "email", "datetime", "resolved_at", "contact_info", "admin_notes"};
                    for (String d : desired) {
                        if (!vrCols.contains(d)) continue;
                        // avoid duplicate if we've already added a similar column above
                        if ((d.equals("listing_id") && vrCols.contains("original_listing_id")) || (d.equals("email") && vrCols.contains("reporter_email")) || (d.equals("datetime") && addedVerDate)) continue;
                        if (colList.length() > 0) { colList.append(", "); ph.append(", "); }
                        colList.append(d);
                        ph.append("?");
                        switch (d) {
                            case "listing_id": values.add(listingId); break;
                            case "item_name": values.add(itemName); break;
                            case "status": 
                                // Force verified_reports.status to a canonical 'verified' value
                                values.add("verified"); 
                                break;
                            case "email": values.add(email); break;
                            case "datetime": values.add(datetime); break;
                            case "contact_info": values.add(contactInfo); break;
                            case "resolved_at": values.add(new java.util.Date().toString()); break;
                            case "admin_notes": values.add(adminNotes); break;
                            default: values.add(null); break;
                        }
                    }
                    if (colList.length() > 0) {
                        String ins = "INSERT INTO verified_reports(" + colList.toString() + ") VALUES(" + ph.toString() + ")";
                        try (PreparedStatement ps2 = conn.prepareStatement(ins)) {
                            for (int i = 0; i < values.size(); i++) {
                                Object v = values.get(i);
                                if (v == null) ps2.setNull(i+1, Types.VARCHAR);
                                else if (v instanceof Integer) ps2.setInt(i+1, (Integer)v);
                                else ps2.setString(i+1, v.toString());
                            }
                            ps2.executeUpdate();
                        }
                    }
                    // Prefer deleting the listing so verified items are not present in `listings`.
                    // If deletion fails (for example due to FK constraints), fall back to updating
                    // the listing status to 'verified' so it isn't treated as an active listing.
                    try {
                        deleteListing(listingId);
                    } catch (SQLException delEx) {
                        try {
                            String up = "UPDATE listings SET status = ? WHERE id = ?";
                            try (PreparedStatement ups = conn.prepareStatement(up)) {
                                ups.setString(1, "verified");
                                ups.setInt(2, listingId);
                                ups.executeUpdate();
                            }
                        } catch (SQLException ex) {
                            // Could not delete or update; log and continue
                            System.err.println("Warning: could not remove or mark listing id=" + listingId + " as verified: " + ex.getMessage());
                        }
                    }
                }
            }
        }
    }

    // Helper to run a query and return list of maps (columnLabel -> value) for simple printing
    public java.util.List<java.util.Map<String,Object>> runQuery(String sql) throws SQLException {
        java.util.List<java.util.Map<String,Object>> out = new java.util.ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                java.sql.ResultSetMetaData md = rs.getMetaData();
                int cols = md.getColumnCount();
                while (rs.next()) {
                    java.util.Map<String,Object> row = new java.util.HashMap<>();
                    for (int i = 1; i <= cols; i++) {
                        row.put(md.getColumnLabel(i), rs.getObject(i));
                    }
                    out.add(row);
                }
            }
        }
        return out;
    }

    public void deleteListing(int id) throws SQLException {
        String sql = "DELETE FROM listings WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    /**
     * Delete a verified_report by id or by listing reference. Tries several common column names
     * so it works across schema variants.
     */
    public boolean deleteVerifiedReport(int id) throws SQLException {
        if (conn == null) return false;
        // try primary id column
        String[] tries = new String[]{"id", "listing_id", "original_listing_id"};
        for (String col : tries) {
            String sql = "DELETE FROM verified_reports WHERE " + col + " = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                int affected = ps.executeUpdate();
                if (affected > 0) return true;
            } catch (SQLException ex) {
                // if column doesn't exist, try next
                // continue silently
            }
        }
        return false;
    }

    public void deleteUser(String email) throws SQLException {
        String sql = "DELETE FROM users WHERE email = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.executeUpdate();
        }
    }

    public DashboardStats getDashboardStats() throws SQLException {
        DashboardStats s = new DashboardStats();
        try (Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) AS c FROM listings")) { if (rs.next()) s.pendingReports = rs.getInt("c"); }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) AS c FROM verified_reports")) { if (rs.next()) s.resolvedCases = rs.getInt("c"); }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) AS c FROM users")) { if (rs.next()) s.totalUsers = rs.getInt("c"); }
        }
        return s;
    }

    /**
     * Scan the listings table for rows marked as Verified (or resolved) and
     * run the verify flow so they are copied into verified_reports.
     */
    public void moveVerifiedListingsToReports() throws SQLException {
        if (conn == null) return;
        // Prefer the actual status column name if present so we handle schema variants
        Set<String> listingCols = getTableColumns("listings");
        String statusCol = findFirstPresent(listingCols, "status", "state");
        String sql;
        if (statusCol != null) {
            sql = "SELECT id FROM listings WHERE lower(" + statusCol + ") = 'verified' OR lower(" + statusCol + ") = 'resolved'";
        } else {
            // fallback to the common name
            sql = "SELECT id FROM listings WHERE lower(status) = 'verified' OR lower(status) = 'resolved'";
        }
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                java.util.List<Integer> ids = new java.util.ArrayList<>();
                while (rs.next()) {
                    ids.add(rs.getInt("id"));
                }
                for (Integer id : ids) {
                    try {
                        // skip if already migrated to avoid duplicates
                        if (isListingAlreadyVerified(id)) continue;
                        verifyListing(id, "Migrated to verified_reports via sync");
                    } catch (SQLException ex) {
                        System.err.println("Warning: failed to migrate listing id=" + id + ": " + ex.getMessage());
                    }
                }
            }
        }
    }

    // Ensure verified_reports.contact_info exists and populate from listings when missing
    private void ensureVerifiedReportsContactColumnAndSync() throws SQLException {
        if (conn == null) return;
        Set<String> vrCols = getTableColumns("verified_reports");
        // Determine the proper column name to use for contact info in verified_reports
        String contactCol = findFirstPresent(vrCols, "contact_info", "reporter_contact", "reporter_contact_info");
        if (contactCol == null) {
            // Add the column in MySQL; SQLite ALTER TABLE ADD COLUMN is supported for simple adds too
            String alter = "ALTER TABLE verified_reports ADD COLUMN contact_info VARCHAR(255)";
            try (Statement st = conn.createStatement()) {
                st.execute(alter);
                // refresh vrCols
                vrCols = getTableColumns("verified_reports");
                contactCol = findFirstPresent(vrCols, "contact_info");
            } catch (SQLException ex) {
                // If this fails, just log and return — not critical
                System.err.println("Warning: failed to add contact_info to verified_reports: " + ex.getMessage());
                return;
            }
        }

        // Copy contact_info from listings for verified_reports rows where contact is missing
        // We try several likely candidate columns for listing->verified mapping
        Set<String> listingCols = getTableColumns("listings");
        String listContactCol = findFirstPresent(listingCols, "contact_info", "contactinfo", "reporter_contact", "reportercontact");
        if (listContactCol == null) return; // nothing to copy from

        // Determine link column in verified_reports to match back to listings
        String idCol = findFirstPresent(vrCols, "original_listing_id", "listing_id", "id");
        String vrIdCol = idCol != null ? idCol : null;

        // If there's a link column, perform an UPDATE ... JOIN (MySQL) or two-step update for SQLite
        if (isMySQL && vrIdCol != null && vrIdCol.equalsIgnoreCase("original_listing_id") || isMySQL && vrIdCol != null && vrIdCol.equalsIgnoreCase("listing_id")) {
            // MySQL supports UPDATE ... JOIN
            String upd = "UPDATE verified_reports vr JOIN listings l ON (l.id = vr." + vrIdCol + ") SET vr." + contactCol + " = l." + listContactCol + " WHERE (vr." + contactCol + " IS NULL OR vr." + contactCol + " = '') AND (l." + listContactCol + " IS NOT NULL AND l." + listContactCol + " != '')";
            try (Statement st = conn.createStatement()) {
                st.executeUpdate(upd);
            } catch (SQLException ex) {
                // ignore failures
            }
        } else {
            // Generic approach: select mappings and run updates per-row
            String sel = "SELECT vr.id AS vr_id, vr." + (vrIdCol != null ? vrIdCol : "id") + " AS listing_id, l." + listContactCol + " AS contact FROM verified_reports vr LEFT JOIN listings l ON l.id = vr." + (vrIdCol != null ? vrIdCol : "id") + " WHERE (vr." + contactCol + " IS NULL OR vr." + contactCol + " = '') AND (l." + listContactCol + " IS NOT NULL AND l." + listContactCol + " != '')";
            try (PreparedStatement ps = conn.prepareStatement(sel)) {
                try (ResultSet rs = ps.executeQuery()) {
                    java.util.List<java.util.Map<String,Object>> rows = new java.util.ArrayList<>();
                    while (rs.next()) {
                        int vrId = rs.getInt("vr_id");
                        String contact = rs.getString("contact");
                        if (contact != null && !contact.isEmpty()) {
                            rows.add(java.util.Map.of("vr_id", vrId, "contact", contact));
                        }
                    }
                    if (!rows.isEmpty()) {
                        String updateSql = "UPDATE verified_reports SET " + contactCol + " = ? WHERE id = ?";
                        try (PreparedStatement ups = conn.prepareStatement(updateSql)) {
                            for (java.util.Map<String,Object> r : rows) {
                                ups.setString(1, (String) r.get("contact"));
                                ups.setInt(2, (Integer) r.get("vr_id"));
                                ups.addBatch();
                            }
                            ups.executeBatch();
                        }
                    }
                }
            } catch (SQLException ex) {
                // ignore
            }
        }
    }

    // Check if a listing is already present in verified_reports using common candidate columns
    private boolean isListingAlreadyVerified(int listingId) throws SQLException {
        Set<String> vrCols = getTableColumns("verified_reports");
        if (vrCols.isEmpty()) return false;
        String[] candidates = new String[]{"original_listing_id", "listing_id", "id"};
        for (String c : candidates) {
            if (!vrCols.contains(c)) continue;
            String sql = "SELECT COUNT(*) AS c FROM verified_reports WHERE " + c + " = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, listingId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int cnt = rs.getInt("c");
                        if (cnt > 0) return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Update an existing user's username and/or password by email.
     */
    public void updateUser(String email, String newUsername, String newPassword) throws SQLException {
        String sql = "UPDATE users SET username = ?, password = ? WHERE email = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newUsername);
            ps.setString(2, newPassword);
            ps.setString(3, email);
            ps.executeUpdate();
        }
    }

    /**
     * Authenticate against the database. Returns a User if username,email,password match a row, otherwise null.
     */
    public User authenticateUser(String email, String username, String password) throws SQLException {
        // If username is provided, require it; otherwise authenticate by email+password only.
        boolean hasUsername = username != null && !username.trim().isEmpty();
        String sql;
        if (hasUsername) {
            sql = "SELECT username, email, password FROM users WHERE email = ? AND password = ? AND username = ?";
        } else {
            sql = "SELECT username, email, password FROM users WHERE email = ? AND password = ?";
        }
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, password);
            if (hasUsername) ps.setString(3, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    User u = new User();
                    u.username = rs.getString("username");
                    u.email = rs.getString("email");
                    u.password = rs.getString("password");
                    return u;
                }
            }
        }
        return null;
    }

    /**
     * Simple diagnostic main. Usage:
     *  - no args: uses SQLite fallback (lostfound.db) and prints stats
     *  - args: host port db user pass  -> attempts MySQL connect
     */
    public static void main(String[] args) {
        System.out.println("DatabaseManager diagnostic starting...");
        DatabaseManager dm = null;
        try {
            if (args.length >= 5) {
                String host = args[0];
                int port = Integer.parseInt(args[1]);
                String db = args[2];
                String user = args[3];
                String pass = args[4];
                dm = new DatabaseManager(host, port, db, user, pass);
            } else {
                dm = new DatabaseManager();
            }
            System.out.println("Connected. isMySQL=" + dm.isMySQL);
            try {
                DashboardStats s = dm.getDashboardStats();
                System.out.println("Stats: listings=" + s.pendingReports + " verified=" + s.resolvedCases + " users=" + s.totalUsers);
            } catch (Exception ex) {
                System.err.println("Failed to read stats: " + ex.getMessage());
            }
        } catch (Exception ex) {
            System.err.println("Database diagnostic failed: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            if (dm != null) dm.closeConnection();
        }
        System.out.println("Done.");
    }
}

