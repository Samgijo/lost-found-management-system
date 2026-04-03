import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.sql.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.table.*;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;


public class MainApplication extends JFrame {

    // Main application components
    private CardLayout cardLayout;
    private JPanel mainPanel;
    private JButton homeBtn, reportBtn, viewBtn;
    private JPanel homeAuthArea;

    // Global state
    private String currentRole = "USER";
    private boolean isLoggedIn = false;
    private String loggedInUsername = "";
    private String loggedInEmail = "";

    // Central data store
    private final Map<String, User> usersByEmail = new HashMap<>();
    private final Map<String, User> usersByUsername = new HashMap<>();
    private final List<Listing> allListings = new ArrayList<>();
    private final List<Object[]> verifiedReports = new ArrayList<>();
    // Database manager (optional)
    private DatabaseManager dbManager;

    // Prefill state for Update Account page
    private String updateAccountEmailPrefill = null;
    private String updateAccountUsernamePrefill = null;
    private String updateAccountPasswordPrefill = null;

    // UI panels
    private ViewAllListings viewAllListingsPanel;
    private AdminDashboard adminDashboardPanel;
   

    public MainApplication() {
        setTitle("Lost & Found Management System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);

        // Seed data
       
        // Initialize database manager (connect to MySQL lostfound DB)
        try {
            dbManager = new DatabaseManager("localhost", 3306, "lostfound", "root", "root");
            loadFromDatabase();
            // quick diagnostic summary to check whether listings/reports exist
            showDBSummary();
        } catch (Exception ex) {
            System.err.println("Warning: database initialization failed - running in memory-only mode.");
            ex.printStackTrace();
            dbManager = null;
        }
        // Setup CardLayout
        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);
        mainPanel.setBackground(new Color(18, 24, 35));

        // Create main application panels
    viewAllListingsPanel = new ViewAllListings(allListings, this);
        adminDashboardPanel = new AdminDashboard();

        mainPanel.add(createHomePage(), "Home");
        mainPanel.add(createLoginPage(), "Login");
        mainPanel.add(createSignUpPage(), "SignUp");
    mainPanel.add(createUpdateAccountPage(), "UpdateAccount");
        mainPanel.add(new ReportItemForm(this), "Report");
        mainPanel.add(viewAllListingsPanel, "ViewListings");
        mainPanel.add(adminDashboardPanel, "AdminDashboard");

        // Build navigation bar
        JPanel navBar = new JPanel(new BorderLayout());
        navBar.setPreferredSize(new Dimension(0, 72));
        navBar.setBackground(new Color(18, 24, 35));
       
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 18, 16));
        left.setOpaque(false);
        JLabel logo = new JLabel("Lost&Found");
        logo.setFont(new Font("SansSerif", Font.BOLD, 20));
        logo.setForeground(new Color(66, 133, 244));
        left.add(logo);
        navBar.add(left, BorderLayout.WEST);

        JPanel centerMenu = new JPanel(new FlowLayout(FlowLayout.CENTER, 18, 18));
        centerMenu.setOpaque(false);

        homeBtn = createNavButton("Home");
        reportBtn = createNavButton("Report Item");
        viewBtn = createNavButton("View Listings");
       
        centerMenu.add(homeBtn);
        centerMenu.add(reportBtn);
        centerMenu.add(viewBtn);
        navBar.add(centerMenu, BorderLayout.CENTER);

        homeAuthArea = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 10));
        homeAuthArea.setOpaque(false);
        navBar.add(homeAuthArea, BorderLayout.EAST);
        updateAuthArea(homeAuthArea);

        // Add listeners
        homeBtn.addActionListener(e -> {
            setActiveNavButton(homeBtn);
            cardLayout.show(mainPanel, "Home");
        });
        reportBtn.addActionListener(e -> {
        
            if (isLoggedIn) {
                setActiveNavButton(reportBtn);
                cardLayout.show(mainPanel, "Report");
            } else {
                JOptionPane.showMessageDialog(this, "Please login to use this feature.");
            }
        });
        viewBtn.addActionListener(e -> {
            if (isLoggedIn) {
                setActiveNavButton(viewBtn);
                cardLayout.show(mainPanel, "ViewListings");
            } else {
                JOptionPane.showMessageDialog(this, "Please login to use this feature.");
            }
        });

        // Main window layout
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(navBar, BorderLayout.NORTH);
        wrapper.add(mainPanel, BorderLayout.CENTER);
        setContentPane(wrapper);

        cardLayout.show(mainPanel, "Home");
        setActiveNavButton(homeBtn);

        // ensure DB connection is closed on exit
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (dbManager != null) dbManager.closeConnection();
            }
        });
    }

    private void setActiveNavButton(JButton activeBtn) {
        Color activeColor = new Color(24, 120, 240);
        Color normalColor = new Color(18, 24, 35);
        Color normalText = new Color(200, 205, 210);

        JButton[] all = new JButton[]{homeBtn, reportBtn, viewBtn};
        for (JButton b : all) {
            if (b == null) continue;
            b.setBackground(normalColor);
            b.setForeground(normalText);
        }

        if (activeBtn != null) {
            activeBtn.setBackground(activeColor);
            activeBtn.setForeground(Color.WHITE);
        }
    }

    private void updateAuthArea(JPanel area) {
        area.removeAll();
        area.setLayout(new FlowLayout(FlowLayout.RIGHT, 16, 10));

        if (!isLoggedIn) {
            JButton loginBtn = createActionButton("Login");
            JButton signupBtn = createOutlineButton("Signup");
            loginBtn.addActionListener(e -> cardLayout.show(mainPanel, "Login"));
            signupBtn.addActionListener(e -> cardLayout.show(mainPanel, "SignUp"));
            area.add(signupBtn);
            area.add(loginBtn);
        } else {
            String initials = loggedInUsername.isEmpty() ? "U" : loggedInUsername.substring(0, 1).toUpperCase();
            JButton avatarBtn = new JButton(initials);
            avatarBtn.setFocusPainted(false);
            avatarBtn.setBorderPainted(false);
            avatarBtn.setContentAreaFilled(true);
            avatarBtn.setBackground(new Color(0, 120, 215));
            avatarBtn.setForeground(Color.WHITE);
            avatarBtn.setFont(new Font("Arial", Font.BOLD, 16));
            avatarBtn.setPreferredSize(new Dimension(48, 48));
            avatarBtn.setOpaque(true);
            avatarBtn.setBorder(BorderFactory.createLineBorder(Color.WHITE, 2));
            avatarBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JPopupMenu profileMenu = new JPopupMenu();
            profileMenu.setOpaque(false);
            profileMenu.setBorder(BorderFactory.createEmptyBorder());

            JPanel infoPanel = new JPanel();
            infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
            infoPanel.setBackground(new Color(30, 35, 45));
            infoPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

            JLabel nameLabel = new JLabel(loggedInUsername);
            nameLabel.setForeground(Color.WHITE);
            nameLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
            JLabel emailLabel = new JLabel(loggedInEmail);
            emailLabel.setForeground(new Color(200, 200, 200));
            emailLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

            infoPanel.add(nameLabel);
            infoPanel.add(emailLabel);
            infoPanel.add(Box.createRigidArea(new Dimension(0, 8)));

            if ("ADMIN".equalsIgnoreCase(currentRole)) {
                JButton adminDashBtn = new JButton("Admin Dashboard");
                adminDashBtn.setFocusPainted(false);
                adminDashBtn.setBorderPainted(false);
                adminDashBtn.setContentAreaFilled(false);
                adminDashBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                adminDashBtn.setForeground(new Color(0, 160, 255));
                adminDashBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
                adminDashBtn.addActionListener(ev -> {
                    profileMenu.setVisible(false);
                    setActiveNavButton(null);
                    cardLayout.show(mainPanel, "AdminDashboard");
                    adminDashboardPanel.updateDashboardCounts();
                });
                infoPanel.add(adminDashBtn);
                infoPanel.add(Box.createRigidArea(new Dimension(0, 8)));
            } else {
                JLabel userLbl = new JLabel("User Dashboard");
                userLbl.setForeground(new Color(180, 180, 180));
                userLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
                userLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
                infoPanel.add(userLbl);
                infoPanel.add(Box.createRigidArea(new Dimension(0, 8)));
            }

            JButton logoutBtn = new JButton("Logout");
            logoutBtn.setBackground(new Color(220, 50, 50));
            logoutBtn.setForeground(Color.WHITE);
            logoutBtn.setFocusPainted(false);
            logoutBtn.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
            logoutBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            logoutBtn.addActionListener(e -> {
                isLoggedIn = false;
                loggedInUsername = "";
                loggedInEmail = "";
                currentRole = "USER";
                updateAuthArea(area);
                cardLayout.show(mainPanel, "Home");
            });

            infoPanel.add(logoutBtn);
            profileMenu.add(infoPanel);
            avatarBtn.addActionListener(e -> profileMenu.show(avatarBtn, 0, avatarBtn.getHeight()));
            area.add(avatarBtn);
        }
        area.revalidate();
        area.repaint();
    }

    private void afterLogin(User u) {
        isLoggedIn = true;
        loggedInUsername = u.username;
        loggedInEmail = u.email;

        if (u.username != null && u.username.equalsIgnoreCase("admin")) {
            currentRole = "ADMIN";
        } else {
            currentRole = "USER";
        }
     updateAuthArea(homeAuthArea);
        if (viewAllListingsPanel != null) viewAllListingsPanel.refreshListings();
        // Show a brief welcome message on successful login
        JOptionPane.showMessageDialog(this, "Welcome, " + loggedInUsername + "!", "Login Successful", JOptionPane.INFORMATION_MESSAGE);
        cardLayout.show(mainPanel, "Home");
        setActiveNavButton(homeBtn);
    }
