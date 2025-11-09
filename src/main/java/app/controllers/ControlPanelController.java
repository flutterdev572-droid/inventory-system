package app.controllers;

import app.db.DatabaseConnection;
import app.models.PermissionManager;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.net.URL;
import java.sql.*;
import java.util.ResourceBundle;

public class ControlPanelController implements Initializable {

    @FXML private TextField employeeIdField;
    @FXML private Label employeeNameLabel;
    @FXML private CheckBox canAddItemsCheck;
    @FXML private CheckBox canManageInventoryCheck;
    @FXML private CheckBox canAddDevicesCheck;
    @FXML private CheckBox canManageDevicesCheck;
    @FXML private CheckBox canTrackSerialsCheck;
    @FXML private CheckBox canViewReportsCheck;
    @FXML private CheckBox canScrapMaintenanceCheck;
    @FXML private CheckBox canManagePricingCheck;
    @FXML private CheckBox canAccessControlPanelCheck;
    @FXML private TableView<ActiveUser> activeUsersTable;

    private int currentEmployeeId;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadActiveUsers();
    }

    @FXML
    private void searchEmployee() {
        String employeeId = employeeIdField.getText().trim();

        if (employeeId.isEmpty()) {
            showAlert("خطأ", "يرجى إدخال رقم الموظف");
            return;
        }

        try {
            currentEmployeeId = Integer.parseInt(employeeId);
            Connection conn = DatabaseConnection.getConnection();

            // البحث في جدول الموظفين
            String sql = "SELECT name FROM Chemtech_management.dbo.Employees WHERE employee_id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, currentEmployeeId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String employeeName = rs.getString("name");
                employeeNameLabel.setText("اسم الموظف: " + employeeName);
                loadEmployeePermissions(currentEmployeeId);
            } else {
                employeeNameLabel.setText("اسم الموظف: غير موجود");
                clearPermissions();
                showAlert("خطأ", "رقم الموظف غير موجود في النظام");
            }

            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("خطأ", "حدث خطأ أثناء البحث عن الموظف");
        }
    }

    private void loadEmployeePermissions(int employeeId) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "SELECT * FROM EmployeePermissions WHERE EmployeeID = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, employeeId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                // تحميل الصلاحيات من الداتا بيز
                canAddItemsCheck.setSelected(rs.getBoolean("CanAddItems"));
                canManageInventoryCheck.setSelected(rs.getBoolean("CanManageInventory"));
                canAddDevicesCheck.setSelected(rs.getBoolean("CanAddDevices"));
                canManageDevicesCheck.setSelected(rs.getBoolean("CanManageDevices"));
                canTrackSerialsCheck.setSelected(rs.getBoolean("CanTrackSerials"));
                canViewReportsCheck.setSelected(rs.getBoolean("CanViewReports"));
                canScrapMaintenanceCheck.setSelected(rs.getBoolean("CanScrapMaintenance"));
                canManagePricingCheck.setSelected(rs.getBoolean("CanManagePricing"));
                canAccessControlPanelCheck.setSelected(rs.getBoolean("CanAccessControlPanel"));
            } else {
                // إذا الموظف مش موجود في جدول الصلاحيات، ننشئ له واحد جديد
                clearPermissions();
            }

            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("خطأ", "حدث خطأ أثناء تحميل الصلاحيات");
        }
    }

    @FXML
    private void savePermissions() {
        if (currentEmployeeId == 0) {
            showAlert("خطأ", "يرجى البحث عن موظف أولاً");
            return;
        }

        try {
            Connection conn = DatabaseConnection.getConnection();

            // Check if permissions already exist
            String checkSql = "SELECT COUNT(*) FROM EmployeePermissions WHERE EmployeeID = ?";
            PreparedStatement checkStmt = conn.prepareStatement(checkSql);
            checkStmt.setInt(1, currentEmployeeId);
            ResultSet rs = checkStmt.executeQuery();
            rs.next();
            boolean exists = rs.getInt(1) > 0;

            String sql;
            if (exists) {
                // Update existing permissions
                sql = "UPDATE EmployeePermissions SET " +
                        "CanAddItems = ?, CanManageInventory = ?, CanAddDevices = ?, " +
                        "CanManageDevices = ?, CanTrackSerials = ?, CanViewReports = ?, " +
                        "CanScrapMaintenance = ?, CanManagePricing = ?, CanAccessControlPanel = ?, " +
                        "UpdatedDate = GETDATE() WHERE EmployeeID = ?";
            } else {
                // Insert new permissions
                sql = "INSERT INTO EmployeePermissions (EmployeeID, CanAddItems, CanManageInventory, " +
                        "CanAddDevices, CanManageDevices, CanTrackSerials, CanViewReports, " +
                        "CanScrapMaintenance, CanManagePricing, CanAccessControlPanel) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            }

            PreparedStatement stmt = conn.prepareStatement(sql);
            int paramIndex = 1;

            if (exists) {
                stmt.setBoolean(paramIndex++, canAddItemsCheck.isSelected());
                stmt.setBoolean(paramIndex++, canManageInventoryCheck.isSelected());
                stmt.setBoolean(paramIndex++, canAddDevicesCheck.isSelected());
                stmt.setBoolean(paramIndex++, canManageDevicesCheck.isSelected());
                stmt.setBoolean(paramIndex++, canTrackSerialsCheck.isSelected());
                stmt.setBoolean(paramIndex++, canViewReportsCheck.isSelected());
                stmt.setBoolean(paramIndex++, canScrapMaintenanceCheck.isSelected());
                stmt.setBoolean(paramIndex++, canManagePricingCheck.isSelected());
                stmt.setBoolean(paramIndex++, canAccessControlPanelCheck.isSelected());
                stmt.setInt(paramIndex++, currentEmployeeId);
            } else {
                stmt.setInt(paramIndex++, currentEmployeeId);
                stmt.setBoolean(paramIndex++, canAddItemsCheck.isSelected());
                stmt.setBoolean(paramIndex++, canManageInventoryCheck.isSelected());
                stmt.setBoolean(paramIndex++, canAddDevicesCheck.isSelected());
                stmt.setBoolean(paramIndex++, canManageDevicesCheck.isSelected());
                stmt.setBoolean(paramIndex++, canTrackSerialsCheck.isSelected());
                stmt.setBoolean(paramIndex++, canViewReportsCheck.isSelected());
                stmt.setBoolean(paramIndex++, canScrapMaintenanceCheck.isSelected());
                stmt.setBoolean(paramIndex++, canManagePricingCheck.isSelected());
                stmt.setBoolean(paramIndex++, canAccessControlPanelCheck.isSelected());
            }

            stmt.executeUpdate();
            conn.close();

            showAlert("نجاح", "تم حفظ الصلاحيات بنجاح");

            // تحديث الـ Dashboard فوراً للمستخدم إذا كان شغال
            PermissionManager.refreshUserPermissions(currentEmployeeId);

        } catch (Exception e) {
            e.printStackTrace();
            showAlert("خطأ", "حدث خطأ أثناء حفظ الصلاحيات");
        }
    }

    @FXML
    private void resetPermissions() {
        clearPermissions();
    }

    private void clearPermissions() {
        canAddItemsCheck.setSelected(false);
        canManageInventoryCheck.setSelected(false);
        canAddDevicesCheck.setSelected(false);
        canManageDevicesCheck.setSelected(false);
        canTrackSerialsCheck.setSelected(false);
        canViewReportsCheck.setSelected(false);
        canScrapMaintenanceCheck.setSelected(false);
        canManagePricingCheck.setSelected(false);
        canAccessControlPanelCheck.setSelected(false);
    }

    private void loadActiveUsers() {
        // هنا هتحمل قائمة المستخدمين النشطين من الـ Session
        // ده مثال بسيط
        ObservableList<ActiveUser> activeUsers = FXCollections.observableArrayList();
        activeUsersTable.setItems(activeUsers);
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // كلاس مساعد لعرض المستخدمين النشطين
    public static class ActiveUser {
        private final int employeeId;
        private final String employeeName;
        private final String lastActivity;

        public ActiveUser(int employeeId, String employeeName, String lastActivity) {
            this.employeeId = employeeId;
            this.employeeName = employeeName;
            this.lastActivity = lastActivity;
        }

        // Getters
        public int getEmployeeId() { return employeeId; }
        public String getEmployeeName() { return employeeName; }
        public String getLastActivity() { return lastActivity; }
    }
}