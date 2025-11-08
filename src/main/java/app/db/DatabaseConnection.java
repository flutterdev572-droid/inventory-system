package app.db;

import app.current_user.CurrentUser;

import java.io.*;
import java.nio.file.Paths;
import java.sql.*;
import java.util.Properties;

public class DatabaseConnection {

    private static String HOST;
    private static String PORT;
    private static String INVENTORY_DB_NAME;
    private static String MANAGEMENT_DB_NAME;
    private static String USER;
    private static String PASSWORD;

    // Keep connections but always validate before reuse
    private static Connection inventoryConnection;
    private static Connection managementConnection;

    private static final String CONFIG_FILE =
            Paths.get(System.getProperty("user.home"), "warehouse_db_config.properties").toString();

    static {
        // try to load config at class load time, not fatal if missing
        reloadConfig();
    }

    /**
     * Public reload that will re-read config file and close existing connections.
     * Use this when user updates configuration.
     */
    public static synchronized void reloadConfig() {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) {
            props.load(fis);
        } catch (IOException e) {
            System.out.println("⚠️ Config file not found or unreadable: " + e.getMessage());
            // keep existing values (if any) or leave them null
        }
        HOST = props.getProperty("host", HOST);
        PORT = props.getProperty("port", PORT);
        INVENTORY_DB_NAME = props.getProperty("inventory_db_name", INVENTORY_DB_NAME != null ? INVENTORY_DB_NAME : "Inventory_DB");
        MANAGEMENT_DB_NAME = props.getProperty("management_db_name", MANAGEMENT_DB_NAME != null ? MANAGEMENT_DB_NAME : "Chemtech_management");
        USER = props.getProperty("user", USER);
        PASSWORD = props.getProperty("password", PASSWORD);
        // close old connections to force new ones using new config
        closeConnections();
    }

    /**
     * Close cached connections safely.
     */
    public static synchronized void closeConnections() {
        try {
            if (inventoryConnection != null && !inventoryConnection.isClosed()) {
                try { inventoryConnection.close(); } catch (Exception ignored) {}
            }
        } catch (SQLException ignored) {}
        inventoryConnection = null;

        try {
            if (managementConnection != null && !managementConnection.isClosed()) {
                try { managementConnection.close(); } catch (Exception ignored) {}
            }
        } catch (SQLException ignored) {}
        managementConnection = null;
    }

    private static String buildUrl(String dbName) {
        String host = HOST != null ? HOST : "localhost";
        String port = PORT != null ? PORT : "1433";
        return "jdbc:sqlserver://" + host + ":" + port +
                ";databaseName=" + dbName + ";encrypt=false;loginTimeout=5;";
    }

    /**
     * Return a valid inventory connection. Tries to reuse cached connection if valid,
     * otherwise creates a new one.
     */
    public static synchronized Connection getInventoryConnection() throws SQLException {
        // set a short login timeout to fail fast when network is down
        try { DriverManager.setLoginTimeout(5); } catch (Exception ignored) {}

        if (inventoryConnection != null) {
            try {
                if (!inventoryConnection.isClosed() && inventoryConnection.isValid(2)) {
                    return inventoryConnection;
                } else {
                    try { inventoryConnection.close(); } catch (Exception ignored) {}
                    inventoryConnection = null;
                }
            } catch (SQLException e) {
                // treat as invalid and recreate
                try { inventoryConnection.close(); } catch (Exception ignored) {}
                inventoryConnection = null;
            }
        }

        // Create a new connection
        String url = buildUrl(INVENTORY_DB_NAME != null ? INVENTORY_DB_NAME : "Inventory_DB");
        inventoryConnection = DriverManager.getConnection(url, USER, PASSWORD);
        return inventoryConnection;
    }

    public static synchronized Connection getManagementConnection() throws SQLException {
        try { DriverManager.setLoginTimeout(5); } catch (Exception ignored) {}

        if (managementConnection != null) {
            try {
                if (!managementConnection.isClosed() && managementConnection.isValid(2)) {
                    return managementConnection;
                } else {
                    try { managementConnection.close(); } catch (Exception ignored) {}
                    managementConnection = null;
                }
            } catch (SQLException e) {
                try { managementConnection.close(); } catch (Exception ignored) {}
                managementConnection = null;
            }
        }

        String url = buildUrl(MANAGEMENT_DB_NAME != null ? MANAGEMENT_DB_NAME : "Chemtech_management");
        managementConnection = DriverManager.getConnection(url, USER, PASSWORD);
        return managementConnection;
    }

    /**
     * Old compatibility method
     */
    public static Connection getConnection() throws SQLException {
        return getInventoryConnection();
    }

    /**
     * Simple test connection (returns message).
     */
    public static String testConnection() {
        try (Connection conn = getInventoryConnection()) {
            if (conn != null && !conn.isClosed() && conn.isValid(2)) {
                return "متصل بقاعدة البيانات بنجاح ✅";
            } else {
                return "فشل التحقق من الاتصال ❌";
            }
        } catch (Exception e) {
            return "فشل الاتصال بقاعدة البيانات ❌: " + e.getMessage();
        }
    }

    /**
     * Log action into Logs table. If DB is unreachable we print an error but avoid throwing.
     */
    public static void logAction(String actionType, String description) {
        try (Connection conn = getInventoryConnection()) {
            String sql = "INSERT INTO Logs (ActionType, Description, EmployeeID) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, actionType);
                stmt.setString(2, description);
                stmt.setInt(3, CurrentUser.getId());
                stmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("❌ فشل تسجيل الحدث في اللوج: " + e.getMessage());
        }
    }
}
