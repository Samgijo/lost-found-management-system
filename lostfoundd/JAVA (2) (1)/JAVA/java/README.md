# 🔍 Lost & Found Management System

A full-featured **Java Swing** desktop application for reporting, tracking, and managing lost and found items. Built with a modern dark-themed UI, role-based access control, and dual-database support (MySQL + SQLite fallback).

---

## 📋 Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Screenshots & UI Overview](#screenshots--ui-overview)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Installation & Setup](#installation--setup)
- [Database Configuration](#database-configuration)
- [How to Compile & Run](#how-to-compile--run)
- [User Guide](#user-guide)
- [Admin Guide](#admin-guide)
- [Database Schema](#database-schema)
- [Database Inspection Tools](#database-inspection-tools)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)
- [License](#license)

---

## Overview

The **Lost & Found Management System** allows users to report lost or found items and browse existing listings. An admin dashboard provides full management capabilities — verifying reports, managing users, and sending email notifications. The application connects to **MySQL** as its primary datastore and gracefully falls back to an embedded **SQLite** database when MySQL is unavailable.

---

## Features

### 🔐 Authentication & User Management
- **Sign Up** — Register with username, email, and password
- **Login** — Authenticate via email + password (username optional)
- **Update Account** — Change username or password for existing accounts
- **Admin Backdoor** — Quick admin access with hardcoded credentials (`ADMIN` / `ADMIN123`)
- **Profile Menu** — Avatar-based dropdown with user info, role display, and logout

### 📝 Report Lost/Found Items
- Submit detailed reports with:
  - Item name and category (Electronics, Documents, Clothing, Accessories, Books, Other)
  - Description (with character limit)
  - Date picker with built-in calendar widget
  - Time selector (hour / minute / AM-PM)
  - Location
  - Status (Lost or Found)
  - Contact info (10-digit numeric input with validation)
  - Image upload (JPG, PNG, JPEG, GIF)
- Full form validation with inline error messages

### 📋 View All Listings
- Browse all active lost/found items in a card-based layout
- Click any listing to see full details in a dialog
- View attached images
- **Contact via Gmail** — One-click button opens Gmail compose with pre-filled recipient, subject, and body
- **Copy Email** — Copy reporter's email to clipboard
- Listings auto-refresh after new reports or admin actions

### 🛡️ Admin Dashboard
- **Dashboard Overview** — Real-time stats cards showing Total Reports, Resolved Cases, and Pending Reports
- **Manage Users** — View all registered users, delete accounts
- **Manage Listings** — View, verify, or delete item listings
- **Verified Reports** — View resolved cases, delete records, send follow-up emails
- Sidebar navigation with hover effects
- Blue-themed table headers with custom cell renderers

### 🗄️ Dual Database Support
- **MySQL (Primary)** — Full relational database with auto-schema creation
- **SQLite (Fallback)** — Embedded file-based DB (`lostfound.db`) when MySQL is unavailable
- Auto-detection of available JDBC drivers
- Dynamic JAR loading from `lib/` directory
- Schema migration and sync between database backends

### 🎨 Modern Dark-Themed UI
- Dark color palette (navy/charcoal backgrounds)
- Nimbus Look-and-Feel
- Rounded buttons, gradient hero section with background image
- Feature cards with shadow effects
- Responsive full-screen layout
- Custom styled combo boxes, text fields, and password fields

---

## Screenshots & UI Overview

| Page | Description |
|------|-------------|
| **Home** | Hero section with background image, tagline, feature cards |
| **Login** | Centered card with username, email, password fields |
| **Sign Up** | Registration form with password confirmation |
| **Report Item** | Multi-field form with calendar, time picker, image upload |
| **View Listings** | Card grid of all active listings with detail dialogs |
| **Admin Dashboard** | Stats overview + tabbed management panels |

---

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Java 8+ |
| UI Framework | Java Swing (with Nimbus L&F) |
| Primary Database | MySQL 8.x |
| Fallback Database | SQLite 3.x |
| MySQL JDBC Driver | MySQL Connector/J (`mysql-connector-java-*.jar`) |
| SQLite JDBC Driver | `sqlite-jdbc-3.36.0.3.jar` / `sqlite-jdbc-3.41.2.1.jar` |
| Build | Manual `javac` / `java` (no Maven/Gradle) |
| IDE Support | VS Code with Java Extension Pack |

---

## Project Structure

```
java/
├── src/                          # Main source files
│   ├── MainApplication.java      # Entry point — all UI panels, navigation, auth logic
│   └── DatabaseManager.java      # DB connection, CRUD, schema management, migrations
│
├── lib/                          # JDBC driver JARs
│   ├── sqlite-jdbc-3.36.0.3.jar
│   └── sqlite-jdbc-3.41.2.1.jar
│
├── bin/                          # Compiled .class files (auto-generated)
│
├── archived_helpers/             # DB diagnostic/migration utility scripts
│   ├── DbDumpListings.java
│   ├── DbInit.java
│   ├── DbInsertTest.java
│   ├── DbInspect.java
│   ├── DbMigrateSqliteToMySQL.java
│   ├── DeleteSampleRows.java
│   ├── MySQLColumnsInspector.java
│   ├── MySQLInspect.java
│   ├── SqlitePreview.java
│   └── TestLoginAuth.java
│
├── lostfound.db                  # SQLite database file (auto-created)
├── hero_bg.jpg                   # Background image for home page hero section
└── README.md                     # This file
```

### Key Source Files

| File | Lines | Purpose |
|------|-------|---------|
| `MainApplication.java` | ~2600 | Complete GUI — Home, Login, SignUp, UpdateAccount, ReportItemForm, ViewAllListings, AdminDashboard, and all UI helpers |
| `DatabaseManager.java` | ~950 | Database abstraction layer — MySQL/SQLite connection, schema creation, CRUD operations, data migration, and verification workflow |

---

## Prerequisites

- **Java JDK 8** or later  
  Verify: `java -version` and `javac -version`

- **MySQL Server 8.x** *(optional but recommended)*  
  Required for full database functionality. Without it, the app uses SQLite.

- **SQLite JDBC JAR** *(included in `lib/`)*  
  Already bundled — no action needed.

- **MySQL Connector/J** *(optional)*  
  Only needed if using MySQL. Download from: https://dev.mysql.com/downloads/connector/j/

---

## Installation & Setup

### 1. Clone or Download the Project

```bash
git clone <repository-url>
cd lostfoundd/JAVA\ \(2\)\ \(1\)/JAVA/java
```

### 2. Verify Java Installation

```powershell
java -version
javac -version
```

### 3. (Optional) Set Up MySQL

If you want to use MySQL as the primary database:

```sql
-- In MySQL shell:
CREATE DATABASE lostfound CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Then place the MySQL Connector/J JAR in the `lib/` folder:
```
lib/mysql-connector-java-8.0.33.jar   (or your version)
```

> **Note:** If MySQL is not available, the app automatically falls back to SQLite (`lostfound.db`).

---

## Database Configuration

The application connects to MySQL with these default settings:

| Parameter | Default Value |
|-----------|---------------|
| Host | `localhost` |
| Port | `3306` |
| Database | `lostfound` |
| Username | `root` |
| Password | `root` |

To change these, edit the `DatabaseManager` constructor call in `src/MainApplication.java` (line ~67):

```java
dbManager = new DatabaseManager("localhost", 3306, "lostfound", "root", "root");
```

### Environment Variables

| Variable | Value | Effect |
|----------|-------|--------|
| `LOSTFOUND_MYSQL_ONLY` | `1` | Disables SQLite fallback — app will fail if MySQL is unavailable |

---

## How to Compile & Run

### Option A: SQLite Only (Simplest)

```powershell
# Compile
javac -cp "lib/*;." -d bin src\DatabaseManager.java src\MainApplication.java

# Run
java -cp "lib/*;bin;." MainApplication
```

### Option B: With MySQL Support

1. Ensure MySQL server is running and the `lostfound` database exists.
2. Place `mysql-connector-java-*.jar` in `lib/`.

```powershell
# Compile
javac -cp "lib/*;." -d bin src\DatabaseManager.java src\MainApplication.java

# Run (app will try MySQL first, fall back to SQLite)
java -cp "lib/*;bin;." MainApplication
```

### macOS / Linux Users

Replace `;` with `:` in classpath:

```bash
javac -cp "lib/*:." -d bin src/DatabaseManager.java src/MainApplication.java
java -cp "lib/*:bin:." MainApplication
```

---

## User Guide

### Registration & Login

1. Click **Signup** in the top-right navigation bar.
2. Enter your **username**, **email**, and **password** (with confirmation).
3. After successful registration, you'll be redirected to the **Login** page.
4. Log in using your **email** and **password**.

### Reporting a Lost/Found Item

1. Log in to your account.
2. Click **Report Item** in the navigation bar (or the hero button on the home page).
3. Fill in all required fields:
   - **Item Name** — Name of the lost/found item
   - **Category** — Select from dropdown
   - **Description** — Describe the item (max character limit enforced)
   - **Date** — Click the calendar button to pick a date
   - **Time** — Select hour, minute, and AM/PM
   - **Location** — Where the item was lost/found
   - **Status** — Select "Lost" or "Found"
   - **Contact Info** — Your phone number (10 digits, numbers only)
   - **Image** — Optionally upload a photo of the item
4. Click **Report Item** to submit.

### Viewing Listings

1. Click **View Listings** in the navigation bar.
2. Browse the card grid of all active listings.
3. Click a card to view full details.
4. Use **Contact via Gmail** to email the reporter directly.
5. Use **Copy Email** to copy their email address.

---

## Admin Guide

### Accessing the Admin Dashboard

**Method 1 — Admin Backdoor Login:**
- Username: `ADMIN`
- Email: *(any value)*
- Password: `ADMIN123`

**Method 2 — Regular Admin Account:**
- Any user with username `admin` (case-insensitive) is treated as an admin.

Once logged in as admin, click your **avatar** in the top-right corner and select **Admin Dashboard**.

### Dashboard Sections

| Section | Capabilities |
|---------|-------------|
| **Dashboard** | View real-time counts — Total Reports, Resolved Cases, Pending Reports |
| **Manage Users** | View all registered users; delete user accounts |
| **Manage Listings** | View all item listings; verify items (moves to Verified Reports); delete listings |
| **Verified Reports** | View all resolved/verified items; delete records; send follow-up emails via Gmail |

### Verification Workflow

1. Navigate to **Manage Listings**.
2. Click **Verify** on a listing row. This will:
   - Copy the listing data into the `verified_reports` table
   - Remove the listing from the active `listings` table
   - Update dashboard counts
3. The verified item now appears in the **Verified Reports** tab.

---

## Database Schema

### `users` Table

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | INT / INTEGER | PRIMARY KEY, AUTO_INCREMENT |
| `username` | VARCHAR(255) / TEXT | — |
| `email` | VARCHAR(255) / TEXT | UNIQUE |
| `password` | VARCHAR(255) / TEXT | — |

### `listings` Table

| Column | Type | Description |
|--------|------|-------------|
| `id` | INT / INTEGER | PRIMARY KEY, AUTO_INCREMENT |
| `reporter_email` | VARCHAR(255) / TEXT | Email of the user who reported |
| `item_name` | VARCHAR(255) / TEXT | Name of the item |
| `category` | VARCHAR(100) / TEXT | Category (Electronics, Documents, etc.) |
| `description` | TEXT | Detailed description |
| `report_date` | DATETIME / TEXT | Date and time of the report |
| `location` | VARCHAR(255) / TEXT | Location where item was lost/found |
| `status` | VARCHAR(50) / TEXT | "Lost" or "Found" |
| `contact_info` | VARCHAR(255) / TEXT | Phone number of the reporter |
| `image_path` | VARCHAR(255) / TEXT | File path to uploaded image |

### `verified_reports` Table

| Column | Type | Description |
|--------|------|-------------|
| `id` | INT / INTEGER | PRIMARY KEY, AUTO_INCREMENT |
| `listing_id` / `original_listing_id` | INT / INTEGER | Reference to original listing |
| `item_name` | VARCHAR(255) / TEXT | Name of the verified item |
| `status` | VARCHAR(50) / TEXT | Always set to "verified" |
| `email` / `reporter_email` | VARCHAR(255) / TEXT | Reporter's email |
| `contact_info` | VARCHAR(255) / TEXT | Reporter's contact info |
| `datetime` / `verification_date` | DATETIME / TEXT | When verified |
| `resolved_at` | DATETIME / TEXT | Resolution timestamp |
| `admin_notes` | TEXT | Notes added by admin during verification |

> **Note:** Column names vary slightly between MySQL and SQLite schemas. The `DatabaseManager` dynamically detects available columns and adapts queries accordingly.

---

## Database Inspection Tools

### Using sqlite3 CLI (Recommended)

```bash
# Open the database
sqlite3 lostfound.db

# View all tables
.tables

# View table schema
.schema users
.schema listings
.schema verified_reports

# Query data
SELECT * FROM users;
SELECT * FROM listings;
SELECT * FROM verified_reports;
```

### Using Java Diagnostic Tools

Several helper scripts are provided in `archived_helpers/`:

| Tool | Purpose |
|------|---------|
| `DbDumpListings.java` | Dump all listings from the database |
| `DbInit.java` | Initialize database tables |
| `DbInsertTest.java` | Test inserting data into the database |
| `DbInspect.java` | Inspect database structure and contents |
| `DbMigrateSqliteToMySQL.java` | Migrate data from SQLite to MySQL |
| `DeleteSampleRows.java` | Remove sample/test data |
| `MySQLColumnsInspector.java` | Inspect MySQL table columns |
| `MySQLInspect.java` | Inspect MySQL database |
| `SqlitePreview.java` | Preview SQLite database contents |
| `TestLoginAuth.java` | Test login authentication flow |

```powershell
# Example: Run DatabaseManager diagnostic
javac -cp "lib/*;." -d bin src\DatabaseManager.java
java -cp "lib/*;bin;." DatabaseManager
```

### Using DB Browser for SQLite

Open `lostfound.db` with [DB Browser for SQLite](https://sqlitebrowser.org/) for a GUI view.

---

## Troubleshooting

### ❌ `ClassNotFoundException: org.sqlite.JDBC`
**Cause:** SQLite JDBC driver not on classpath.  
**Fix:** Ensure `sqlite-jdbc-*.jar` is in `lib/` and included in the `-cp` argument.

### ❌ `ClassNotFoundException: com.mysql.cj.jdbc.Driver`
**Cause:** MySQL Connector/J not found.  
**Fix:** Download from https://dev.mysql.com/downloads/connector/j/ and place in `lib/`.  
**Note:** The app will automatically fall back to SQLite.

### ❌ `Communications link failure` / MySQL connection refused
**Cause:** MySQL server not running or wrong host/port.  
**Fix:** Start MySQL server, or update connection parameters in `MainApplication.java`.

### ❌ `Warning: database initialization failed - running in memory-only mode`
**Cause:** Neither MySQL nor SQLite could initialize.  
**Fix:** Ensure at least the SQLite JDBC JAR is in `lib/`.

### ❌ Login fails — "No account found for this email"
**Cause:** User not registered or email mismatch.  
**Fix:** Register a new account via Sign Up, or check email case sensitivity.

### ❌ Images not displaying in listings
**Cause:** Image path is absolute from the machine where it was uploaded.  
**Fix:** Ensure the referenced image file exists at the stored path.

### ❌ `allowPublicKeyRetrieval` error with MySQL
**Cause:** MySQL server requires public key retrieval.  
**Fix:** Already handled — the connection URL includes `allowPublicKeyRetrieval=true`.

---

## Application Architecture

```
┌─────────────────────────────────────────────────┐
│                MainApplication                   │
│         (JFrame + CardLayout navigation)         │
│                                                  │
│  ┌──────────┐  ┌───────┐  ┌────────┐            │
│  │ HomePage  │  │ Login │  │ SignUp │            │
│  └──────────┘  └───────┘  └────────┘            │
│  ┌──────────────┐  ┌─────────────────┐          │
│  │ ReportItemForm│  │ ViewAllListings │          │
│  └──────────────┘  └─────────────────┘          │
│  ┌──────────────────┐  ┌──────────────────┐     │
│  │ UpdateAccountPage│  │ AdminDashboard   │     │
│  └──────────────────┘  │  ├─ Dashboard    │     │
│                         │  ├─ ManageUsers  │     │
│                         │  ├─ ManageList.  │     │
│                         │  └─ VerifiedRep. │     │
│                         └──────────────────┘     │
└──────────────┬──────────────────────────────────┘
               │
               ▼
┌──────────────────────────┐
│     DatabaseManager       │
│  ┌────────┐  ┌─────────┐ │
│  │ MySQL  │→ │ SQLite  │ │
│  │(primary)│  │(fallback)│ │
│  └────────┘  └─────────┘ │
└──────────────────────────┘
```

---

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/your-feature`)
3. Commit your changes (`git commit -m 'Add your feature'`)
4. Push to the branch (`git push origin feature/your-feature`)
5. Open a Pull Request

---

## License

This project is developed for educational and demonstration purposes.

---

> **Built with ❤️ using Java Swing**
