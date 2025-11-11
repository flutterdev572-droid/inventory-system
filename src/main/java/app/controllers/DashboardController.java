package app.controllers;

import app.db.DatabaseConnection;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
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
    @FXML private Label loggedUserLabel;
    @FXML private Label totalDevicesLabel;
    @FXML private VBox lastTransactionContainer;
    @FXML private VBox sidebarDrawer;
    @FXML private VBox mainContentArea;

    private boolean isSidebarOpen = true;
    private TranslateTransition sidebarTransition;
    private TranslateTransition contentTransition;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public void setLoggedEmployeeName(String name) {
        if (loggedUserLabel != null) {
            loggedUserLabel.setText("مرحباً: " + name);
        }
    }

    @FXML
    public void initialize() {
        // تهيئة الـ animations
        setupAnimations();

        // الكود الأصلي للاتصال بقاعدة البيانات
        String status = DatabaseConnection.testConnection();
        dbStatusLabel.setText(status);
        if (status.contains("نجاح")) {
            dbStatusLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
        } else if (status.contains("فشل")) {
            dbStatusLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
        } else {
            dbStatusLabel.setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
        }

        loadDashboardStats();

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.seconds(5), event -> loadDashboardStats())
        );
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void setupAnimations() {
        // animation للسايدبار
        sidebarTransition = new TranslateTransition(Duration.millis(300), sidebarDrawer);

        // animation للمحتوى الرئيسي
        contentTransition = new TranslateTransition(Duration.millis(300), mainContentArea);
    }

    @FXML
    private void toggleSidebar() {
        if (isSidebarOpen) {
            // إغلاق السايدبار - يتحرك لليسار خارج الشاشة
            sidebarTransition.setToX(-280);
            // تعديل الـ anchors للمحتوى الرئيسي ليشمل المساحة كاملة
            AnchorPane.setLeftAnchor(mainContentArea, 25.0);
        } else {
            // فتح السايدبار - يعود لوضعه الطبيعي
            sidebarTransition.setToX(0);
            // إعادة الـ anchors للمحتوى الرئيسي لوضعه الأصلي
            AnchorPane.setLeftAnchor(mainContentArea, 295.0);
        }

        // تشغيل الـ animations
        sidebarTransition.play();
        contentTransition.play();

        isSidebarOpen = !isSidebarOpen;
    }

    private String formatNumber(int number) {
        if (number >= 1_000_000) {
            double millions = number / 1_000_000.0;
            return String.format("%.1f مليون", millions);
        } else if (number >= 1_000) {
            double thousands = number / 1_000.0;
            return String.format("%.1f ألف", thousands);
        } else {
            return String.valueOf(number);
        }
    }

    private void loadDashboardStats() {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getInventoryConnection();

            if (conn == null) {
                showDisconnectedStatus("⚠ لا يوجد اتصال بقاعدة البيانات");
                return;
            }

            dbStatusLabel.setText("✅ متصل بقاعدة البيانات");
            dbStatusLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");

            PreparedStatement stmt;
            ResultSet rs;

            stmt = conn.prepareStatement("SELECT COUNT(*) AS total FROM Items");
            rs = stmt.executeQuery();
            if (rs.next()) totalItemsLabel.setText(formatNumber(rs.getInt("total")));

            stmt = conn.prepareStatement("""
                SELECT COUNT(*) AS low_stock
                FROM Items i
                JOIN StockBalances s ON i.ItemID = s.ItemID
                WHERE s.Quantity < i.MinQuantity
            """);
            rs = stmt.executeQuery();
            if (rs.next()) lowStockLabel.setText(formatNumber(rs.getInt("low_stock")));

            stmt = conn.prepareStatement("SELECT COUNT(*) AS total_devices FROM Devices");
            rs = stmt.executeQuery();
            if (rs.next()) totalDevicesLabel.setText(formatNumber(rs.getInt("total_devices")));

            stmt = conn.prepareStatement("SELECT COUNT(*) AS total_trans FROM StockTransactions");
            rs = stmt.executeQuery();
            if (rs.next()) totalTransactionsLabel.setText(formatNumber(rs.getInt("total_trans")));

            stmt = conn.prepareStatement("SELECT ISNULL(SUM(Quantity), 0) AS total_in FROM StockTransactions WHERE TransactionType = 'IN'");
            rs = stmt.executeQuery();
            if (rs.next()) totalInLabel.setText(formatNumber(rs.getInt("total_in")));

            stmt = conn.prepareStatement("SELECT ISNULL(SUM(Quantity), 0) AS total_out FROM StockTransactions WHERE TransactionType = 'OUT'");
            rs = stmt.executeQuery();
            if (rs.next()) totalOutLabel.setText(formatNumber(rs.getInt("total_out")));

            stmt = conn.prepareStatement("""
                SELECT TOP 1 
                    st.TransactionType, st.Quantity, st.TransactionDate,
                    st.ReceiverName, st.Notes, i.ItemName, u.UnitName,
                    e.name AS EmployeeName
                FROM StockTransactions st
                LEFT JOIN Items i ON st.ItemID = i.ItemID
                LEFT JOIN Units u ON i.UnitID = u.UnitID
                LEFT JOIN Chemtech_management.dbo.Employees e ON st.EmployeeID = e.employee_id
                ORDER BY st.TransactionDate DESC
            """);
            rs = stmt.executeQuery();

            lastTransactionContainer.getChildren().clear();

            if (rs.next()) {
                VBox card = buildTransactionCard(
                        rs.getString("TransactionType"),
                        rs.getDouble("Quantity"),
                        rs.getString("ItemName"),
                        rs.getString("UnitName"),
                        rs.getString("ReceiverName"),
                        rs.getString("Notes"),
                        rs.getString("EmployeeName"),
                        dateFormat.format(rs.getTimestamp("TransactionDate"))
                );
                lastTransactionContainer.getChildren().add(card);
            } else {
                Label noData = new Label("لا توجد معاملات بعد");
                noData.setStyle("-fx-text-fill: #475569; -fx-font-size: 14px; -fx-font-weight: bold;");
                lastTransactionContainer.getChildren().add(noData);
            }

        } catch (Exception e) {
            showDisconnectedStatus("❌ فشل الاتصال بالسيرفر - سيتم إعادة المحاولة خلال 10 ثواني");
            System.err.println("❌ خطأ أثناء تحميل الإحصائيات: " + e.getMessage());

            Timeline retryTimeline = new Timeline(new KeyFrame(Duration.seconds(10), ev -> loadDashboardStats()));
            retryTimeline.setCycleCount(1);
            retryTimeline.play();

        } finally {
            if (conn != null) try { conn.close(); } catch (Exception ignored) {}
        }
    }

    private VBox buildTransactionCard(String type, double quantity, String itemName,
                                      String unitName, String receiver, String notes,
                                      String employee, String date) {

        VBox card = new VBox(8);
        card.setPadding(new javafx.geometry.Insets(12));
        card.setBackground(new Background(new BackgroundFill(
                Color.web(type.equals("IN") ? "#ecfdf5" : "#fef2f2"),
                new CornerRadii(12), javafx.geometry.Insets.EMPTY
        )));
        card.setBorder(new Border(new BorderStroke(
                Color.web(type.equals("IN") ? "#10b981" : "#ef4444"),
                BorderStrokeStyle.SOLID, new CornerRadii(12), new BorderWidths(1)
        )));

        Label title = new Label(type.equals("IN") ? "🟢 عملية إضافة" : "🔴 عملية صرف");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

        Label item = new Label("📦 الصنف: " + (itemName != null ? itemName : "صنف محذوف"));
        Label qty = new Label("🔢 الكمية: " + quantity + " " + (unitName != null ? unitName : "وحدة"));
        Label emp = new Label("👷‍♂ الموظف: " + (employee != null ? employee : "غير معروف"));

        VBox infoBox = new VBox(5, item, qty, emp);

        if ("OUT".equals(type) && receiver != null && !receiver.isEmpty() && !receiver.equals("System")) {
            infoBox.getChildren().add(new Label("👤 المستلم: " + receiver));
        }

        infoBox.getChildren().add(new Label("🕒 التاريخ: " + date));

        if (notes != null && !notes.isEmpty()) {
            infoBox.getChildren().add(new Label("📝 ملاحظات: " + notes));
        }

        card.getChildren().addAll(title, new javafx.scene.control.Separator(), infoBox);
        return card;
    }

    private void showDisconnectedStatus(String message) {
        dbStatusLabel.setText(message);
        dbStatusLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
        lastTransactionContainer.getChildren().setAll(new Label("🚫 تعذر تحميل آخر معاملة بسبب انقطاع الاتصال"));
        totalItemsLabel.setText("--");
        lowStockLabel.setText("--");
        totalDevicesLabel.setText("--");
        totalTransactionsLabel.setText("--");
        totalInLabel.setText("--");
        totalOutLabel.setText("--");
    }

    @FXML
    private void logout() {
        try {
            Stage currentStage = (Stage) loggedUserLabel.getScene().getWindow();
            currentStage.close();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/login.fxml"));
            Parent root = loader.load();

            Stage loginStage = new Stage();
            currentStage.setMaximized(true);
            loginStage.setTitle("تسجيل الدخول");
            loginStage.setScene(new Scene(root));
            loginStage.show();

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("❌ خطأ أثناء تسجيل الخروج: " + e.getMessage());
        }
    }

    @FXML private void openAddItemPage() { openPage("/views/AddItems.fxml", "إضافة صنف جديد"); }
    @FXML private void openInventoryManagement() { openPage("/views/StockView.fxml", "إدارة المخزون"); }
    @FXML private void openAddDevicePage() { openPage("/views/AddDevice.fxml", "تسجيل جهاز جديد"); }
    @FXML private void openDevicesPage() { openPage("/views/DevicesManagement.fxml", "إدارة الأجهزة"); }
    @FXML private void openSerialTracking() { openPage("/views/SerialTrackingView.fxml", "تتبع السيريالات"); }
    @FXML private void onScrapMaintenanceClicked() { openPage("/views/ScrapMaintenanceView.fxml", "الأجهزة التالفة والصيانة"); }
    @FXML
    private void openPricingPage() {
        openPage("/views/PricingView.fxml", "💰 إدارة تسعير الأصناف");
    }
    @FXML
    private void openDeviceExitPage() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/DeviceExitView.fxml"));
        Parent root = loader.load();
        Stage stage = new Stage();
        stage.setScene(new Scene(root));
        stage.setTitle("الأجهزة الخارجة من المصنع");
        stage.show();
    }
    @FXML
    private void openRequestView() {
        openPage("/views/AdminRequests.fxml", "📦 طلبات الصرف");
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

    private void openPage(String fxmlPath, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle(title);
            stage.setScene(new Scene(root));
            stage.setResizable(true);
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("❌ خطأ أثناء فتح الصفحة: " + e.getMessage());
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("خطأ في فتح الصفحة");
            alert.setHeaderText("تعذر فتح الصفحة: " + title);
            alert.setContentText("الرجاء التحقق من وجود الملف والمسار والمكتبات المطلوبة.\n\n" + e.getMessage());
            alert.showAndWait();
        }
    }
}