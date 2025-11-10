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

    // إزالة التخزين المؤقت للاتصالات - هذا هو مصدر المشكلة
    // private static Connection inventoryConnection;
    // private static Connection managementConnection;

    private static final String CONFIG_FILE =
            Paths.get(System.getProperty("user.home"), "warehouse_db_config.properties").toString();

    static {
        reloadConfig();
    }

    public static synchronized void reloadConfig() {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) {
            props.load(fis);
        } catch (IOException e) {
            System.out.println("⚠️ Config file not found or unreadable: " + e.getMessage());
        }
        HOST = props.getProperty("host", HOST);
        PORT = props.getProperty("port", PORT);
        INVENTORY_DB_NAME = props.getProperty("inventory_db_name", INVENTORY_DB_NAME != null ? INVENTORY_DB_NAME : "Inventory_DB");
        MANAGEMENT_DB_NAME = props.getProperty("management_db_name", MANAGEMENT_DB_NAME != null ? MANAGEMENT_DB_NAME : "Chemtech_management");
        USER = props.getProperty("user", USER);
        PASSWORD = props.getProperty("password", PASSWORD);
    }

    private static String buildUrl(String dbName) {
        String host = HOST != null ? HOST : "localhost";
        String port = PORT != null ? PORT : "1433";
        return "jdbc:sqlserver://" + host + ":" + port +
                ";databaseName=" + dbName + ";encrypt=false;loginTimeout=5;";
    }

    /**
     * إنشاء اتصال جديد في كل مرة - هذا هو الحل الآمن
     */
    public static Connection getInventoryConnection() throws SQLException {
        try {
            DriverManager.setLoginTimeout(5);
        } catch (Exception ignored) {}

        String url = buildUrl(INVENTORY_DB_NAME != null ? INVENTORY_DB_NAME : "Inventory_DB");
        return DriverManager.getConnection(url, USER, PASSWORD);
    }

    public static Connection getManagementConnection() throws SQLException {
        try {
            DriverManager.setLoginTimeout(5);
        } catch (Exception ignored) {}

        String url = buildUrl(MANAGEMENT_DB_NAME != null ? MANAGEMENT_DB_NAME : "Chemtech_management");
        return DriverManager.getConnection(url, USER, PASSWORD);
    }

    /**
     * للتوافق مع الكود القديم
     */
    public static Connection getConnection() throws SQLException {
        return getInventoryConnection();
    }

    /**
     * اختبار الاتصال - بدون استخدام try-with-resources
     */
    public static String testConnection() {
        Connection conn = null;
        try {
            conn = getInventoryConnection();
            if (conn != null && !conn.isClosed()) {
                // اختيار بسيط للتأكد من أن الاتصال يعمل
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeQuery("SELECT 1");
                }
                return "متصل بقاعدة البيانات بنجاح ✅";
            } else {
                return "فشل التحقق من الاتصال ❌";
            }
        } catch (Exception e) {
            return "فشل الاتصال بقاعدة البيانات ❌: " + e.getMessage();
        } finally {
            // إغلاق الاتصال يدوياً
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    System.err.println("خطأ في إغلاق الاتصال: " + e.getMessage());
                }
            }
        }
    }

    /**
     * تسجيل الإجراءات
     */
    public static void logAction(String actionType, String description) {
        Connection conn = null;
        PreparedStatement stmt = null;
        try {
            conn = getInventoryConnection();
            String sql = "INSERT INTO Logs (ActionType, Description, EmployeeID) VALUES (?, ?, ?)";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, actionType);
            stmt.setString(2, description);
            stmt.setInt(3, CurrentUser.getId());
            stmt.executeUpdate();
        } catch (Exception e) {
            System.err.println("❌ فشل تسجيل الحدث في اللوج: " + e.getMessage());
        } finally {
            // إغلاق الموارد يدوياً
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}