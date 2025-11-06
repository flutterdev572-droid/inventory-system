package app.controllers;

import app.db.DatabaseConnection;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;

public class DashboardController {

    @FXML private Label dbStatusLabel;
    @FXML private Label totalItemsLabel;
    @FXML private Label lowStockLabel;
    @FXML private Label totalTransactionsLabel;
    @FXML private Label totalInLabel;
    @FXML private Label totalOutLabel;
    @FXML private Label lastTransactionLabel;
    @FXML private Label loggedUserLabel;
    @FXML private Label totalDevicesLabel;


    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public void setLoggedEmployeeName(String name) {
        if (loggedUserLabel != null) {
            loggedUserLabel.setText("مرحباً: " + name);
        }
    }

    @FXML
    public void initialize() {
        // عرض حالة الاتصال
        String status = DatabaseConnection.testConnection();
        dbStatusLabel.setText(status);
        if (status.contains("نجاح")) {
            dbStatusLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
        } else if (status.contains("فشل")) {
            dbStatusLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
        } else {
            dbStatusLabel.setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
        }

        // تحميل الإحصائيات أول مرة
        loadDashboardStats();

        // ✅ تحديث تلقائي كل 5 ثواني
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.seconds(5), event -> loadDashboardStats())
        );
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void loadDashboardStats() {
        try (Connection conn = DatabaseConnection.getInventoryConnection()) {

            // إجمالي الأصناف
            String totalItemsSQL = "SELECT COUNT(*) AS total FROM Items";
            PreparedStatement stmt = conn.prepareStatement(totalItemsSQL);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                totalItemsLabel.setText(String.valueOf(rs.getInt("total")));
            }

            // الأصناف منخفضة المخزون
            String lowStockSQL = """
            SELECT COUNT(*) AS low_stock
            FROM Items i
            JOIN StockBalances s ON i.ItemID = s.ItemID
            WHERE s.Quantity < i.MinQuantity
        """;
            stmt = conn.prepareStatement(lowStockSQL);
            rs = stmt.executeQuery();
            if (rs.next()) {
                lowStockLabel.setText(String.valueOf(rs.getInt("low_stock")));
            }
            // ✅ إجمالي الأجهزة
            String totalDevicesSQL = "SELECT COUNT(*) AS total_devices FROM Devices";
            stmt = conn.prepareStatement(totalDevicesSQL);
            rs = stmt.executeQuery();
            if (rs.next()) {
                totalDevicesLabel.setText(String.valueOf(rs.getInt("total_devices")));
            }


            // إجمالي المعاملات
            String totalTransSQL = "SELECT COUNT(*) AS total_trans FROM StockTransactions";
            stmt = conn.prepareStatement(totalTransSQL);
            rs = stmt.executeQuery();
            if (rs.next()) {
                totalTransactionsLabel.setText(String.valueOf(rs.getInt("total_trans")));
            }

            // إجمالي الكميات المضافة
            String totalInSQL = "SELECT ISNULL(SUM(Quantity), 0) AS total_in FROM StockTransactions WHERE TransactionType = 'IN'";
            stmt = conn.prepareStatement(totalInSQL);
            rs = stmt.executeQuery();
            if (rs.next()) {
                totalInLabel.setText(String.valueOf(rs.getDouble("total_in")));
            }

            // إجمالي الكميات المصروفة
            String totalOutSQL = "SELECT ISNULL(SUM(Quantity), 0) AS total_out FROM StockTransactions WHERE TransactionType = 'OUT'";
            stmt = conn.prepareStatement(totalOutSQL);
            rs = stmt.executeQuery();
            if (rs.next()) {
                totalOutLabel.setText(String.valueOf(rs.getDouble("total_out")));
            }


            // ✅ آخر معاملة مع تفاصيل كاملة (بما في ذلك الوحدة)
            String lastTransSQL = """
                SELECT TOP 1 
                    st.TransactionType,
                    st.Quantity,
                    st.TransactionDate,
                    st.ReceiverName,
                    st.Notes,
                    i.ItemName,
                    u.UnitName,  -- ✅ إضافة الوحدة
                    e.name AS EmployeeName
                FROM StockTransactions st
                LEFT JOIN Items i ON st.ItemID = i.ItemID
                LEFT JOIN Units u ON i.UnitID = u.UnitID  -- ✅ JOIN مع جدول الوحدات
                LEFT JOIN Chemtech_management.dbo.Employees e ON st.EmployeeID = e.employee_id
                ORDER BY st.TransactionDate DESC
            """;
            stmt = conn.prepareStatement(lastTransSQL);
            rs = stmt.executeQuery();
            if (rs.next()) {
                String transactionType = rs.getString("TransactionType");
                double quantity = rs.getDouble("Quantity");
                String itemName = rs.getString("ItemName");
                String unitName = rs.getString("UnitName");  // ✅ الوحدة
                String receiverName = rs.getString("ReceiverName");
                String notes = rs.getString("Notes");
                String employeeName = rs.getString("EmployeeName");
                String date = dateFormat.format(rs.getTimestamp("TransactionDate"));

                // ✅ بناء نص واضح للعملية بالتنسيق الجديد مع الوحدة
                String transactionText = buildTransactionText(
                        transactionType, quantity, itemName, unitName, receiverName,
                        notes, employeeName, date
                );
                lastTransactionLabel.setText(transactionText);
            } else {
                lastTransactionLabel.setText("لا توجد معاملات بعد");
            }

        } catch (Exception e) {
            e.printStackTrace();
            dbStatusLabel.setText("❌ Error loading stats");
            lastTransactionLabel.setText("خطأ في تحميل آخر معاملة");
        }
    }

    // ✅ دالة لبناء نص واضح ومنسّق للعملية مع الوحدة
    private String buildTransactionText(String type, double quantity, String itemName,
                                        String unitName, String receiver, String notes,
                                        String employee, String date) {
        StringBuilder text = new StringBuilder();

        // 🔹 نوع العملية
        if ("IN".equals(type)) {
            text.append("🟢 عملية إضافة\n");
        } else {
            text.append("🔴 عملية صرف\n");
        }

        text.append("━━━━━━━━━━━━━━━━━━━━━━\n");

        // 📦 الصنف
        text.append("الصنف: ").append(itemName != null ? itemName : "صنف محذوف").append("\n");

        // 🔢 الكمية والوحدة
        String displayUnit = (unitName != null && !unitName.isEmpty()) ? unitName : "وحدة";
        text.append("🔢 الكمية: ").append(quantity).append(" ").append(displayUnit).append("\n");

        // 👷‍♂️ الموظف
        text.append("👷‍♂️ الموظف: ").append(employee != null ? employee : "غير معروف").append("\n");

        // 👤 المستلم (في حالة الصرف فقط)
        if ("OUT".equals(type) && receiver != null && !receiver.isEmpty() && !receiver.equals("System")) {
            text.append("👤 المستلم: ").append(receiver).append("\n");
        }

        // 🕒 التاريخ والوقت
        text.append("🕒 التاريخ: ").append(date).append("\n");

        // 📝 الملاحظات (إن وُجدت)
        if (notes != null && !notes.isEmpty()) {
            text.append("📝 ملاحظات: ").append(notes).append("\n");
        }

        text.append("━━━━━━━━━━━━━━━━━━━━━━");

        return text.toString();
    }

    @FXML
    private void logout() {
        try {
            // إغلاق الشاشة الحالية
            Stage currentStage = (Stage) loggedUserLabel.getScene().getWindow();
            currentStage.close();

            // تحميل صفحة تسجيل الدخول
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/login.fxml"));
            Parent root = loader.load();

            Stage loginStage = new Stage();
            loginStage.setTitle("تسجيل الدخول");
            loginStage.setScene(new Scene(root));
            loginStage.show();

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("❌ خطأ أثناء تسجيل الخروج: " + e.getMessage());
        }
    }


    @FXML
    private void openAddItemPage() {
        openPage("/views/AddItems.fxml", "إضافة صنف جديد");
    }

    @FXML
    private void openInventoryManagement() {
        openPage("/views/StockView.fxml", "إدارة المخزون");
    }


    @FXML
    private void openReports() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/ReportsView.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("📊 التقارير والإحصائيات");
            stage.setScene(new Scene(root));
            stage.setMaximized(true);
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @FXML
    private void openAddDevicePage() {
        openPage("/views/AddDevice.fxml", "تسجيل جهاز جديد");
    }
    @FXML
    private void openDevicesPage() {
        openPage("/views/DevicesManagement.fxml", "إدارة الأجهزة");
    }
    @FXML
    private void openSerialTracking() {
        openPage("/views/SerialTrackingView.fxml", "تتبع السيريالات");
    }
    @FXML
    private void onScrapMaintenanceClicked() {
        openPage("/views/ScrapMaintenanceView.fxml", "تتبع السيريالات");
    }





    @FXML
    private void openPage(String fxmlPath, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle(title);
            stage.setScene(new Scene(root));
            stage.show();
        } catch (Exception e) {
            System.out.println("❌ خطأ أثناء فتح الصفحة: " + e.getMessage());
        }
    }
}