// Paste this inside your MainApplication class, but OUTSIDE all other methods
public void openGmailDirect(String to, String subject, String body) {
    try {
        // Encode URL parameters
        String url = "https://mail.google.com/mail/?view=cm&fs=1" +
                     "&to=" + java.net.URLEncoder.encode(to, "UTF-8") +
                     "&su=" + java.net.URLEncoder.encode(subject, "UTF-8") +
                     "&body=" + java.net.URLEncoder.encode(body, "UTF-8");

        // Open default browser
        if (java.awt.Desktop.isDesktopSupported()) {
            java.awt.Desktop.getDesktop().browse(new URI(url));
        } else {
            JOptionPane.showMessageDialog(this, "Desktop is not supported.");
        }
    } catch (Exception ex) {
        ex.printStackTrace();
        JOptionPane.showMessageDialog(this, "Failed to open Gmail:\n" + ex.getMessage());
    }
}




       

    private JPanel createHomePage() {
        JPanel homePanel = new JPanel(new BorderLayout());
        homePanel.setBackground(new Color(18, 24, 35));
        BackgroundPanel hero = new BackgroundPanel("hero_bg.jpg");
        hero.setLayout(new BoxLayout(hero, BoxLayout.Y_AXIS));
        hero.setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));
        hero.setOpaque(true);
        hero.add(Box.createRigidArea(new Dimension(0, 70)));
        JLabel title = new JLabel("Lost & Found Management System");
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setFont(new Font("SansSerif", Font.BOLD, 36));
        title.setForeground(Color.WHITE);
        JLabel subtitle = new JLabel("Easily report and retrieve lost items with our system.");
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 18));
        subtitle.setForeground(new Color(230, 230, 230));
        RoundedButton bigReport = new RoundedButton("Report Item");
        bigReport.setAlignmentX(Component.CENTER_ALIGNMENT);
        bigReport.setPreferredSize(new Dimension(160, 44));
        bigReport.setMaximumSize(new Dimension(160, 44));
        bigReport.setFont(new Font("SansSerif", Font.BOLD, 14));
        bigReport.setBackground(new Color(0x2f54eb));
        bigReport.setForeground(Color.WHITE);
        bigReport.setOpaque(true);
        bigReport.setBorderPainted(false);
        bigReport.setEnabled(true);
        bigReport.addActionListener(e -> {
            if (isLoggedIn) {
                reportBtn.doClick();
            } else {
                JOptionPane.showMessageDialog(this, "Please login to use this feature.");
            }
        });
        hero.add(title);
        hero.add(Box.createRigidArea(new Dimension(0, 12)));
        hero.add(subtitle);
        hero.add(Box.createRigidArea(new Dimension(0, 22)));
        hero.add(bigReport);
        hero.add(Box.createVerticalGlue());
        JPanel featuresHolder = new JPanel(new GridBagLayout());
        featuresHolder.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 18, 0, 18);
        gbc.gridy = 0;
        FeatureCard c1 = new FeatureCard("Easy Reporting", "Quickly submit lost and found reports.");
        FeatureCard c2 = new FeatureCard("Verified Listings", "Ensuring accurate and valid reports.");
        FeatureCard c3 = new FeatureCard("Secure System", "Your data is safe and protected.");
        gbc.gridx = 0;
        featuresHolder.add(c1, gbc);
        gbc.gridx = 1;
        featuresHolder.add(c2, gbc);
        gbc.gridx = 2;
        featuresHolder.add(c3, gbc);
        JPanel featureContainer = new JPanel();
        featureContainer.setOpaque(false);
        featureContainer.setLayout(new BoxLayout(featureContainer, BoxLayout.Y_AXIS));
        featureContainer.add(Box.createRigidArea(new Dimension(0, 20)));
        featureContainer.add(featuresHolder);
        hero.add(featureContainer);
        homePanel.add(hero, BorderLayout.CENTER);
        return homePanel;
    }

    private JPanel createLoginPage() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(new Color(15, 20, 35));
        root.setOpaque(true);
        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        JPanel card = new JPanel(new GridBagLayout());
        card.setPreferredSize(new Dimension(520, 460));
        card.setBackground(new Color(25, 30, 45));
        card.setBorder(BorderFactory.createLineBorder(new Color(0, 120, 255), 2));
        card.setOpaque(true);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(12, 16, 12, 16);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        JLabel title = new JLabel("Login", SwingConstants.CENTER);
        title.setForeground(new Color(0, 160, 255));
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        card.add(title, gbc);
        gbc.gridy++;
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        JLabel uLabel = fieldLabel("Username");
        JTextField uField = new JTextField(20);
        styleTextField(uField);
        card.add(uLabel, gbc);
        gbc.gridx = 1;
        card.add(uField, gbc);
        gbc.gridy++;
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        JLabel eLabel = fieldLabel("Email");
        JTextField eField = new JTextField(20);
        styleTextField(eField);
        card.add(eLabel, gbc);
        gbc.gridx = 1;
        card.add(eField, gbc);
        gbc.gridy++;
        gbc.gridx = 0;
        JLabel pLabel = fieldLabel("Password");
        JPasswordField pField = new JPasswordField(20);
        stylePasswordField(pField);
        card.add(pLabel, gbc);
        gbc.gridx = 1;
        card.add(pField, gbc);
        gbc.gridy++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        JButton submit = new JButton("Submit");
        submit.setPreferredSize(new Dimension(220, 40));
        submit.setBackground(new Color(24, 120, 240));
        submit.setForeground(Color.WHITE);
        submit.setOpaque(true);
        submit.setBorderPainted(false);
        submit.setFocusPainted(false);
        submit.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.add(submit, gbc);
        gbc.gridy++;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel toSign = linkLabel("Don't have an account? Sign Up");
        toSign.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                cardLayout.show(mainPanel, "SignUp");
            }
        });
        card.add(toSign, gbc);

        Runnable doLogin = () -> {
            String uname = uField.getText().trim();
            String emailInput = eField.getText().trim();
            String pass = new String(pField.getPassword());
            if (emailInput.isEmpty() || pass.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Email and Password are required.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            // Admin backdoor
            if (uname.equals("ADMIN") && pass.equals("ADMIN123")) {
                isLoggedIn = true;
                loggedInUsername = "ADMIN";
                loggedInEmail = "admin@system.local";
                currentRole = "ADMIN";
                JOptionPane.showMessageDialog(this, "Welcome, Admin!");
                updateAuthArea(homeAuthArea);
                cardLayout.show(mainPanel, "Home");
                return;
            }
            // Debug prints to help trace login issues
            System.out.println("Attempt login: username='" + uname + "' email='" + emailInput + "'");
            // lookup user by email (map keys are lowercased)
            User byEmail = usersByEmail.get(emailInput.toLowerCase());
            if (byEmail == null) {
                // If DB manager available, try DB-authenticate directly (handles cases where in-memory not yet synced)
                if (dbManager != null) {
                    try {
                        DatabaseManager.User dbu = dbManager.authenticateUser(emailInput, uname, pass);
                        if (dbu != null) {
                            // map DB user to local User and proceed
                            User mapped = new User(dbu.username, dbu.email, dbu.password);
                            // ensure in-memory maps contain this user
                            usersByEmail.put(mapped.email.toLowerCase(), mapped);
                            usersByUsername.put(mapped.username.toLowerCase(), mapped);
                            afterLogin(mapped);
                            updateAuthArea(homeAuthArea);
                            return;
                        }
                    } catch (Exception ex) {
                        System.err.println("DB authenticate failed: " + ex.getMessage());
                    }
                }
                System.out.println("Login failed: no user found for email=" + emailInput);
                JOptionPane.showMessageDialog(this, "No account found for this email.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            System.out.println("Found user: username='" + byEmail.username + "' storedEmail='" + byEmail.email + "'");
            if (byEmail.password == null) {
                System.out.println("Warning: loaded user has null password for email=" + byEmail.email);
            }
            // Primary validation: email + password must match (password stored plainly in DB in this app)
            if (!byEmail.password.equals(pass)) {
                System.out.println("Login failed: incorrect password for email=" + emailInput);
                JOptionPane.showMessageDialog(this, "Incorrect password.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            // If username field was provided but doesn't match stored username, show a non-blocking warning but allow login
            if (!uname.isEmpty() && !byEmail.username.equalsIgnoreCase(uname)) {
                JOptionPane.showMessageDialog(this, "Warning: provided username does not match account username. Logging in using email+password.", "Notice", JOptionPane.WARNING_MESSAGE);
            }
            currentRole = "USER";
            afterLogin(byEmail);
            updateAuthArea(homeAuthArea);
        };

        submit.addActionListener(e -> doLogin.run());
        pField.addActionListener(e -> doLogin.run());
        content.add(card);
        root.add(content, BorderLayout.CENTER);
        return root;
    }

    private JPanel createSignUpPage() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(new Color(15, 20, 35));
        root.setOpaque(true);
        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        JPanel card = new JPanel(new GridBagLayout());
        card.setPreferredSize(new Dimension(520, 520));
        card.setBackground(new Color(25, 30, 45));
        card.setBorder(BorderFactory.createLineBorder(new Color(0, 120, 255), 2));
        card.setOpaque(true);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(12, 16, 12, 16);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        JLabel title = new JLabel("Sign Up", SwingConstants.CENTER);
        title.setForeground(new Color(0, 160, 255));
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        card.add(title, gbc);
        gbc.gridy++;
        gbc.gridwidth = 1;
        JLabel uLabel = fieldLabel("Username");
        JTextField uField = new JTextField(20);
        styleTextField(uField);
        card.add(uLabel, gbc);
        gbc.gridx = 1;
        card.add(uField, gbc);
        gbc.gridy++;
        gbc.gridx = 0;
        JLabel eLabel = fieldLabel("Email");
        JTextField eField = new JTextField(20);
        styleTextField(eField);
        card.add(eLabel, gbc);
        gbc.gridx = 1;
        card.add(eField, gbc);
        gbc.gridy++;
        gbc.gridx = 0;
        JLabel pLabel = fieldLabel("Password");
        JPasswordField pField = new JPasswordField(20);
        stylePasswordField(pField);
        card.add(pLabel, gbc);
        gbc.gridx = 1;
        card.add(pField, gbc);
        gbc.gridy++;
        gbc.gridx = 0;
        JLabel cLabel = fieldLabel("Confirm Password");
        JPasswordField cField = new JPasswordField(20);
        stylePasswordField(cField);
        card.add(cLabel, gbc);
        gbc.gridx = 1;
        card.add(cField, gbc);
        gbc.gridy++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        JButton submit = new JButton("Submit");
        submit.setPreferredSize(new Dimension(220, 40));
        submit.setBackground(new Color(24, 120, 240));
        submit.setForeground(Color.WHITE);
        submit.setOpaque(true);
        submit.setBorderPainted(false);
        submit.setFocusPainted(false);
        submit.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.add(submit, gbc);
        gbc.gridy++;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel toLogin = linkLabel("Already have an account? Login");
        toLogin.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                cardLayout.show(mainPanel, "Login");
            }
        });
        card.add(toLogin, gbc);
        Runnable doSignup = () -> {
            String uname = uField.getText().trim();
            String email = eField.getText().trim().toLowerCase();
            String pass = new String(pField.getPassword());
            String conf = new String(cField.getPassword());
            if (uname.isEmpty() || email.isEmpty() || pass.isEmpty() || conf.isEmpty()) {
                JOptionPane.showMessageDialog(this, "All fields are required.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (!email.contains("@") || !email.contains(".")) {
                JOptionPane.showMessageDialog(this, "Enter a valid email.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (!pass.equals(conf)) {
                JOptionPane.showMessageDialog(this, "Passwords do not match.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            // If email already exists, offer to update the existing account instead of failing
            if (usersByEmail.containsKey(email)) {
                // navigate to the Update Account page and prefill fields
                User existing = usersByEmail.get(email);
                // Store prefill values in a small state holder (use instance fields)
                updateAccountEmailPrefill = email;
                updateAccountUsernamePrefill = existing.username;
                updateAccountPasswordPrefill = existing.password;
                cardLayout.show(mainPanel, "UpdateAccount");
                return;
            }
            if (usersByUsername.containsKey(uname.toLowerCase())) {
                JOptionPane.showMessageDialog(this, "Username already taken.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            User u = new User(uname, email, pass);
            registerUser(u);
            JOptionPane.showMessageDialog(this, "Registration successful! Please login.");
            adminDashboardPanel.refreshUsersTable(); // Refresh the table after a new user is added
            cardLayout.show(mainPanel, "Login");
        };
        submit.addActionListener(e -> doSignup.run());
        cField.addActionListener(e -> doSignup.run());
        content.add(card);
        root.add(content, BorderLayout.CENTER);
        return root;
    }

    private JPanel createUpdateAccountPage() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(new Color(15, 20, 35));
        root.setOpaque(true);
        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        JPanel card = new JPanel(new GridBagLayout());
        card.setPreferredSize(new Dimension(520, 420));
        card.setBackground(new Color(25, 30, 45));
        card.setBorder(BorderFactory.createLineBorder(new Color(0, 120, 255), 2));
        card.setOpaque(true);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(12, 16, 12, 16);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        JLabel title = new JLabel("Update Account", SwingConstants.CENTER);
        title.setForeground(new Color(0, 160, 255));
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        card.add(title, gbc);
        gbc.gridy++;
        gbc.gridwidth = 1;

        JLabel eLabel = fieldLabel("Email (cannot change)");
        JTextField eField = new JTextField(20);
        eField.setEditable(false);
        styleTextField(eField);
        card.add(eLabel, gbc);
        gbc.gridx = 1;
        card.add(eField, gbc);
        gbc.gridy++;
        gbc.gridx = 0;

        JLabel uLabel = fieldLabel("Username");
        JTextField uField = new JTextField(20);
        styleTextField(uField);
        card.add(uLabel, gbc);
        gbc.gridx = 1;
        card.add(uField, gbc);
        gbc.gridy++;
        gbc.gridx = 0;

        JLabel pLabel = fieldLabel("New Password");
        JPasswordField pField = new JPasswordField(20);
        stylePasswordField(pField);
        card.add(pLabel, gbc);
        gbc.gridx = 1;
        card.add(pField, gbc);
        gbc.gridy++;
        gbc.gridx = 0;

        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        JButton update = new JButton("Update Account");
        update.setPreferredSize(new Dimension(220, 40));
        update.setBackground(new Color(24, 120, 240));
        update.setForeground(Color.WHITE);
        update.setOpaque(true);
        update.setBorderPainted(false);
        update.setFocusPainted(false);
        update.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.add(update, gbc);

        gbc.gridy++;
        JLabel toLogin = linkLabel("Back to Login");
        toLogin.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                cardLayout.show(mainPanel, "Login");
            }
        });
        card.add(toLogin, gbc);

        // Prefill when the panel is shown using a ComponentListener
        root.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                if (updateAccountEmailPrefill != null) eField.setText(updateAccountEmailPrefill);
                if (updateAccountUsernamePrefill != null) uField.setText(updateAccountUsernamePrefill);
                if (updateAccountPasswordPrefill != null) pField.setText(updateAccountPasswordPrefill);
            }
        });

        Runnable doUpdate = () -> {
            String email = eField.getText().trim().toLowerCase();
            String uname = uField.getText().trim();
            String pass = new String(pField.getPassword());
            if (email.isEmpty() || uname.isEmpty() || pass.isEmpty()) {
                JOptionPane.showMessageDialog(this, "All fields are required.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            // Check username collision
            User existing = usersByEmail.get(email);
            if (existing == null) {
                JOptionPane.showMessageDialog(this, "No account found for this email.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (!existing.username.equalsIgnoreCase(uname) && usersByUsername.containsKey(uname.toLowerCase())) {
                JOptionPane.showMessageDialog(this, "Username already taken.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            // Persist to DB
            if (dbManager != null) {
                try {
                    dbManager.updateUser(email, uname, pass);
                } catch (Exception ex) {
                    System.err.println("Failed to update user in DB: " + ex.getMessage());
                    JOptionPane.showMessageDialog(this, "Failed to update account in database.", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }
            // Update in-memory maps
            usersByUsername.remove(existing.username.toLowerCase());
            existing.username = uname;
            existing.password = pass;
            usersByUsername.put(uname.toLowerCase(), existing);
            JOptionPane.showMessageDialog(this, "Account updated. Please login.");
            adminDashboardPanel.refreshUsersTable();
            cardLayout.show(mainPanel, "Login");
        };

        update.addActionListener(e -> doUpdate.run());
        pField.addActionListener(e -> doUpdate.run());
        content.add(card);
        root.add(content, BorderLayout.CENTER);
        return root;
    }

    private void registerUser(User u) {
        // persist to DB if available
        if (dbManager != null) {
            try {
                dbManager.registerUser(u.username, u.email, u.password);
            } catch (Exception ex) {
                System.err.println("Failed to persist user to DB: " + ex.getMessage());
            }
        }
        usersByEmail.put(u.email.toLowerCase(), u);
        usersByUsername.put(u.username.toLowerCase(), u);
    }

    public void addListing(Listing listing) {
        System.out.println("addListing invoked: itemName='" + listing.itemName + "' reporter='" + listing.reporterEmail + "' date='" + listing.reportedAt + "'");
        // persist to DB if available
        if (dbManager != null) {
            try {
                dbManager.addListing(listing.reporterEmail, listing.itemName, listing.category, listing.description, listing.reportedAt, listing.location, listing.status, listing.contactInfo, listing.imagePath);
                // reload listings from the database so we show the DB-backed rows (with correct ids)
                loadFromDatabase();
                System.out.println("After loadFromDatabase, total listings=" + allListings.size());
                for (Listing l : allListings) System.out.println(String.format("listing id=%d email=%s name=%s date=%s", l.id, l.reporterEmail, l.itemName, l.reportedAt));
                // attempt to find and auto-select the newly added listing
                for (Listing l : allListings) {
                    if (l.reporterEmail != null && l.reporterEmail.equals(listing.reporterEmail)
                            && l.itemName != null && l.itemName.equals(listing.itemName)
                            && l.reportedAt != null && l.reportedAt.equals(listing.reportedAt)) {
                        if (viewAllListingsPanel != null) viewAllListingsPanel.selectListingById(l.id);
                        break;
                    }
                }
            } catch (Exception ex) {
                System.err.println("Failed to persist listing to DB: " + ex.getMessage());
                // fall back to in-memory list so UI still updates
                allListings.add(listing);
                viewAllListingsPanel.refreshListings();
                adminDashboardPanel.refreshListingsTable();
            }
        } else {
            // no DB: keep previous behavior
            allListings.add(listing);
            viewAllListingsPanel.refreshListings();
            adminDashboardPanel.refreshListingsTable();
        }
    }

    // Public helper so child panels can force a reload from the canonical DB source.
    public void reloadListingsFromDB() {
        loadFromDatabase();
    }

    private void loadFromDatabase() {
        if (dbManager == null) return;
        try {
            // Ensure any listings already marked Verified in the DB are migrated to verified_reports
            try { dbManager.moveVerifiedListingsToReports(); } catch (Exception ex) { System.err.println("Warning: moveVerifiedListingsToReports failed: " + ex.getMessage()); }
            List<DatabaseManager.User> users = dbManager.getAllUsers();
            usersByEmail.clear();
            usersByUsername.clear();
            for (DatabaseManager.User u : users) {
                User nu = new User(u.username, u.email, u.password);
                usersByEmail.put(nu.email.toLowerCase(), nu);
                usersByUsername.put(nu.username.toLowerCase(), nu);
            }

            // Ensure passwords are populated from the DB (some DatabaseManager versions may omit password)
            syncUserPasswordsFromDB();

            allListings.clear();
            List<DatabaseManager.Listing>listings = dbManager.getAllListings();
            for (DatabaseManager.Listing l : listings) {
                // Skip entries already marked as Verified so they show only in the Verified Reports tab
                String s = l.status != null ? l.status.trim() : "";
                if (s.equalsIgnoreCase("verified") || s.equalsIgnoreCase("resolved")) {
                    continue;
                }
                Listing nl = new Listing(l.id, l.email, l.name, l.category, l.description, l.date, l.location, l.status, l.contactInfo, l.imagePath);
                allListings.add(nl);
            }

            verifiedReports.clear();
            List<DatabaseManager.VerifiedReport> reports = dbManager.getVerifiedReports();
            for (DatabaseManager.VerifiedReport r : reports) {
                // Admin table columns: ID, Item, Status, Email, Contact, Date, Delete, Send Mail
                Object id = r.id > 0 ? r.id : (r.listingId > 0 ? r.listingId : "");
                Object item = r.itemName != null ? r.itemName : "";
                Object status = r.status != null ? r.status : "";
                Object email = r.email != null ? r.email : "";
                Object contact = r.contactInfo != null ? r.contactInfo : "";
                Object date = r.dateTime != null ? r.dateTime : r.resolvedAt != null ? r.resolvedAt : "";
                verifiedReports.add(new Object[]{id, item, status, email, contact, date, "Delete", "Send"});
            }

            if (viewAllListingsPanel != null) viewAllListingsPanel.refreshListings();
            if (adminDashboardPanel != null) {
                adminDashboardPanel.refreshListingsTable();
                adminDashboardPanel.refreshUsersTable();
                // Ensure verified reports are refreshed so verified items move to the Verified Reports tab
                adminDashboardPanel.refreshReportsTable();
            }
        } catch (Exception ex) {
            System.err.println("Error loading from DB: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    // Debug helper: print simple counts from DB to console
    private void showDBSummary() {
        if (dbManager == null) return;
        try {
            DatabaseManager.DashboardStats s = dbManager.getDashboardStats();
            System.out.println("DB Summary: listings=" + s.pendingReports + ", verified=" + s.resolvedCases + ", users=" + s.totalUsers);
        } catch (Exception ex) {
            System.err.println("Failed to fetch DB summary: " + ex.getMessage());
        }
    }

    // In case DatabaseManager.getAllUsers() does not populate passwords, fetch them directly using JDBC
    private void syncUserPasswordsFromDB() {
        if (dbManager == null) return;
        try {
            // Use the existing DatabaseManager API rather than trying to open a separate MySQL connection.
            // This ensures the app works when DatabaseManager is using the SQLite fallback.
            List<DatabaseManager.User> dbUsers = dbManager.getAllUsers();
            int updated = 0;
            for (DatabaseManager.User du : dbUsers) {
                if (du == null || du.email == null) continue;
                User local = usersByEmail.get(du.email.toLowerCase());
                if (local != null && (local.password == null || local.password.isEmpty()) && du.password != null) {
                    local.password = du.password;
                    updated++;
                }
            }
            System.out.println("syncUserPasswordsFromDB: updated passwords for " + updated + " users.");
        } catch (Exception ex) {
            System.err.println("Failed to sync passwords from DatabaseManager: " + ex.getMessage());
        }
    }

    // Print listings and verified reports for debugging
    private void showDBDetails() {
        if (dbManager == null) return;
        try {
            System.out.println("--- DB Listings ---");
            // Query and pretty-print listings via DatabaseManager.runQuery
            try {
                java.util.List<java.util.Map<String,Object>> rows = dbManager.runQuery("SELECT id, reporter_email AS email, item_name AS item, category, report_date AS date, status, contact_info AS contact, image_path AS image FROM listings");
                prettyPrintRowMaps(rows);
            } catch (Exception ignore) {
                List<DatabaseManager.Listing> listings = dbManager.getAllListings();
                for (DatabaseManager.Listing l : listings) {
                    System.out.println(String.format("id=%d email=%s item=%s category=%s date=%s status=%s contact=%s image=%s", l.id, l.email, l.name, l.category, l.date, l.status, l.contactInfo, l.imagePath));
                }
            }

            System.out.println("--- DB Verified Reports ---");
            try {
                java.util.List<java.util.Map<String,Object>> rows2 = dbManager.runQuery("SELECT id, listing_id, item_name, status, email, datetime AS date, resolved_at AS resolved, admin_notes AS notes FROM verified_reports");
                prettyPrintRowMaps(rows2);
            } catch (Exception ignore) {
                List<DatabaseManager.VerifiedReport> reports = dbManager.getVerifiedReports();
                for (DatabaseManager.VerifiedReport r : reports) {
                    System.out.println(String.format("id=%d listing_id=%d item=%s status=%s email=%s date=%s resolved=%s notes=%s", r.id, r.listingId, r.itemName, r.status, r.email, r.dateTime, r.resolvedAt, r.adminNotes));
                }
            }
        } catch (Exception ex) {
            System.err.println("Failed to fetch DB details: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    // Helper: pretty-print a JDBC ResultSet with simple column width calculation and truncation
    private void prettyPrintResultSet(java.sql.ResultSet rs) throws java.sql.SQLException {
        java.sql.ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        String[] headers = new String[cols];
        int[] widths = new int[cols];
        for (int i = 0; i < cols; i++) {
            headers[i] = md.getColumnLabel(i + 1);
            widths[i] = Math.min(headers[i].length(), 30);
        }
        while (rs.next()) {
            String[] row = new String[cols];
            for (int i = 0; i < cols; i++) {
                Object o = rs.getObject(i + 1);
                String s = o == null ? "" : o.toString();
                if (s.length() > 60) s = s.substring(0, 57) + "...";
                row[i] = s;
                widths[i] = Math.min(Math.max(widths[i], Math.min(s.length(), 60)), 60);
            }
            rows.add(row);
        }
        // adjust widths to at least header size
        for (int i = 0; i < cols; i++) widths[i] = Math.max(widths[i], Math.min(headers[i].length(), 60));

        // print header
        StringBuilder fmt = new StringBuilder();
        for (int w : widths) fmt.append("%-").append(w + 2).append("s");
        String format = fmt.toString();
        System.out.println(String.format(format, (Object[]) headers));
        // separator
        StringBuilder sep = new StringBuilder();
        for (int w : widths) {
            for (int k = 0; k < w + 2; k++) sep.append('-');
        }
        System.out.println(sep.toString());
        // rows
        for (String[] r : rows) {
            Object[] cells = new Object[cols];
            for (int i = 0; i < cols; i++) cells[i] = r[i];
            System.out.println(String.format(format, cells));
        }
        if (rows.isEmpty()) System.out.println("(no rows)");
    }

    private void prettyPrintRowMaps(java.util.List<java.util.Map<String,Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            System.out.println("(no rows)");
            return;
        }
        java.util.List<String> cols = new java.util.ArrayList<>(rows.get(0).keySet());
        int ccount = cols.size();
        int[] widths = new int[ccount];
        for (int i = 0; i < ccount; i++) widths[i] = Math.min(cols.get(i).length(), 30);
        java.util.List<String[]> outRows = new java.util.ArrayList<>();
        for (java.util.Map<String,Object> r : rows) {
            String[] cells = new String[ccount];
            for (int i = 0; i < ccount; i++) {
                Object v = r.get(cols.get(i));
                String s = v == null ? "" : v.toString();
                if (s.length() > 60) s = s.substring(0, 57) + "...";
                cells[i] = s;
                widths[i] = Math.max(widths[i], Math.min(s.length(), 60));
            }
            outRows.add(cells);
        }
        // header
        StringBuilder fmtb = new StringBuilder();
        for (int w : widths) fmtb.append("%-").append(w + 2).append("s");
        String fmt = fmtb.toString();
    System.out.println(String.format(fmt, (Object[]) cols.toArray(new String[0])));
        StringBuilder sep = new StringBuilder();
        for (int w : widths) for (int k = 0; k < w + 2; k++) sep.append('-');
        System.out.println(sep.toString());
        for (String[] r : outRows) System.out.println(String.format(fmt, (Object[]) r));
    }

    private JButton createNavButton(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif", Font.PLAIN, 14));
        b.setForeground(new Color(200, 205, 210));
        b.setBackground(new Color(18, 24, 35));
        b.setOpaque(true);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setPreferredSize(new Dimension(130, 38));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private JButton createActionButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(new Color(52, 152, 219));
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setBorderPainted(false);
        button.setPreferredSize(new Dimension(120, 36));
        button.setFont(new Font("SansSerif", Font.BOLD, 14));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private JButton createOutlineButton(String text) {
        JButton button = new JButton(text);
        button.setBorder(BorderFactory.createLineBorder(new Color(52, 152, 219), 2));
        button.setContentAreaFilled(false);
        button.setForeground(new Color(52, 152, 219));
        button.setFocusPainted(false);
        button.setPreferredSize(new Dimension(120, 36));
        button.setFont(new Font("SansSerif", Font.BOLD, 14));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private JLabel linkLabel(String text) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setForeground(new Color(0, 160, 255));
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return l;
    }

    private JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Color.WHITE);
        l.setFont(new Font("SansSerif", Font.PLAIN, 14));
        return l;
    }

    private void styleTextField(JTextField tf) {
        tf.setBackground(new Color(34, 40, 49));
        tf.setForeground(Color.WHITE);
        tf.setCaretColor(Color.WHITE);
        tf.setBorder(BorderFactory.createLineBorder(new Color(80, 90, 105)));
    }

    private void stylePasswordField(JPasswordField pf) {
        pf.setBackground(new Color(34, 40, 49));
        pf.setForeground(Color.WHITE);
        pf.setCaretColor(Color.WHITE);
        pf.setBorder(BorderFactory.createLineBorder(new Color(80, 90, 105)));
    }

    // ==== Nested Classes and Data Models ====
   
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
            } catch (Exception ignored) {
            }
            new MainApplication().setVisible(true);
        });
    }
   
    // =====================================
    // All original nested classes start here
    // =====================================

    // Renamed `AdminDashboard` to an inner class `AdminDashboardPanel` to avoid conflicts
    class AdminDashboard extends JPanel {
        // Custom header renderer for blue background and white text
        private static class BlueHeaderRenderer extends DefaultTableCellRenderer {
            public BlueHeaderRenderer() {
                setOpaque(true);
                setBackground(new Color(24, 120, 240));
                setForeground(Color.WHITE);
                setHorizontalAlignment(CENTER);
                setFont(new Font("Segoe UI", Font.BOLD, 14));
            }
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setBackground(new Color(24, 120, 240));
                setForeground(Color.WHITE);
                setFont(new Font("Segoe UI", Font.BOLD, 14));
                return this;
            }
        }
        // Theme colors
        private final Color PRIMARY_COLOR = new Color(33, 37, 41);
        private final Color ACCENT_COLOR = new Color(0, 123, 255);
        private final Color SIDEBAR_COLOR = new Color(52, 58, 64);
        private final Font BOLD_FONT = new Font("Segoe UI", Font.BOLD, 14);
        // Tables and models
        private JTable usersTable, listingsTable, verifiedReportsTable;
        private DefaultTableModel usersModel, listingsModel, verifiedReportsModel;
        // Dashboard labels
        private JLabel totalReportsLabel, resolvedCasesLabel, pendingReportsLabel;
        private int reportIdCounter = 1;

        public AdminDashboard() {
            setLayout(new BorderLayout());
            setBackground(new Color(18, 24, 35));

            JPanel sidebar = new JPanel();
            sidebar.setBackground(SIDEBAR_COLOR);
            sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
            sidebar.setPreferredSize(new Dimension(220, 700));

            JLabel logo = new JLabel("Back2U Admin", SwingConstants.CENTER);
            logo.setFont(new Font("Segoe UI", Font.BOLD, 18));
            logo.setForeground(Color.WHITE);
            logo.setBorder(new EmptyBorder(20, 0, 20, 0));
            sidebar.add(logo);

            JButton dashboardBtn = createSidebarButton("Dashboard");
            JButton usersBtn = createSidebarButton("Manage Users");
            JButton listingsBtn = createSidebarButton("Manage Listings");
            JButton reportsBtn = createSidebarButton("Verified Reports");

            sidebar.add(dashboardBtn);
            sidebar.add(usersBtn);
            sidebar.add(listingsBtn);
            sidebar.add(reportsBtn);

            JPanel contentPanel = new JPanel(new CardLayout());
            JPanel dashboardPanel = createDashboardPanel();
            JPanel usersPanel = createUsersPanel();
            JPanel listingsPanel = createListingsPanel();
            JPanel reportsPanel = createReportsPanel();

            contentPanel.add(dashboardPanel, "Dashboard");
            contentPanel.add(usersPanel, "Manage Users");
            contentPanel.add(listingsPanel, "Manage Listings");
            contentPanel.add(reportsPanel, "Verified Reports");

            CardLayout cl = (CardLayout) contentPanel.getLayout();
            dashboardBtn.addActionListener(e -> {
                updateDashboardCounts();
                cl.show(contentPanel, "Dashboard");
            });
            usersBtn.addActionListener(e -> cl.show(contentPanel, "Manage Users"));
            listingsBtn.addActionListener(e -> cl.show(contentPanel, "Manage Listings"));
            reportsBtn.addActionListener(e -> cl.show(contentPanel, "Verified Reports"));

            add(sidebar, BorderLayout.WEST);
            add(contentPanel, BorderLayout.CENTER);
        }

        private JButton createSidebarButton(String text) {
            JButton button = new JButton(text);
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
            button.setBackground(SIDEBAR_COLOR);
            button.setForeground(Color.WHITE);
            button.setFocusPainted(false);
            button.setFont(new Font("Segoe UI", Font.BOLD, 14));
            button.setHorizontalAlignment(SwingConstants.LEFT);
            button.setBorder(new EmptyBorder(0, 20, 0, 0));
            button.setCursor(new Cursor(Cursor.HAND_CURSOR));
            button.addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent evt) {
                    button.setBackground(ACCENT_COLOR);
                }
                public void mouseExited(MouseEvent evt) {
                    button.setBackground(SIDEBAR_COLOR);
                }
            });
            return button;
        }

        private JPanel createDashboardPanel() {
            JPanel panel = new JPanel(new GridLayout(1, 3, 20, 20));
            panel.setBorder(new EmptyBorder(30, 30, 30, 30));
            panel.setBackground(PRIMARY_COLOR);
            totalReportsLabel = createDashboardCard("Total Reports", "0", new Color(0, 123, 255));
            resolvedCasesLabel = createDashboardCard("Resolved Cases", "0", new Color(40, 167, 69));
            pendingReportsLabel = createDashboardCard("Pending Reports", "0", new Color(220, 53, 69));
            panel.add(totalReportsLabel);
            panel.add(resolvedCasesLabel);
            panel.add(pendingReportsLabel);
            return panel;
        }

        private JLabel createDashboardCard(String title, String value, Color color) {
            JLabel label = new JLabel("<html><center>" + title + "<br><span style='font-size:24px;'>" + value + "</span></center></html>", SwingConstants.CENTER);
            label.setOpaque(true);
            label.setBackground(color);
            label.setForeground(Color.WHITE);
            label.setFont(new Font("Segoe UI", Font.BOLD, 16));
            label.setBorder(new EmptyBorder(20, 20, 20, 20));
            return label;
        }

        private JPanel createUsersPanel() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBorder(new EmptyBorder(20, 20, 20, 20));
            panel.setBackground(PRIMARY_COLOR);
            JLabel header = new JLabel("Manage Users");
            header.setFont(new Font("Segoe UI", Font.BOLD, 18));
            header.setForeground(Color.WHITE);
            panel.add(header, BorderLayout.NORTH);
            String[] columnNames = {"Username", "Email", "Actions"};
            usersModel = new DefaultTableModel(columnNames, 0);
            usersTable = new JTable(usersModel);
            usersTable.setBackground(new Color(60, 63, 65));
            usersTable.setForeground(Color.WHITE);
            usersTable.setSelectionBackground(ACCENT_COLOR);
            usersTable.setFillsViewportHeight(true);
            usersTable.setRowHeight(32); // Make rows bigger
            JTableHeader headerTable = usersTable.getTableHeader();
            headerTable.setDefaultRenderer(new BlueHeaderRenderer());
            refreshUsersTable();
            usersTable.getColumn("Actions").setCellRenderer(new ActionsRenderer());
            usersTable.getColumn("Actions").setCellEditor(new ActionsEditor(new JCheckBox(), usersModel));
            panel.add(new JScrollPane(usersTable), BorderLayout.CENTER);
            return panel;
        }

        private JPanel createListingsPanel() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBorder(new EmptyBorder(20, 20, 20, 20));
            panel.setBackground(PRIMARY_COLOR);
            JLabel header = new JLabel("Manage Listings");
            header.setFont(new Font("Segoe UI", Font.BOLD, 18));
            header.setForeground(Color.WHITE);
            panel.add(header, BorderLayout.NORTH);
            String[] columnNames = {"Email", "Item", "Description", "Date", "Status", "Actions"};
            listingsModel = new DefaultTableModel(columnNames, 0);
            listingsTable = new JTable(listingsModel);
            listingsTable.setBackground(new Color(60, 63, 65));
            listingsTable.setForeground(Color.WHITE);
            listingsTable.setSelectionBackground(ACCENT_COLOR);
            listingsTable.setFillsViewportHeight(true);
            listingsTable.setRowHeight(32); // Make rows bigger
            JTableHeader headerTable = listingsTable.getTableHeader();
            headerTable.setDefaultRenderer(new BlueHeaderRenderer());
            refreshListingsTable();
            listingsTable.getColumn("Status").setCellRenderer(new StatusRenderer());
            listingsTable.getColumn("Actions").setCellRenderer(new ActionsRenderer());
            listingsTable.getColumn("Actions").setCellEditor(new ActionsEditor(new JCheckBox(), listingsModel));
            panel.add(new JScrollPane(listingsTable), BorderLayout.CENTER);
            return panel;
        }

        private void refreshListingsTable() {
            listingsModel.setRowCount(0);
            for (Listing listing : allListings) {
                listingsModel.addRow(new Object[]{listing.reporterEmail, listing.itemName, listing.description, listing.reportedAt, listing.status, ""});
            }
            updateDashboardCounts();
        }

        private void refreshUsersTable() {
            usersModel.setRowCount(0);
            for (User user : usersByEmail.values()) {
                if (!"admin".equalsIgnoreCase(user.username)) {
                    usersModel.addRow(new Object[]{user.username, user.email, ""});
                }
            }
        }

        private void refreshReportsTable() {
            verifiedReportsModel.setRowCount(0);
            for (Object[] report : verifiedReports) {
                verifiedReportsModel.addRow(report);
            }
        }

        private JPanel createReportsPanel() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBorder(new EmptyBorder(20, 20, 20, 20));
            panel.setBackground(PRIMARY_COLOR);
            JLabel header = new JLabel("Verified Reports (Resolved Cases)");
            header.setFont(new Font("Segoe UI", Font.BOLD, 18));
            header.setForeground(Color.WHITE);
            panel.add(header, BorderLayout.NORTH);
            String[] columnNames = {"ID", "Item", "Status", "Email", "Contact", "Date", "Delete", "Send Mail"};
            verifiedReportsModel = new DefaultTableModel(columnNames, 0);
            verifiedReportsTable = new JTable(verifiedReportsModel);
            verifiedReportsTable.setBackground(new Color(60, 63, 65));
            verifiedReportsTable.setForeground(Color.WHITE);
            verifiedReportsTable.setSelectionBackground(ACCENT_COLOR);
            verifiedReportsTable.setFillsViewportHeight(true);
            verifiedReportsTable.setRowHeight(32); // Make rows bigger
            JTableHeader headerTable = verifiedReportsTable.getTableHeader();
            headerTable.setDefaultRenderer(new BlueHeaderRenderer());
            verifiedReportsTable.getColumn("Delete").setCellRenderer(new ButtonRenderer("Delete"));
            verifiedReportsTable.getColumn("Delete").setCellEditor(new ButtonEditor(new JCheckBox(), "Delete"));
            verifiedReportsTable.getColumn("Send Mail").setCellRenderer(new ButtonRenderer("Send"));
            verifiedReportsTable.getColumn("Send Mail").setCellEditor(new ButtonEditor(new JCheckBox(), "Send"));
            panel.add(new JScrollPane(verifiedReportsTable), BorderLayout.CENTER);
            return panel;
        }

        public void updateDashboardCounts() {
            int resolved = verifiedReports.size();
            int pending = allListings.size();
            int total = resolved + pending;
            totalReportsLabel.setText("<html><center>Total Reports<br><span style='font-size:24px;'>" + total + "</span></center></html>");
            resolvedCasesLabel.setText("<html><center>Resolved Cases<br><span style='font-size:24px;'>" + resolved + "</span></center></html>");
            pendingReportsLabel.setText("<html><center>Pending Reports<br><span style='font-size:24px;'>" + pending + "</span></center></html>");
        }
       
        class ActionsRenderer extends JPanel implements TableCellRenderer {
            public ActionsRenderer() {
                setLayout(new FlowLayout(FlowLayout.CENTER, 5, 0));
                setBackground(new Color(60, 63, 65));
            }
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                removeAll();
                if (table.getModel() == usersModel) {
                    add(createButton("Delete", new Color(220, 53, 69)));
                    add(createButton("Mail", ACCENT_COLOR));
                } else if (table.getModel() == listingsModel) {
                    add(createButton("Verify", new Color(40, 167, 69)));
                    add(createButton("Delete", new Color(220, 53, 69)));
                }
                return this;
            }
            private JButton createButton(String text, Color color) {
                JButton button = new JButton(text);
                button.setBackground(color);
                button.setForeground(Color.WHITE);
                button.setFont(new Font("Segoe UI", Font.BOLD, 12));
                return button;
            }
        }
        class ActionsEditor extends DefaultCellEditor {
            private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
            private int currentRow;
            private final DefaultTableModel currentModel;
            private final JButton deleteButton = new JButton("Delete");
            private final JButton verifyButton = new JButton("Verify");
            private final JButton mailButton = new JButton("Mail");
            public ActionsEditor(JCheckBox checkBox, DefaultTableModel model) {
                super(checkBox);
                this.currentModel = model;
                styleButton(deleteButton, new Color(220, 53, 69));
                styleButton(verifyButton, new Color(40, 167, 69));
                styleButton(mailButton, ACCENT_COLOR);
                deleteButton.addActionListener(e -> {
                    if (currentRow >= 0 && currentRow < currentModel.getRowCount()) {
                        if (currentModel == usersModel) {
                            String email = (String) currentModel.getValueAt(currentRow, 1);
                            if (dbManager != null) {
                                try {
                                    dbManager.deleteUser(email);
                                } catch (SQLException ex) {
                                    System.err.println("Failed to delete user in DB: " + ex.getMessage());
                                }
                            }
                            // reload canonical data from DB to stay consistent
                            loadFromDatabase();
                        } else if (currentModel == listingsModel) {
                            Listing l = null;
                            try { l = allListings.get(currentRow); } catch (Exception ex) { l = null; }
                            if (dbManager != null && l != null) {
                                try {
                                    dbManager.deleteListing(l.id);
                                } catch (SQLException ex) {
                                    System.err.println("Failed to delete listing in DB: " + ex.getMessage());
                                }
                            }
                            // reload canonical data from DB so the UI and counts reflect DB state
                            loadFromDatabase();
                        }
                    }
                    fireEditingStopped();
                });
                verifyButton.addActionListener(e -> {
                    if (currentRow >= 0 && currentRow < currentModel.getRowCount() && currentModel == listingsModel) {
                        Listing listing = allListings.remove(currentRow);
                        if (dbManager != null && listing != null) {
                            try {
                                dbManager.verifyListing(listing.id, "Verified via admin UI");
                                // reload verified reports from DB
                                loadFromDatabase();
                            } catch (SQLException ex) {
                                System.err.println("Failed to verify listing in DB: " + ex.getMessage());
                            }
                            } else if (listing != null) {
                            verifiedReports.add(new Object[]{
                                reportIdCounter++, listing.itemName, listing.status, listing.reporterEmail, listing.contactInfo, listing.reportedAt, "Delete", "Mail"
                            });
                            refreshListingsTable();
                            refreshReportsTable();
                        }
                    }
                    fireEditingStopped();
                });

mailButton.addActionListener(e -> {
    if (currentRow >= 0 && currentRow < currentModel.getRowCount()) {
        String email = (String) currentModel.getValueAt(currentRow, 1);
        openGmailDirect(email, "Report Update", "Your report has been verified successfully!");
    }
    fireEditingStopped();
});




                panel.add(verifyButton);
                panel.add(deleteButton);
                panel.add(mailButton);
            }
            @Override
            public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
                this.currentRow = row;
                panel.setBackground(new Color(60, 63, 65));
                if (currentModel == usersModel) {
                    verifyButton.setVisible(false);
                    deleteButton.setVisible(true);
                    mailButton.setVisible(true);
                } else if (currentModel == listingsModel) {
                    verifyButton.setVisible(true);
                    deleteButton.setVisible(true);
                    mailButton.setVisible(false);
                }
                return panel;
            }
            @Override
            public Object getCellEditorValue() {
                return "";
            }
            private void styleButton(JButton b, Color c) {
                b.setBackground(c);
                b.setForeground(Color.WHITE);
                b.setFont(new Font("Segoe UI", Font.BOLD, 12));
            }
        }
        class StatusRenderer extends DefaultTableCellRenderer {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String status = value.toString().toLowerCase();
                if (status.equals("lost")) label.setForeground(new Color(220, 53, 69));
                else if (status.equals("found")) label.setForeground(new Color(40, 167, 69));
                return label;
            }
        }
        class ButtonRenderer extends JButton implements TableCellRenderer {
            public ButtonRenderer(String text) {
                setText(text);
                setOpaque(true);
                setFont(BOLD_FONT);
                setForeground(Color.WHITE);
                if ("Delete".equals(text)) setBackground(new Color(220, 53, 69));
                else if ("Send".equals(text)) setBackground(ACCENT_COLOR);
            }
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                return this;
            }
        }
        class ButtonEditor extends DefaultCellEditor {
            protected JButton button;
            private String label;
            private boolean clicked;
            private int currentRow;
            public ButtonEditor(JCheckBox checkBox, String action) {
                super(checkBox);
                button = new JButton();
                button.setOpaque(true);
                button.setFont(BOLD_FONT);
                button.setForeground(Color.WHITE);
                if ("Delete".equals(action)) button.setBackground(new Color(220, 53, 69));
                else if ("Send".equals(action)) button.setBackground(ACCENT_COLOR);
                button.addActionListener(e -> {
                    if (action.equals("Delete")) {
                        int result = JOptionPane.showConfirmDialog(button,
                                "Are you sure you want to delete this report?",
                                "Confirm Delete", JOptionPane.YES_NO_OPTION);
                        if (result == JOptionPane.YES_OPTION) {
                            // Try to delete from DB if possible
                            Object idObj = null;
                            try {
                                idObj = verifiedReportsModel.getValueAt(currentRow, 0);
                            } catch (Exception ex) { idObj = null; }
                            boolean deleted = false;
                            if (dbManager != null && idObj != null) {
                                try {
                                    int id = -1;
                                    if (idObj instanceof Number) id = ((Number) idObj).intValue();
                                    else id = Integer.parseInt(idObj.toString());
                                    deleted = dbManager.deleteVerifiedReport(id);
                                } catch (Exception ex) {
                                    // ignore DB delete errors and fall back to UI-only
                                }
                            }
                            // Remove from UI model and in-memory list
                            try { verifiedReportsModel.removeRow(currentRow); } catch (Exception ignore) {}
                            try { verifiedReports.remove(currentRow); } catch (Exception ignore) {}
                            if (deleted) System.out.println("Deleted verified_report id=" + idObj);
                            updateDashboardCounts();
                        }
                    } else if (action.equals("Send")) {
                        String email = (String) verifiedReportsModel.getValueAt(currentRow, 3);
                        openGmailDirect(email, "Report Update", "Your report has been verified successfully!");
                    }
                    fireEditingStopped();
                });
            }
            @Override
            public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
                this.currentRow = row;
                label = (value == null) ? "" : value.toString();
                button.setText(label);
                clicked = true;
                return button;
            }
            @Override
            public Object getCellEditorValue() {
                if (clicked) {}
                clicked = false;
                return label;
            }
        }
    }
   
    // Remaining nested classes
    private static class User {
        String username, email, password;
        User(String u, String e, String p) {
            username = u; email = e; password = p;
        }
    }

    private static class Listing {
        int id;
        String reporterEmail;
        String itemName;
        String category;
        String description;
        String reportedAt;
        String location;
        String status;
        String imagePath;
        String contactInfo;

        public Listing() {}

        public Listing(int id, String reporterEmail, String itemName, String category, String description, String reportedAt, String location, String status, String contactInfo, String imagePath) {
            this.id = id;
            this.reporterEmail = reporterEmail;
            this.itemName = itemName;
            this.category = category;
            this.description = description;
            this.reportedAt = reportedAt;
            this.location = location;
            this.status = status;
            this.contactInfo = contactInfo;
            this.imagePath = imagePath;
        }
    }
   
    class ReportItemForm extends JPanel {
        private String uploadedFilePath = null;
        private JPanel panel;
        private MainApplication parentApp;
        public ReportItemForm(MainApplication parentApp) {
            this.parentApp = parentApp;
            setBackground(new Color(18, 24, 35));
            setLayout(new BorderLayout());
            panel = new JPanel(new GridBagLayout());
            panel.setBackground(new Color(0x0a0f17));
            panel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createEmptyBorder(10, 10, 10, 10),
                    BorderFactory.createLineBorder(new Color(0x3b4a6b), 1)
            ));
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(4, 8, 4, 8);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 0.5;
            JLabel title = new JLabel("Report Item", SwingConstants.CENTER);
            title.setFont(new Font("Inter", Font.BOLD, 22));
            title.setForeground(new Color(0x2f54eb));
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.gridwidth = 4;
            panel.add(title, gbc);
            gbc.gridwidth = 1;
            int row = 1;
            gbc.gridx = 0;
            gbc.gridy = row;
            panel.add(makeLabel("Item Name:"), gbc);
            gbc.gridx = 1;
            panel.add(makeLabel("Category:"), gbc);
            row++;
            gbc.gridx = 0;
            gbc.gridy = row;
            JTextField itemNameField = makeTextField(200, 30);
            itemNameField.setFont(new Font("Inter", Font.PLAIN, 16));
            panel.add(itemNameField, gbc);
            JLabel itemNameError = makeErrorLabel();
            gbc.gridy = row + 1;
            panel.add(itemNameError, gbc);
            gbc.gridx = 1;
            gbc.gridy = row;
            JComboBox<String> categoryBox = makeCombo(new String[]{
                "-- Select Category --", "Electronics", "Clothing", "Accessories", "Documents", "Others"});
            styleDarkCombo(categoryBox);
            panel.add(categoryBox, gbc);
            JLabel categoryError = makeErrorLabel();
            gbc.gridy = row + 1;
            panel.add(categoryError, gbc);
            row += 2;
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.gridwidth = 2;
            panel.add(makeLabel("Description:"), gbc);
            row++;
            JTextArea descriptionField = makeTextArea(500, 150);
            descriptionField.setFont(new Font("Inter", Font.PLAIN, 16));
            descriptionField.setSelectionStart(0);
            descriptionField.setSelectionEnd(descriptionField.getText().length());
            JPanel descPanel = new JPanel(new BorderLayout());
            descPanel.setBackground(new Color(0x0a0f17));
            descPanel.setPreferredSize(new Dimension(500, 150));
            descPanel.setMinimumSize(new Dimension(400, 100));
            descPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
            JScrollPane descriptionScroll = new JScrollPane(descriptionField);
            descriptionScroll.setBorder(BorderFactory.createLineBorder(new Color(0x3b4a6b)));
            descriptionScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            descPanel.add(descriptionScroll, BorderLayout.CENTER);
            gbc.gridy = row;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;
            gbc.weighty = 0.0;
            panel.add(descPanel, gbc);
            JLabel descriptionError = makeErrorLabel();
            row++;
            gbc.gridy = row;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weighty = 0;
            panel.add(descriptionError, gbc);
            gbc.gridwidth = 1;
            row += 2;
            gbc.gridx = 0;
            gbc.gridy = row;
            panel.add(makeLabel("Date:"), gbc);
            gbc.gridx = 1;
            panel.add(makeLabel("Time:"), gbc);
            row++;
            gbc.gridx = 0;
            gbc.gridy = row;
            JTextField dateField = makeTextField(200, 20);
            dateField.setEditable(false);
            JButton calendarBtn = new JButton("");
            calendarBtn.setMargin(new Insets(2, 5, 2, 5));
            styleButton(calendarBtn, false);
            JPanel datePanel = new JPanel(new BorderLayout(5, 0));
            datePanel.setBackground(new Color(0x0a0f17));
            datePanel.add(dateField, BorderLayout.CENTER);
            datePanel.add(calendarBtn, BorderLayout.EAST);
            panel.add(datePanel, gbc);
            JLabel dateError = makeErrorLabel();
            gbc.gridy = row + 1;
            panel.add(dateError, gbc);
            calendarBtn.addActionListener(e -> {
                JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Select Date", Dialog.ModalityType.MODELESS);
                dialog.setUndecorated(true);
                dialog.add(new AdvancedCalendarPanel(dateField, dialog));
                dialog.pack();
                Point location = calendarBtn.getLocationOnScreen();
                dialog.setLocation(location.x, location.y + calendarBtn.getHeight());
                dialog.setVisible(true);
            });
            gbc.gridx = 1;
            String[] hours = new String[12];
            for (int i = 0; i < 12; i++) hours[i] = String.format("%02d", i + 1);
            String[] minutes = new String[60];
            for (int i = 0; i < 60; i++) minutes[i] = String.format("%02d", i);
            String[] meridian = {"AM", "PM"};
            JComboBox<String> hourBox = makeCombo(hours);
            styleDarkCombo(hourBox);
            JComboBox<String> minuteBox = makeCombo(minutes);
            styleDarkCombo(minuteBox);
            JComboBox<String> ampmBox = makeCombo(meridian);
            styleDarkCombo(ampmBox);
            JPanel timePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            timePanel.setBackground(new Color(0x0a0f17));
            timePanel.add(hourBox);
            timePanel.add(minuteBox);
            timePanel.add(ampmBox);
            gbc.gridy = row;
            gbc.insets = new Insets(0, 8, 0, 8);
            panel.add(timePanel, gbc);
            JLabel timeError = makeErrorLabel();
            gbc.gridy = row + 1;
            panel.add(timeError, gbc);
            gbc.insets = new Insets(8, 8, 8, 8);
            row += 2;
            gbc.gridx = 0;
            gbc.gridy = row;
            panel.add(makeLabel("Location:"), gbc);
            gbc.gridx = 1;
            panel.add(makeLabel("Status:"), gbc);
            row++;
            gbc.gridx = 0;
            gbc.gridy = row;
            JTextField locationField = makeTextField(200, 30);
            locationField.setFont(new Font("Inter", Font.PLAIN, 16));
            panel.add(locationField, gbc);
            JLabel locationError = makeErrorLabel();
            gbc.gridy = row + 1;
            panel.add(locationError, gbc);
            gbc.gridx = 1;
            gbc.gridy = row;
            JComboBox<String> statusBox = makeCombo(new String[]{"-- Select Status --", "Lost", "Found"});
            styleDarkCombo(statusBox);
            panel.add(statusBox, gbc);
            JLabel statusError = makeErrorLabel();
            gbc.gridy = row + 1;
            panel.add(statusError, gbc);
            row += 2;
            gbc.gridx = 0;
            gbc.gridy = row;
            panel.add(makeLabel("Contact Info:"), gbc);
            gbc.gridx = 1;
            JTextField contactField = makeTextField(200, 30);
            contactField.setFont(new Font("Inter", Font.PLAIN, 16));
            ((AbstractDocument) contactField.getDocument()).setDocumentFilter(new DocumentFilter() {
                private final int MAX_LENGTH = 10;
                @Override
                public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
                    if (string != null && string.matches("\\d+")) {
                        if (fb.getDocument().getLength() + string.length() <= MAX_LENGTH) {
                            super.insertString(fb, offset, string, attr);
                        }
                    }
                }
                @Override
                public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
                    if (text != null && text.matches("\\d+")) {
                        if (fb.getDocument().getLength() - length + text.length() <= MAX_LENGTH) {
                            super.replace(fb, offset, length, text, attrs);
                        }
                    }
                }
            });
            panel.add(contactField, gbc);
            JLabel contactError = makeErrorLabel();
            gbc.gridy = row + 1;
            panel.add(contactError, gbc);
            row += 2;
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.gridwidth = 2;
            panel.add(makeLabel("Upload Image:"), gbc);
            row++;
            JButton uploadBtn = new JButton("Choose File");
            uploadBtn.setOpaque(true);
            uploadBtn.setContentAreaFilled(true);
            uploadBtn.setBackground(new Color(0x2f54eb));
            uploadBtn.setBorder(BorderFactory.createLineBorder(new Color(0x2f54eb), 2));
            uploadBtn.setForeground(Color.WHITE);
            uploadBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
            JLabel fileNameLabel = new JLabel("No file selected");
            fileNameLabel.setForeground(Color.WHITE);
            JPanel uploadPanel = new JPanel(new BorderLayout(10, 0));
            uploadPanel.setBackground(new Color(0x0a0f17));
            uploadPanel.add(uploadBtn, BorderLayout.WEST);
            uploadPanel.add(fileNameLabel, BorderLayout.CENTER);
            gbc.gridy = row;
            panel.add(uploadPanel, gbc);
            gbc.gridwidth = 1;
            uploadBtn.addActionListener(e -> {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Image Files", "jpg", "png", "jpeg", "gif"));
                int result = fileChooser.showOpenDialog(this);
                if (result == JFileChooser.APPROVE_OPTION) {
                    uploadedFilePath = fileChooser.getSelectedFile().getAbsolutePath();
                    fileNameLabel.setText(fileChooser.getSelectedFile().getName());
                }
            });
            row++;
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.gridwidth = 2;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;
            gbc.insets = new Insets(20, 8, 10, 8);
            JButton submitBtn = new JButton("Report Item");
            submitBtn.setOpaque(true);
            submitBtn.setContentAreaFilled(true);
            submitBtn.setBackground(new Color(0x2f54eb));
            submitBtn.setBorder(BorderFactory.createLineBorder(new Color(0x2f54eb), 2));
            submitBtn.setForeground(Color.WHITE);
            submitBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
            submitBtn.setPreferredSize(new Dimension(150, 40));
            submitBtn.setMaximumSize(new Dimension(150, 40));
            submitBtn.setMinimumSize(new Dimension(150, 40));
            JPanel submitPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            submitPanel.setBackground(new Color(0x0a0f17));
            submitPanel.add(submitBtn);
            panel.add(submitPanel, gbc);
            submitBtn.addActionListener(e -> {
                // DEBUG: trace submit clicks and state
                System.out.println("[DEBUG] submitBtn clicked - role=" + parentApp.currentRole + " isLoggedIn=" + parentApp.isLoggedIn + " loggedInEmail=" + parentApp.loggedInEmail);
                boolean valid = true;
                clearErrors(itemNameError, categoryError, descriptionError, dateError, timeError, locationError, statusError, contactError);
                if (itemNameField.getText().trim().isEmpty()) {
                    itemNameError.setText("Item Name is required");
                    valid = false;
                }
                if (categoryBox.getSelectedIndex() == 0) {
                    categoryError.setText("Category is required");
                    valid = false;
                }
                if (descriptionField.getText().trim().isEmpty()) {
                    descriptionError.setText("Description is required");
                    valid = false;
                }
                if (dateField.getText().trim().isEmpty()) {
                    dateError.setText("Date is required");
                    valid = false;
                }
                if (hourBox.getSelectedItem() == null || minuteBox.getSelectedItem() == null || ampmBox.getSelectedItem() == null) {
                    timeError.setText("Time is required");
                    valid = false;
                }
                if (locationField.getText().trim().isEmpty()) {
                    locationError.setText("Location is required");
                    valid = false;
                }
                if (statusBox.getSelectedIndex() == 0) {
                    statusError.setText("Status is required");
                    valid = false;
                }
                if (contactField.getText().trim().isEmpty()) {
                    contactError.setText("Contact is required");
                    valid = false;
                }
                if (!valid) {
                    System.out.println("[DEBUG] submit validation failed");
                    return;
                }
                System.out.println("[DEBUG] submit validation passed");
                String itemName = itemNameField.getText();
                String category = (String) categoryBox.getSelectedItem();
                String description = descriptionField.getText();
                String date = dateField.getText();
                String time = hourBox.getSelectedItem() + ":" + minuteBox.getSelectedItem() + " " + ampmBox.getSelectedItem();
                String location = locationField.getText();
                String status = (String) statusBox.getSelectedItem();
        Listing newListing = new Listing(
            0,
            parentApp.loggedInEmail,
            itemName,
            category,
            description,
            date + " " + time,
            location,
            status,
            contactField.getText(),
            uploadedFilePath
        );
                // Try to persist directly to DB so the user gets immediate feedback on failures.
                boolean persistedToDB = false;
                if (parentApp == null) {
                    System.out.println("[DEBUG] parentApp is null - falling back to in-memory");
                }
                boolean isAdmin = parentApp != null && "ADMIN".equalsIgnoreCase(parentApp.currentRole);
                if (isAdmin) System.out.println("[DEBUG] Admin submission detected - skipping DB persist");
                if (parentApp != null && parentApp.dbManager != null && !isAdmin) {
                    System.out.println("[DEBUG] Attempting DB persist (dbManager available)");
                    try {
                        parentApp.dbManager.addListing(newListing.reporterEmail, newListing.itemName, newListing.category, newListing.description, newListing.reportedAt, newListing.location, newListing.status, newListing.contactInfo, newListing.imagePath);
                        // Reload canonical DB-backed listings
                        parentApp.reloadListingsFromDB();
                        persistedToDB = true;
                        System.out.println("[DEBUG] DB persist succeeded");
                    } catch (Exception ex) {
                        System.err.println("[DEBUG] DB persist threw: " + ex.getMessage());
                        ex.printStackTrace();
                        // Log full stack trace to a file for diagnosis, then show a compact warning
                        try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter("db_error.log", true))) {
                            pw.println("--- DB Save Error: " + new java.util.Date() + " ---");
                            ex.printStackTrace(pw);
                            pw.println();
                        } catch (Exception logEx) {
                            System.err.println("Failed to write db_error.log: " + logEx.getMessage());
                        }
                        JOptionPane.showMessageDialog(this, "Warning: failed to save to database. Listing will be kept in memory.\nSee db_error.log for details.", "DB Save Warning", JOptionPane.WARNING_MESSAGE);
                    }
                } else {
                    System.out.println("[DEBUG] Skipping DB persist (dbManager not available or admin)");
                }
                if (!persistedToDB) {
                    // fall back to existing method which will append to in-memory lists and refresh UI
                    parentApp.addListing(newListing);
                }
                JOptionPane.showMessageDialog(this, "Item reported successfully! It has been added to the listings.", "Success", JOptionPane.INFORMATION_MESSAGE);
                itemNameField.setText("");
                categoryBox.setSelectedIndex(0);
                descriptionField.setText("");
                dateField.setText("");
                hourBox.setSelectedIndex(0);
                minuteBox.setSelectedIndex(0);
                ampmBox.setSelectedIndex(0);
                locationField.setText("");
                statusBox.setSelectedIndex(0);
                contactField.setText("");
                uploadedFilePath = null;
                fileNameLabel.setText("No file selected");
                clearErrors(itemNameError, categoryError, descriptionError, dateError, timeError, locationError, statusError, contactError);
                parentApp.cardLayout.show(parentApp.mainPanel, "ViewListings");
            });
            JPanel centerPanel = new JPanel(new GridBagLayout());
            centerPanel.setBackground(new Color(0x0a0f17));
            GridBagConstraints centerGbc = new GridBagConstraints();
            centerGbc.gridx = 0;
            centerGbc.gridy = 0;
            centerGbc.anchor = GridBagConstraints.NORTH;
            centerPanel.add(panel, centerGbc);
            JScrollPane scrollPane = new JScrollPane(centerPanel);
            scrollPane.setBorder(null);
            scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            scrollPane.getVerticalScrollBar().setUnitIncrement(16);
            scrollPane.getViewport().setBackground(new Color(0x0a0f17));
            setLayout(new BorderLayout());
            add(scrollPane, BorderLayout.CENTER);
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    updatePanelSize();
                }
            });
        }
        private void updatePanelSize() {
            panel.revalidate();
            panel.repaint();
        }
        private void clearErrors(JLabel... labels) {
            for (JLabel lbl : labels) lbl.setText(" ");
        }
        private JLabel makeLabel(String text) {
            JLabel label = new JLabel(text);
            label.setForeground(new Color(200, 200, 200));
            return label;
        }
        private JLabel makeErrorLabel() {
            JLabel label = new JLabel(" ");
            label.setForeground(Color.RED);
            return label;
        }
        private JTextField makeTextField(int width, int height) {
            JTextField tf = new JTextField();
            tf.setPreferredSize(new Dimension(width, height));
            tf.setMinimumSize(new Dimension(width, height));
            tf.setBackground(new Color(0x0a0f17));
            tf.setForeground(Color.WHITE);
            tf.setCaretColor(Color.WHITE);
            tf.setBorder(BorderFactory.createLineBorder(new Color(0x3b4a6b)));
            return tf;
        }
        private JTextArea makeTextArea(int width, int height) {
            JTextArea ta = new JTextArea();
            ta.setPreferredSize(new Dimension(width, height));
            ta.setMinimumSize(new Dimension(width, height));
            ta.setBackground(new Color(0x0a0f17));
            ta.setForeground(Color.WHITE);
            ta.setCaretColor(Color.WHITE);
            ta.setLineWrap(true);
            ta.setWrapStyleWord(true);
            ta.setBorder(BorderFactory.createLineBorder(new Color(0x3b4a6b)));
            return ta;
        }
        private JComboBox<String> makeCombo(String[] items) {
            JComboBox<String> box = new JComboBox<>(items);
            box.setBackground(new Color(0x0a0f17));
            box.setForeground(Color.WHITE);
            box.setBorder(BorderFactory.createLineBorder(new Color(0x3b4a6b), 2));
            box.setPreferredSize(new Dimension(70, 30));
            return box;
        }
        private void styleButton(JButton btn, boolean primary) {
            if (primary) {
                btn.setBackground(new Color(0x2f54eb));
                btn.setForeground(Color.WHITE);
                btn.setBorder(BorderFactory.createLineBorder(new Color(0x2f54eb), 2));
            } else {
                btn.setBackground(new Color(0x0a0f17));
                btn.setForeground(Color.WHITE);
                btn.setBorder(BorderFactory.createLineBorder(new Color(0x3b4a6b), 2));
            }
            btn.setFocusPainted(false);
        }
        private void styleDarkCombo(JComboBox<?> combo) {
            combo.setBackground(new Color(0x0a0f17));
            combo.setForeground(Color.WHITE);
            combo.setBorder(BorderFactory.createLineBorder(new Color(0x3b4a6b), 2));
            combo.setUI(new BasicComboBoxUI() {
                @Override
                protected JButton createArrowButton() {
                    JButton b = new JButton();
                    b.setBackground(new Color(0x0a0f17));
                    b.setForeground(Color.WHITE);
                    b.setBorder(BorderFactory.createEmptyBorder());
                    return b;
                }
            });
        }
        private class AdvancedCalendarPanel extends JPanel {
            private JComboBox<String> monthCombo;
            private JComboBox<Integer> yearCombo;
            private JPanel daysPanel;
            public AdvancedCalendarPanel(JTextField targetField, Window parentWindow) {
                setLayout(new BorderLayout());
                setBackground(new Color(0x0a0f17));
                Calendar cal = Calendar.getInstance();
                String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
                monthCombo = new JComboBox<>(months);
                monthCombo.setSelectedIndex(cal.get(Calendar.MONTH));
                styleDarkCombo(monthCombo);
                int currentYear = cal.get(Calendar.YEAR);
                Integer[] years = new Integer[50];
                for (int i = 0; i < 50; i++) years[i] = currentYear - 25 + i;
                yearCombo = new JComboBox<>(years);
                yearCombo.setSelectedItem(currentYear);
                styleDarkCombo(yearCombo);
                JPanel topPanel = new JPanel();
                topPanel.setBackground(new Color(0x0a0f17));
                topPanel.add(monthCombo);
                topPanel.add(yearCombo);
                add(topPanel, BorderLayout.NORTH);
                daysPanel = new JPanel(new GridLayout(7, 7));
                daysPanel.setBackground(new Color(0x0a0f17));
                add(daysPanel, BorderLayout.CENTER);
                updateDays(monthCombo.getSelectedIndex(), (Integer) yearCombo.getSelectedItem(), targetField, parentWindow);
                monthCombo.addActionListener(e -> updateDays(monthCombo.getSelectedIndex(), (Integer) yearCombo.getSelectedItem(), targetField, parentWindow));
                yearCombo.addActionListener(e -> updateDays(monthCombo.getSelectedIndex(), (Integer) yearCombo.getSelectedItem(), targetField, parentWindow));
            }
            private void styleDarkCombo(JComboBox<?> combo) {
                combo.setBackground(new Color(0x0a0f17));
                combo.setForeground(Color.WHITE);
                combo.setUI(new BasicComboBoxUI() {
                    protected JButton createArrowButton() {
                        JButton b = new JButton();
                        b.setBackground(new Color(0x0a0f17));
                        b.setForeground(Color.WHITE);
                        b.setBorder(BorderFactory.createEmptyBorder());
                        return b;
                    }
                });
            }
            private void updateDays(int month, int year, JTextField targetField, Window parentWindow) {
                daysPanel.removeAll();
                String[] days = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
                for (String d : days) {
                    JLabel lbl = new JLabel(d, SwingConstants.CENTER);
                    lbl.setForeground(Color.WHITE);
                    daysPanel.add(lbl);
                }
                Calendar cal = Calendar.getInstance();
                cal.set(year, month, 1);
                int startDay = cal.get(Calendar.DAY_OF_WEEK) - 1;
                int numDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
                Calendar today = Calendar.getInstance();
                int currentDay = today.get(Calendar.DAY_OF_MONTH);
                int currentMonth = today.get(Calendar.MONTH);
                int currentYear = today.get(Calendar.YEAR);
                for (int i = 0; i < startDay; i++) daysPanel.add(new JLabel(""));
                for (int d = 1; d <= numDays; d++) {
                    JButton btn = new JButton(String.valueOf(d));
                    btn.setBackground(new Color(0x0a0f17));
                    btn.setForeground(Color.WHITE);
                    btn.setBorder(BorderFactory.createLineBorder(new Color(0x3b4a6b), 2));
                    btn.setMargin(new Insets(0, 0, 0, 0));
                    if (year > currentYear || (year == currentYear && month > currentMonth) || (year == currentYear && month == currentMonth && d > currentDay)) {
                        btn.setEnabled(false);
                        btn.setForeground(Color.GRAY);
                    } else {
                        btn.addActionListener(e -> {
                            targetField.setText(btn.getText() + "/" + (month + 1) + "/" + year);
                            parentWindow.dispose();
                        });
                    }
                    daysPanel.add(btn);
                }
                int remaining = 42 - startDay - numDays;
                for (int i = 0; i < remaining; i++) daysPanel.add(new JLabel(""));
                daysPanel.revalidate();
                daysPanel.repaint();
            }
        }
    }
   
    class ViewAllListings extends JPanel {
        private JPanel listingsPanel;
        private JTextField searchField;
        private JComboBox<String> categoryFilter;
        private JCheckBox showLost, showFound;
        private List<Listing> sharedListings;
        private MainApplication parentApp;
        public ViewAllListings(List<Listing> sharedListings, MainApplication parentApp) {
            this.sharedListings = sharedListings;
            this.parentApp = parentApp;
            setBackground(new Color(18, 24, 35));
            setLayout(new BorderLayout(10, 10));
            setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            JPanel topPanel = new JPanel(new BorderLayout());
            topPanel.setOpaque(false);
            JLabel title = new JLabel("View All Listings", SwingConstants.CENTER);
            title.setFont(new Font("SansSerif", Font.BOLD, 28));
            title.setForeground(new Color(0, 160, 255));
            topPanel.add(title, BorderLayout.NORTH);
            JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
            controlPanel.setOpaque(false);
            searchField = new JTextField(20);
            searchField.putClientProperty("JComponent.roundRect", true);
            searchField.setToolTipText("Search by name or description");
            styleTextField(searchField);
            searchField.addActionListener(e -> filterListings());
            String[] categories = {"All", "Electronics", "Clothing", "Accessories", "Documents", "Others"};
            categoryFilter = new JComboBox<>(categories);
            styleCombo(categoryFilter);
            categoryFilter.addActionListener(e -> filterListings());
            showLost = new JCheckBox("Show Lost");
            styleCheckBox(showLost);
            showLost.setSelected(true);
            showLost.addItemListener(e -> filterListings());
            showFound = new JCheckBox("Show Found");
            styleCheckBox(showFound);
            showFound.setSelected(true);
            showFound.addItemListener(e -> filterListings());
            controlPanel.add(new JLabel("Search:"));
            controlPanel.add(searchField);
            controlPanel.add(new JLabel("Category:"));
            controlPanel.add(categoryFilter);
            controlPanel.add(new JLabel("Status:"));
            controlPanel.add(showLost);
            controlPanel.add(showFound);
            JButton refreshBtn = new JButton("Refresh");
            refreshBtn.setFocusPainted(false);
            refreshBtn.setBackground(new Color(60, 60, 60));
            refreshBtn.setForeground(Color.WHITE);
            refreshBtn.addActionListener(e -> {
                if (this.parentApp != null) {
                    this.parentApp.reloadListingsFromDB();
                    this.refreshListings();
                }
            });
            controlPanel.add(refreshBtn);
            topPanel.add(controlPanel, BorderLayout.CENTER);
            add(topPanel, BorderLayout.NORTH);
            listingsPanel = new JPanel();
            listingsPanel.setLayout(new BoxLayout(listingsPanel, BoxLayout.Y_AXIS));
            listingsPanel.setBackground(new Color(18, 24, 35));
            JScrollPane scrollPane = new JScrollPane(listingsPanel);
            scrollPane.setBorder(BorderFactory.createEmptyBorder());
            scrollPane.getViewport().setBackground(new Color(18, 24, 35));
            add(scrollPane, BorderLayout.CENTER);
            refreshListings();
        }
        public void refreshListings() {
            listingsPanel.removeAll();
            String searchText = searchField.getText().trim().toLowerCase();
            String selectedCategory = (String) categoryFilter.getSelectedItem();
            boolean lostChecked = showLost.isSelected();
            boolean foundChecked = showFound.isSelected();
            for (Listing listing : sharedListings) {
                boolean matchesSearch = searchText.isEmpty() ||
                        listing.itemName.toLowerCase().contains(searchText) ||
                        listing.description.toLowerCase().contains(searchText);
                boolean matchesCategory = "All".equals(selectedCategory) ||
                        listing.category.equals(selectedCategory);
                boolean matchesStatus = (lostChecked && listing.status.equals("Lost")) ||
                        (foundChecked && listing.status.equals("Found"));
                if (matchesSearch && matchesCategory && matchesStatus) {
                    listingsPanel.add(new ListingPanel(listing));
                    listingsPanel.add(Box.createRigidArea(new Dimension(0, 10)));
                }
            }
            listingsPanel.revalidate();
            listingsPanel.repaint();
        }
        // Scroll to and highlight a listing by id if it's visible in the current filtered view
        public void selectListingById(int id) {
            for (Component c : listingsPanel.getComponents()) {
                if (c instanceof ListingPanel) {
                    ListingPanel lp = (ListingPanel) c;
                    if (lp.getListingId() == id) {
                        lp.scrollRectToVisible(lp.getBounds());
                        lp.highlightOnce();
                        break;
                    }
                }
            }
        }
        private void filterListings() {
            refreshListings();
        }
        private void styleTextField(JTextField tf) {
            tf.setBackground(new Color(34, 40, 49));
            tf.setForeground(Color.WHITE);
            tf.setCaretColor(Color.WHITE);
            tf.setBorder(BorderFactory.createLineBorder(new Color(80, 90, 105)));
            tf.setPreferredSize(new Dimension(200, 30));
        }
        private void styleCombo(JComboBox<String> combo) {
            combo.setBackground(new Color(34, 40, 49));
            combo.setForeground(Color.WHITE);
            combo.setPreferredSize(new Dimension(150, 30));
        }
        private void styleCheckBox(JCheckBox cb) {
            cb.setBackground(new Color(18, 24, 35));
            cb.setForeground(Color.WHITE);
            cb.setFocusPainted(false);
        }
        private class ListingPanel extends JPanel {
            private final Listing listingObj;
            public ListingPanel(Listing listing) {
                this.listingObj = listing;
                setLayout(new BorderLayout(10, 0));
                setBackground(new Color(25, 30, 45));
                setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
                setBorder(BorderFactory.createLineBorder(new Color(40, 50, 60), 1));
                if (listingObj.imagePath != null && !listingObj.imagePath.isEmpty()) {
                    ImageIcon originalIcon = new ImageIcon(listingObj.imagePath);
                    Image originalImage = originalIcon.getImage();
                    Image scaledImage = originalImage.getScaledInstance(80, 80, Image.SCALE_SMOOTH);
                    JLabel imageLabel = new JLabel(new ImageIcon(scaledImage));
                    add(imageLabel, BorderLayout.WEST);
                }
                JPanel textPanel = new JPanel(new GridLayout(0, 1));
                textPanel.setOpaque(false);
                JLabel nameLabel = new JLabel(listingObj.itemName);
                nameLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
                nameLabel.setForeground(Color.WHITE);
                JLabel descLabel = new JLabel("<html><div style='text-overflow: ellipsis; white-space: nowrap; overflow: hidden;'>" + listingObj.description + "</div></html>");
                descLabel.setForeground(new Color(180, 180, 180));
                textPanel.add(nameLabel);
                textPanel.add(descLabel);
                JLabel statusLabel = new JLabel(listingObj.status, SwingConstants.CENTER);
                statusLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
                statusLabel.setOpaque(true);
                statusLabel.setBackground(listingObj.status.equals("Lost") ? new Color(220, 50, 50) : new Color(50, 180, 80));
                statusLabel.setForeground(Color.WHITE);
                statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
                statusLabel.setPreferredSize(new Dimension(80, 25));
                JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
                rightPanel.setOpaque(false);
                rightPanel.add(statusLabel);
                JButton detailsButton = new JButton("Details");
                detailsButton.setFocusPainted(false);
                detailsButton.setBackground(new Color(24, 120, 240));
                detailsButton.setForeground(Color.WHITE);
                detailsButton.addActionListener(e -> new ListingDetailsDialog(listingObj).setVisible(true));
                rightPanel.add(detailsButton);
                add(textPanel, BorderLayout.CENTER);
                add(rightPanel, BorderLayout.EAST);
            }
            public int getListingId() { return listingObj.id; }
            public void highlightOnce() {
                setBorder(BorderFactory.createLineBorder(new Color(255, 200, 0), 2));
                Timer t = new Timer(1800, ae -> setBorder(BorderFactory.createLineBorder(new Color(40, 50, 60), 1)));
                t.setRepeats(false);
                t.start();
            }
        }
        private class ListingDetailsDialog extends JDialog {
            public ListingDetailsDialog(Listing listing) {
                setTitle("Listing Details");
                setModal(true);
                setBackground(new Color(18, 24, 35));
                setDefaultCloseOperation(DISPOSE_ON_CLOSE);
                setResizable(false);
                setLayout(new BorderLayout(12, 12));
                setSize(520, 560);
                setLocationRelativeTo(null);

                JPanel panel = new JPanel(new GridBagLayout());
                panel.setBackground(new Color(25, 30, 45));
                panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
                GridBagConstraints gbc = new GridBagConstraints();
                gbc.insets = new Insets(8, 8, 8, 8);
                gbc.anchor = GridBagConstraints.NORTHWEST;

                // Title row (name + status badge)
                JLabel title = new JLabel(listing.itemName);
                title.setFont(new Font("SansSerif", Font.BOLD, 20));
                title.setForeground(new Color(0, 160, 255));
                gbc.gridx = 0;
                gbc.gridy = 0;
                gbc.gridwidth = 1;
                gbc.weightx = 1.0;
                gbc.fill = GridBagConstraints.HORIZONTAL;
                panel.add(title, gbc);

                JLabel statusBadge = new JLabel(listing.status);
                statusBadge.setOpaque(true);
                statusBadge.setForeground(Color.WHITE);
                statusBadge.setFont(new Font("SansSerif", Font.BOLD, 12));
                statusBadge.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
                statusBadge.setBackground(listing.status.equalsIgnoreCase("Lost") ? new Color(220, 50, 50) : new Color(50, 180, 80));
                gbc.gridx = 1;
                gbc.gridy = 0;
                gbc.fill = GridBagConstraints.NONE;
                gbc.weightx = 0;
                panel.add(statusBadge, gbc);

                // Image (centered) if present
                if (listing.imagePath != null && !listing.imagePath.isEmpty()) {
                    ImageIcon originalIcon = new ImageIcon(listing.imagePath);
                    Image originalImage = originalIcon.getImage();
                    Image scaledImage = originalImage.getScaledInstance(180, 180, Image.SCALE_SMOOTH);
                    JLabel imageLabel = new JLabel(new ImageIcon(scaledImage));
                    imageLabel.setBorder(BorderFactory.createLineBorder(new Color(60, 65, 75), 1));
                    gbc.gridx = 0;
                    gbc.gridy = 1;
                    gbc.gridwidth = 2;
                    gbc.fill = GridBagConstraints.NONE;
                    gbc.anchor = GridBagConstraints.CENTER;
                    panel.add(imageLabel, gbc);
                }

                // Details grid
                JPanel details = new JPanel(new GridBagLayout());
                details.setOpaque(false);
                GridBagConstraints d = new GridBagConstraints();
                d.insets = new Insets(6, 6, 6, 6);
                d.anchor = GridBagConstraints.WEST;
                d.gridx = 0; d.gridy = 0; d.weightx = 0;

                // Build detail rows explicitly (label + value)
                int rowIdx = 0;
                GridBagConstraints lc = new GridBagConstraints();
                lc.insets = new Insets(6, 6, 6, 6);
                lc.anchor = GridBagConstraints.WEST;
                lc.gridx = 0;

                GridBagConstraints vc = new GridBagConstraints();
                vc.insets = lc.insets;
                vc.anchor = GridBagConstraints.WEST;
                vc.gridx = 1;
                vc.weightx = 1.0;
                vc.fill = GridBagConstraints.HORIZONTAL;

                // DB id
                lc.gridy = rowIdx; vc.gridy = rowIdx;
                JLabel idLabel = new JLabel("DB id:"); idLabel.setForeground(new Color(200,200,200)); idLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
                details.add(idLabel, lc);
                JLabel idVal = new JLabel(String.valueOf(listing.id)); idVal.setForeground(Color.WHITE);
                details.add(idVal, vc);
                rowIdx++;

                // Reporter
                lc.gridy = rowIdx; vc.gridy = rowIdx;
                JLabel repLabel = new JLabel("Reporter:"); repLabel.setForeground(new Color(200,200,200)); repLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
                details.add(repLabel, lc);
                JLabel repVal = new JLabel(listing.reporterEmail == null ? "" : listing.reporterEmail); repVal.setForeground(Color.WHITE);
                details.add(repVal, vc);
                rowIdx++;

                // Category
                lc.gridy = rowIdx; vc.gridy = rowIdx;
                JLabel catLabel = new JLabel("Category:"); catLabel.setForeground(new Color(200,200,200)); catLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
                details.add(catLabel, lc);
                JLabel catVal = new JLabel(listing.category == null ? "" : listing.category); catVal.setForeground(Color.WHITE);
                details.add(catVal, vc);
                rowIdx++;

                // Date
                lc.gridy = rowIdx; vc.gridy = rowIdx;
                JLabel dateLabel = new JLabel("Date:"); dateLabel.setForeground(new Color(200,200,200)); dateLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
                details.add(dateLabel, lc);
                JLabel dateVal = new JLabel(listing.reportedAt == null ? "" : listing.reportedAt); dateVal.setForeground(Color.WHITE);
                details.add(dateVal, vc);
                rowIdx++;

                // Location
                lc.gridy = rowIdx; vc.gridy = rowIdx;
                JLabel locLabel = new JLabel("Location:"); locLabel.setForeground(new Color(200,200,200)); locLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
                details.add(locLabel, lc);
                JLabel locVal = new JLabel(listing.location == null ? "" : listing.location); locVal.setForeground(Color.WHITE);
                details.add(locVal, vc);
                rowIdx++;

                // Spacer row before description
                lc.gridy = rowIdx; vc.gridy = rowIdx;
                rowIdx++;

                // Description label + scrollable text area (spans columns)
                GridBagConstraints descLabelC = new GridBagConstraints();
                descLabelC.insets = new Insets(6,6,6,6);
                descLabelC.gridx = 0; descLabelC.gridy = rowIdx; descLabelC.anchor = GridBagConstraints.NORTHWEST;
                JLabel descLbl = new JLabel("Description:"); descLbl.setForeground(new Color(200,200,200)); descLbl.setFont(new Font("SansSerif", Font.BOLD, 12));
                details.add(descLbl, descLabelC);

                GridBagConstraints descC = new GridBagConstraints();
                descC.insets = new Insets(6,6,6,6);
                descC.gridx = 1; descC.gridy = rowIdx; descC.fill = GridBagConstraints.BOTH; descC.weightx = 1.0; descC.weighty = 1.0;
                JTextArea descArea = new JTextArea(listing.description == null ? "" : listing.description);
                descArea.setLineWrap(true); descArea.setWrapStyleWord(true); descArea.setEditable(false);
                descArea.setBackground(new Color(25,30,45)); descArea.setForeground(Color.WHITE);
                descArea.setBorder(BorderFactory.createLineBorder(new Color(60,65,75)));
                JScrollPane descScroll = new JScrollPane(descArea);
                descScroll.setPreferredSize(new Dimension(320,120));
                details.add(descScroll, descC);
                rowIdx++;

                // Attach details panel to main panel
                gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.BOTH; gbc.weighty = 1.0;
                panel.add(details, gbc);

                // Action buttons
                JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
                btnRow.setOpaque(false);
                JButton copyBtn = new JButton("Copy ID");
                copyBtn.addActionListener(ae -> {
                    try {
                        StringSelection ss = new StringSelection(String.valueOf(listing.id));
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ss, null);
                    } catch (Exception ex) { /* ignore */ }
                });
                JButton emailBtn = new JButton("Email Reporter");
                emailBtn.addActionListener(ae -> {
                    if (listing.reporterEmail != null && !listing.reporterEmail.isEmpty()) openGmailDirect(listing.reporterEmail, "About your reported item: " + listing.itemName, "Hi, regarding your report for " + listing.itemName + "...");
                });
                JButton closeBtn = new JButton("Close");
                closeBtn.addActionListener(ae -> dispose());
                btnRow.add(copyBtn); btnRow.add(emailBtn); btnRow.add(closeBtn);

                add(panel, BorderLayout.CENTER);
                add(btnRow, BorderLayout.SOUTH);
            }
            private void addDetail(JPanel panel, GridBagConstraints gbc, int row, String labelText, String valueText) {
                gbc.gridx = 0;
                gbc.gridy = row;
                gbc.gridwidth = 1;
                JLabel label = new JLabel(labelText);
                label.setForeground(new Color(200, 200, 200));
                panel.add(label, gbc);
                gbc.gridx = 1;
                JLabel value = new JLabel("<html><div style='width: 200px;'>" + valueText + "</div></html>");
                value.setForeground(Color.WHITE);
                panel.add(value, gbc);
            }
        }
    }
   
    // UI components and helpers
    static class RoundedButton extends JButton {
        public RoundedButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setForeground(Color.WHITE);
        }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
            super.paintComponent(g2);
            g2.dispose();
        }
        @Override
        public void setContentAreaFilled(boolean b) {}
    }
   
    static class FeatureCard extends JPanel {
        public FeatureCard(String title, String desc) {
            setOpaque(false);
            setLayout(new BorderLayout());
            setPreferredSize(new Dimension(300, 100));
            setMaximumSize(new Dimension(300, 100));
            setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 18));
            JLabel t = new JLabel(title);
            t.setFont(new Font("SansSerif", Font.BOLD, 16));
            t.setForeground(Color.WHITE);
            t.setHorizontalAlignment(SwingConstants.CENTER);
            JLabel d = new JLabel("<html><div style='text-align:center;'>" + desc + "</div></html>");
            d.setFont(new Font("SansSerif", Font.PLAIN, 13));
            d.setForeground(new Color(200, 210, 220));
            d.setHorizontalAlignment(SwingConstants.CENTER);
            add(t, BorderLayout.NORTH);
            add(d, BorderLayout.CENTER);
        }
        @Override
        protected void paintComponent(Graphics g) {
            int w = getWidth(), h = getHeight();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(0, 0, 0, 80));
            g2.fillRoundRect(6, 8, w - 6, h - 6, 18, 18);
            g2.setColor(new Color(28, 36, 44, 220));
            g2.fillRoundRect(0, 0, w - 8, h - 10, 18, 18);
            g2.dispose();
            super.paintComponent(g);
        }
    }
   
    static class BackgroundPanel extends JPanel {
        private final Image image;
        public BackgroundPanel(String imagePath) {
            Image img = null;
            try {
                img = new ImageIcon(imagePath).getImage();
            } catch (Exception ignored) {}
            image = img;
            setOpaque(true);
        }
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (image != null) {
                int iw = image.getWidth(null), ih = image.getHeight(null);
                if (iw > 0 && ih > 0) {
                    double sw = (double) getWidth() / iw;
                    double sh = (double) getHeight() / ih;
                    double s = Math.max(sw, sh);
                    int w = (int) (iw * s), h = (int) (ih * s);
                    int x = (getWidth() - w) / 2;
                    int y = (getHeight() - h) / 2;
                    g.drawImage(image, x, y, w, h, this);
                } else {
                    g.drawImage(image, 0, 0, getWidth(), getHeight(), this);
                }
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(new Color(6, 10, 14, 150));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            } else {
                setBackground(new Color(40, 44, 50));
            }
        }
    }
}
