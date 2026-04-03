public class UiInspector {
    public static void main(String[] args) throws Exception {
        MainApplication app = new MainApplication();
        // reload DB canonical state
        app.reloadListingsFromDB();
        java.lang.reflect.Field allListingsField = MainApplication.class.getDeclaredField("allListings");
        allListingsField.setAccessible(true);
        java.util.List<?> allListings = (java.util.List<?>) allListingsField.get(app);
        java.lang.reflect.Field verifiedField = MainApplication.class.getDeclaredField("verifiedReports");
        verifiedField.setAccessible(true);
        java.util.List<?> verified = (java.util.List<?>) verifiedField.get(app);
        System.out.println("UI inspector: allListings.size=" + allListings.size() + " verifiedReports.size=" + verified.size());
        app.dispose();
    }
}