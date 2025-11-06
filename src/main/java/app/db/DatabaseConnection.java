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

    private static Connection inventoryConnection;
    private static Connection managementConnection;
    private static final String CONFIG_FILE =
            Paths.get(System.getProperty("user.home"), "warehouse_db_config.properties").toString();

    static {
        loadConfig();
    }

    private static void loadConfig() {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) {
            props.load(fis);
            HOST = props.getProperty("host");
            PORT = props.getProperty("port");
            INVENTORY_DB_NAME = props.getProperty("inventory_db_name", "Inventory_DB");
            MANAGEMENT_DB_NAME = props.getProperty("management_db_name", "Chemtech_management");
            USER = props.getProperty("user");
            PASSWORD = props.getProperty("password");
        } catch (IOException e) {
            System.out.println("⚠️ Config file not found!");
        }
    }

    // الاتصال بداتابيز المخزن (لعمليات النظام العادية)
    public static Connection getInventoryConnection() throws SQLException {
        if (inventoryConnection == null || inventoryConnection.isClosed()) {
            String url = "jdbc:sqlserver://" + HOST + ":" + PORT
                    + ";databaseName=" + INVENTORY_DB_NAME
                    + ";encrypt=false;useUnicode=true;characterEncoding=UTF-8;";
            inventoryConnection = DriverManager.getConnection(url, USER, PASSWORD);
        }
        return inventoryConnection;
    }

    // الاتصال بداتابيز الإدارة (للوجين فقط)
    public static Connection getManagementConnection() throws SQLException {
        if (managementConnection == null || managementConnection.isClosed()) {
            String url = "jdbc:sqlserver://" + HOST + ":" + PORT
                    + ";databaseName=" + MANAGEMENT_DB_NAME
                    + ";encrypt=false;useUnicode=true;characterEncoding=UTF-8;";
            managementConnection = DriverManager.getConnection(url, USER, PASSWORD);
        }
        return managementConnection;
    }

    // للتوافق مع الكود القديم
    public static Connection getConnection() throws SQLException {
        return getInventoryConnection();
    }

    public static String testConnection() {
        try {
            Connection conn = getInventoryConnection();
            if (conn != null && !conn.isClosed()) {
                return "متصل بقاعدة البيانات بنجاح ✅";
            }
        } catch (Exception e) {
            return "فشل الاتصال بقاعدة البيانات ❌";
        }
        return "حدث خطأ غير معروف ⚠️";
    }

    public static void logAction(String actionType, String description) {
        try (Connection conn = getInventoryConnection()) {
            String sql = "INSERT INTO Logs (ActionType, Description, EmployeeID) VALUES (?, ?, ?)";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, actionType);
            stmt.setString(2, description);
            stmt.setInt(3, CurrentUser.getId());
            stmt.executeUpdate();
        } catch (Exception e) {
            System.err.println("❌ فشل تسجيل الحدث في اللوج: " + e.getMessage());
        }
    }

}