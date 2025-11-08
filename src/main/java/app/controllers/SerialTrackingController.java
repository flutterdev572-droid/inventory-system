package app.controllers;

import app.db.DatabaseConnection;
import app.current_user.CurrentUser;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.File;
import java.io.FileOutputStream;
import java.sql.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javax.imageio.ImageIO;
import javafx.stage.FileChooser;

public class SerialTrackingController {

    @FXML private ComboBox<Device> deviceCombo;
    @FXML private ComboBox<String> serialCombo;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private Button refreshSerialsBtn;
    @FXML private Button showExceededBtn;
    @FXML private Button showAllBtn;
    @FXML private Button exportExcelBtn;
    @FXML private TableView<UsageRow> usageTable;
    @FXML private TableColumn<UsageRow, String> colItem;
    @FXML private TableColumn<UsageRow, String> colExpected;
    @FXML private TableColumn<UsageRow, String> colUsed;
    @FXML private TableColumn<UsageRow, String> colStatus;
    @FXML private TableColumn<UsageRow, String> colDate;
    @FXML private TableColumn<UsageRow, String> colUser;
    @FXML private Button priceSerialBtn;
    @FXML private Button showExceededSerialsBtn;
    @FXML private Button showAllSerialsBtn;

    private final ObservableList<UsageRow> usageList = FXCollections.observableArrayList();
    private final FilteredList<UsageRow> filteredUsageList = new FilteredList<>(usageList);
    private ObservableList<String> masterSerials = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colItem.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        colExpected.setCellValueFactory(new PropertyValueFactory<>("expected"));
        colUsed.setCellValueFactory(new PropertyValueFactory<>("used"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("usedAt"));
        colUser.setCellValueFactory(new PropertyValueFactory<>("usedBy"));

        usageTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(UsageRow row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) {
                    setStyle("");
                } else if (row.getStatus().contains("تجاوز")) {
                    setStyle("-fx-background-color: rgba(255,100,100,0.5); -fx-font-weight: bold;");
                } else {
                    setStyle("");
                }
            }
        });

        usageTable.setItems(filteredUsageList);
        serialCombo.setEditable(true);
        serialCombo.getEditor().addEventFilter(KeyEvent.KEY_RELEASED, this::onSerialFilterKey);
        deviceCombo.setOnAction(e -> onDeviceSelected());
        refreshSerialsBtn.setOnAction(e -> onDeviceSelected());
        showExceededBtn.setOnAction(e -> onShowExceeded());
        showAllBtn.setOnAction(e -> onShowAll());
        exportExcelBtn.setOnAction(e -> onExportToExcel());
        showExceededSerialsBtn.setOnAction(e -> onShowExceededSerials());
        showAllSerialsBtn.setOnAction(e -> onShowAllSerials());
        loadDevices();
    }

    private void onSerialFilterKey(KeyEvent event) {
        String text = serialCombo.getEditor().getText();
        filterSerials(text);
    }

    private void filterSerials(String filter) {
        if (filter == null || filter.isBlank()) {
            serialCombo.setItems(masterSerials);
            return;
        }
        final String f = filter.toLowerCase();
        ObservableList<String> filtered = FXCollections.observableArrayList();
        for (String s : masterSerials) {
            if (s.toLowerCase().contains(f)) filtered.add(s);
        }
        serialCombo.setItems(filtered);
        serialCombo.getEditor().setText(filter);
        serialCombo.show();
    }

    // تحميل الأجهزة بدون إظهار ID
    private void loadDevices() {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT DeviceID, DeviceName FROM Devices ORDER BY DeviceName")) {
            ResultSet rs = ps.executeQuery();
            ObservableList<Device> list = FXCollections.observableArrayList();
            while (rs.next()) {
                list.add(new Device(rs.getInt("DeviceID"), rs.getString("DeviceName")));
            }
            deviceCombo.setItems(list);
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("حدث خطأ أثناء تحميل الأجهزة.");
        }
    }

    @FXML
    private void onDeviceSelected() {
        Device selectedDevice = deviceCombo.getValue();
        if (selectedDevice == null) {
            masterSerials.clear();
            serialCombo.setItems(masterSerials);
            return;
        }

        int deviceId = selectedDevice.getId();

        LocalDate start = startDatePicker.getValue();
        LocalDate end = endDatePicker.getValue();

        masterSerials.clear();

        StringBuilder sql = new StringBuilder();
        sql.append("""
            SELECT DS.SerialNumber, MAX(DS.CreatedAt) AS CreatedAt
            FROM DeviceSerials DS
            """);

        if (start != null && end != null) {
            sql.append("JOIN SerialComponentUsage SCU ON SCU.SerialID = DS.SerialID ");
        }

        sql.append("WHERE DS.DeviceID = ? ");

        if (start != null && end != null) {
            sql.append("AND SCU.UsedAt BETWEEN ? AND ? ");
        }

        sql.append("""
            GROUP BY DS.SerialNumber
            ORDER BY MAX(DS.CreatedAt) DESC
            """);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            ps.setInt(1, deviceId);
            if (start != null && end != null) {
                Timestamp tsStart = Timestamp.from(start.atStartOfDay(ZoneId.systemDefault()).toInstant());
                Timestamp tsEnd = Timestamp.from(end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().minusSeconds(1));
                ps.setTimestamp(2, tsStart);
                ps.setTimestamp(3, tsEnd);
            }

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                masterSerials.add(rs.getString("SerialNumber"));
            }
            serialCombo.setItems(masterSerials);

            if (masterSerials.isEmpty()) {
                showAlert("لا يوجد سيريالات لهذا الجهاز في الفترة المحددة.");
            }

        } catch (Exception e) {
            e.printStackTrace();
            showAlert("حدث خطأ أثناء تحميل السيريالات.");
        }
    }

    public void setSelectedSerial(String serial) {
        Platform.runLater(() -> {
            serialCombo.setValue(serial);
            onShowSerial(); // عرض بيانات السيريال تلقائياً
        });
    }


    private void showAllSerialsInNewWindow() {
        Device selectedDevice = deviceCombo.getValue();
        if (selectedDevice == null) {
            showAlert("اختر جهاز أولاً");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/AllSerialsView.fxml"));
            Parent root = loader.load();

            AllSerialsController controller = loader.getController();
            controller.setParentController(this);
            controller.setDevice(selectedDevice);
            controller.loadSerials();

            Stage stage = new Stage();
            stage.setTitle("جميع سيريالات جهاز: " + selectedDevice.getName());
            stage.setScene(new Scene(root, 600, 500));
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(deviceCombo.getScene().getWindow());
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
            showAlert("حدث خطأ أثناء فتح نافذة السيريالات: " + e.getMessage());
        }
    }


    @FXML
    private void onShowSerial() {
        String serial = serialCombo.getValue();
        if (serial == null || serial.isBlank()) {
            showAlert("اختر سيريال أولاً");
            return;
        }

        Device device = deviceCombo.getValue();
        if (device == null) {
            showAlert("اختر جهاز أولاً");
            return;
        }

        int deviceId = device.getId();

        usageList.clear();

        String sql = """
            SELECT 
                I.ItemID, 
                I.ItemName, 
                ISNULL(DC.ExpectedQty, 0) AS ExpectedQty, 
                ISNULL(SUM(SCU.Quantity), 0) AS UsedTotal, 
                MAX(SCU.UsedAt) AS LastUsed
            FROM SerialComponentUsage SCU
            JOIN Items I ON SCU.ItemID = I.ItemID
            LEFT JOIN (
                SELECT ItemID, SUM(Quantity) AS ExpectedQty 
                FROM DeviceComponents 
                WHERE DeviceID = ? 
                GROUP BY ItemID
            ) DC ON DC.ItemID = I.ItemID
            JOIN DeviceSerials DS ON SCU.SerialID = DS.SerialID
            WHERE DS.SerialNumber = ?
            GROUP BY I.ItemID, I.ItemName, DC.ExpectedQty
            ORDER BY I.ItemName
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, deviceId);
            ps.setString(2, serial);

            ResultSet rs = ps.executeQuery();
            List<UsageRow> rows = new ArrayList<>();

            while (rs.next()) {
                String itemName = rs.getString("ItemName");
                double expected = rs.getDouble("ExpectedQty");
                double used = rs.getDouble("UsedTotal");
                Timestamp lastUsed = rs.getTimestamp("LastUsed");

                String usedAt = lastUsed == null ? "-" : lastUsed.toString();
                // ✅ هنا بنعرض اسم المستخدم الحالي بدل ID
                String usedBy = CurrentUser.getName();

                String status = computeStatus(expected, used);

                rows.add(new UsageRow(
                        itemName,
                        String.format("%.2f", expected),
                        String.format("%.2f", used),
                        status,
                        usedAt,
                        usedBy
                ));
            }
//            debugExceededItems();

            if (rows.isEmpty()) {
                showAlert("لا توجد بيانات استخدام لهذا السيريال.");
            }

            usageList.addAll(rows);
            filteredUsageList.setPredicate(null);

        } catch (Exception e) {
            e.printStackTrace();
            showAlert("حدث خطأ أثناء تحميل بيانات الاستخدام.");
        }
    }

    @FXML
    private void onShowExceededSerials() {
        Device selectedDevice = deviceCombo.getValue();
        if (selectedDevice == null) {
            showAlert("اختر جهاز أولاً");
            return;
        }

        int deviceId = selectedDevice.getId();

        String sql = """
        SELECT DS.SerialNumber
        FROM DeviceSerials DS
        JOIN (
            SELECT 
                SCU.SerialID,
                SUM(SCU.Quantity) AS UsedTotal,
                SUM(ISNULL(DC.Quantity, 0)) AS ExpectedTotal
            FROM SerialComponentUsage SCU
            LEFT JOIN DeviceComponents DC 
                ON DC.ItemID = SCU.ItemID 
                AND DC.DeviceID = ?
            GROUP BY SCU.SerialID
        ) T ON T.SerialID = DS.SerialID
        WHERE DS.DeviceID = ?
        AND T.UsedTotal > T.ExpectedTotal
        ORDER BY DS.SerialNumber
    """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, deviceId);
            ps.setInt(2, deviceId);

            ResultSet rs = ps.executeQuery();
            ObservableList<String> exceededSerials = FXCollections.observableArrayList();

            while (rs.next()) {
                exceededSerials.add(rs.getString("SerialNumber"));
            }

            if (exceededSerials.isEmpty()) {
                showAlert("لا توجد سيريالات متجاوزة لهذا الجهاز.");
                return;
            }

            openSerialsWindow("السيريالات المتجاوزة", exceededSerials, selectedDevice.getName());

        } catch (Exception e) {
            e.printStackTrace();
            showAlert("حدث خطأ أثناء البحث عن السيريالات المتجاوزة: " + e.getMessage());
        }
    }

    private void openSerialsWindow(String title, ObservableList<String> serials, String deviceName) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/AllSerialsView.fxml"));
            Parent root = loader.load();

            AllSerialsController controller = loader.getController();
            controller.setParentController(this);
            controller.setDevice(new Device(0, deviceName)); // نستخدم ID=0 مؤقتاً
            controller.setSerials(serials);

            Stage stage = new Stage();
            stage.setTitle(title + " - " + deviceName);
            stage.setScene(new Scene(root, 600, 500));
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(deviceCombo.getScene().getWindow());
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
            showAlert("حدث خطأ أثناء فتح نافذة السيريالات: " + e.getMessage());
        }
    }

    // دالة مساعدة للتحقق من وجود تجاوزات
    private void debugExceededItems() {
        Device selectedDevice = deviceCombo.getValue();
        if (selectedDevice == null) return;

        int deviceId = selectedDevice.getId();

        String debugSql = """
        SELECT DS.SerialNumber, I.ItemName, SCU.Quantity, DC.Quantity as Expected
        FROM DeviceSerials DS
        JOIN SerialComponentUsage SCU ON SCU.SerialID = DS.SerialID
        JOIN Items I ON SCU.ItemID = I.ItemID
        LEFT JOIN DeviceComponents DC ON DC.ItemID = I.ItemID AND DC.DeviceID = ?
        WHERE DS.DeviceID = ?
        AND SCU.Quantity > ISNULL(DC.Quantity, 0)
    """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(debugSql)) {

            ps.setInt(1, deviceId);
            ps.setInt(2, deviceId);
            ResultSet rs = ps.executeQuery();

            System.out.println("=== تصحيح: العناصر المتجاوزة ===");
            while (rs.next()) {
                System.out.println("سيريال: " + rs.getString("SerialNumber") +
                        " | عنصر: " + rs.getString("ItemName") +
                        " | مستخدم: " + rs.getDouble("Quantity") +
                        " | متوقع: " + rs.getDouble("Expected"));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void onShowAllSerials() {
        Device selectedDevice = deviceCombo.getValue();
        if (selectedDevice == null) {
            showAlert("اختر جهاز أولاً");
            return;
        }

        // استخدام الدالة الموجودة مسبقاً
        openSerialsWindow("جميع السيريالات", masterSerials, selectedDevice.getName());
    }

    @FXML
    private void onShowExceeded() {
        if (usageList.isEmpty()) {
            onShowSerial();
        }

        filteredUsageList.setPredicate(row ->
                row != null && row.getStatus().contains("تجاوز")
        );

        if (filteredUsageList.isEmpty()) {
            showAlert("لا توجد عناصر متجاوزة في هذا السيريال.");
            filteredUsageList.setPredicate(null);
        }
    }

    /// /////////////////////////////
    // ✅ تصحيح دالة التسعير - نجمع الكميات أولاً
    @FXML
    private void onPriceSerial() {
        String serial = serialCombo.getValue();
        Device device = deviceCombo.getValue();

        if (serial == null || serial.isBlank() || device == null) {
            showAlert("اختر الجهاز والسيريال أولاً.");
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = """
        SELECT I.ItemName, ISNULL(SCU.Quantity, 0) AS UsedQty, ISNULL(P.UnitPrice,0) AS Price
        FROM SerialComponentUsage SCU
        JOIN Items I ON SCU.ItemID = I.ItemID
        LEFT JOIN ItemPrices P ON I.ItemID = P.ItemID
        JOIN DeviceSerials DS ON SCU.SerialID = DS.SerialID
        WHERE DS.SerialNumber = ?
        """;

            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, serial);
            ResultSet rs = ps.executeQuery();

            // ✅ أولاً: نجمع الكميات لكل عنصر
            Map<String, Double> itemQuantities = new HashMap<>();
            Map<String, Double> itemPrices = new HashMap<>();

            while (rs.next()) {
                String itemName = rs.getString("ItemName");
                double qty = rs.getDouble("UsedQty");
                double price = rs.getDouble("Price");

                // نجمع الكميات لنفس العنصر
                itemQuantities.put(itemName, itemQuantities.getOrDefault(itemName, 0.0) + qty);
                itemPrices.put(itemName, price); // نأخذ آخر سعر
            }

            // ✅ ثانياً: نخلق الـ PriceDetail بعد الجمع
            List<PriceDetail> details = new ArrayList<>();
            double total = 0;

            for (Map.Entry<String, Double> entry : itemQuantities.entrySet()) {
                String itemName = entry.getKey();
                double totalQty = entry.getValue();
                double price = itemPrices.getOrDefault(itemName, 0.0);
                double subtotal = price * totalQty;
                total += subtotal;

                details.add(new PriceDetail(itemName, totalQty, price, subtotal));
            }

            if (details.isEmpty()) {
                showAlert("لا توجد بيانات تسعير لهذا السيريال.");
                return;
            }

            showPriceDialog(device.getName(), serial, details, total);

        } catch (Exception e) {
            e.printStackTrace();
            showAlert("حدث خطأ أثناء التسعير:\n" + e.getMessage());
        }
    }

    // ✅ تمثيل بيانات التسعير
    public static class PriceDetail {
        private final String itemName;
        private final double qty;
        private final double price;
        private final double subtotal;

        public PriceDetail(String itemName, double qty, double price, double subtotal) {
            this.itemName = itemName;
            this.qty = qty;
            this.price = price;
            this.subtotal = subtotal;
        }

        public String getItemName() { return itemName; }
        public double getQty() { return qty; }
        public double getPrice() { return price; }
        public double getSubtotal() { return subtotal; }
    }

    // ✅ عرض Dialog مع التفاصيل وخيارات التصدير (معدل)
    private void showPriceDialog(String deviceName, String serial, List<PriceDetail> details, double total) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("تسعير السيريال");

        VBox container = new VBox(10);
        container.setStyle("-fx-padding: 20; -fx-background-color: #ffffff;");

        // شعار الشركة
        ImageView logo = new ImageView(new javafx.scene.image.Image(
                getClass().getResourceAsStream("/images/colord_logo.png")));
        logo.setFitHeight(60);
        logo.setPreserveRatio(true);

        Label company = new Label("CHEM TECH");
        company.setStyle("-fx-font-size: 20px; -fx-font-weight:bold;");

        Label deviceLabel = new Label("الجهاز: " + deviceName + " | السيريال: " + serial);
        deviceLabel.setStyle("-fx-font-size: 16px; -fx-font-weight:bold;");

        // ✅ نحتاج لجلب الكميات المتوقعة للجهاز
        final Map<String, Double> expectedQuantities = getExpectedQuantitiesForDevice(deviceCombo.getValue().getId());

        // جدول داخلي
        TableView<PriceDetail> table = new TableView<>();
        table.setPrefHeight(300);

        // ✅ تلوين الصفوف المتجاوزة بناءً على الكمية
        table.setRowFactory(tv -> new TableRow<PriceDetail>() {
            @Override
            protected void updateItem(PriceDetail item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("");
                } else {
                    double expected = expectedQuantities.getOrDefault(item.getItemName(), 0.0);
                    double used = item.getQty();
                    // ✅ التجاوز: إذا الكمية المستخدمة أكبر من المتوقعة
                    if (used > expected) {
                        setStyle("-fx-background-color: rgba(255,100,100,0.5); -fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        TableColumn<PriceDetail, String> colItem = new TableColumn<>("المكون");
        colItem.setCellValueFactory(new PropertyValueFactory<>("itemName"));

        // ✅ إضافة عمود للكمية المتوقعة (الطريقة الصحيحة)
        TableColumn<PriceDetail, String> colExpected = new TableColumn<>("المتوقع");
        colExpected.setCellValueFactory(cellData -> {
            String itemName = cellData.getValue().getItemName();
            double expected = expectedQuantities.getOrDefault(itemName, 0.0);
            return new javafx.beans.property.SimpleStringProperty(String.format("%.2f", expected));
        });

        TableColumn<PriceDetail, String> colQty = new TableColumn<>("الكمية المستخدمة");
        colQty.setCellValueFactory(cellData -> {
            double qty = cellData.getValue().getQty();
            return new javafx.beans.property.SimpleStringProperty(String.format("%.2f", qty));
        });
        colQty.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String qtyStr, boolean empty) {
                super.updateItem(qtyStr, empty);
                if (empty || qtyStr == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(qtyStr);
                    PriceDetail item = getTableView().getItems().get(getIndex());
                    if (item != null) {
                        double expected = expectedQuantities.getOrDefault(item.getItemName(), 0.0);
                        double used = item.getQty();

                        // ✅ تلوين إذا كانت الكمية أكبر من المتوقعة
                        if (used > expected) {
                            setStyle("-fx-background-color: rgba(255,100,100,0.5); -fx-font-weight: bold;");
                        } else {
                            setStyle("");
                        }
                    }
                }
            }
        });

        TableColumn<PriceDetail, String> colPrice = new TableColumn<>("سعر الوحدة");
        colPrice.setCellValueFactory(cellData -> {
            double price = cellData.getValue().getPrice();
            return new javafx.beans.property.SimpleStringProperty(String.format("%.2f", price));
        });

        TableColumn<PriceDetail, String> colSubtotal = new TableColumn<>("الإجمالي");
        colSubtotal.setCellValueFactory(cellData -> {
            double subtotal = cellData.getValue().getSubtotal();
            return new javafx.beans.property.SimpleStringProperty(String.format("%.2f", subtotal));
        });
        colSubtotal.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String subtotalStr, boolean empty) {
                super.updateItem(subtotalStr, empty);
                if (empty || subtotalStr == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(subtotalStr);
                    PriceDetail item = getTableView().getItems().get(getIndex());
                    if (item != null) {
                        double expected = expectedQuantities.getOrDefault(item.getItemName(), 0.0);
                        double used = item.getQty();

                        // ✅ تلوين إذا كانت الكمية أكبر من المتوقعة
                        if (used > expected) {
                            setStyle("-fx-background-color: rgba(255,100,100,0.5); -fx-font-weight: bold;");
                        } else {
                            setStyle("");
                        }
                    }
                }
            }
        });

        table.getColumns().addAll(colItem, colExpected, colQty, colPrice, colSubtotal);
        table.getItems().addAll(details);

        Separator sep = new Separator();

        // ✅ حساب إجمالي المتجاوز (الفرق بين المستخدم والمتوقع مضروب في السعر)
        double exceededTotal = 0.0;
        for (PriceDetail detail : details) {
            double expected = expectedQuantities.getOrDefault(detail.getItemName(), 0.0);
            double used = detail.getQty();
            if (used > expected) {
                double exceededQty = used - expected;
                double exceededCost = exceededQty * detail.getPrice();
                exceededTotal += exceededCost;
            }
        }

        Label totalLabel = new Label(String.format("المجموع النهائي: %.2f ج.م", total));
        totalLabel.setStyle("-fx-font-size: 16px; -fx-font-weight:bold; -fx-text-fill:#22c55e;");

        // ✅ إضافة سطر إجمالي المتجاوز
        Label exceededLabel = new Label(String.format("إجمالي التجاوز: %.2f ج.م", exceededTotal));
        exceededLabel.setStyle("-fx-font-size: 14px; -fx-font-weight:bold; -fx-text-fill:#ef4444;");

        HBox buttons = new HBox(10);
        Button exportExcelBtn = new Button("تصدير Excel");
        exportExcelBtn.setStyle("-fx-background-color:#3b82f6; -fx-text-fill:white; -fx-font-weight:bold;");

        // ✅ جعل المتغير final للاستخدام في lambda
        final double finalExceededTotal = exceededTotal;
        exportExcelBtn.setOnAction(e -> exportPriceDetailsToExcel(deviceName, serial, details, total, finalExceededTotal, expectedQuantities));

        Button exportImageBtn = new Button("تصدير صورة");
        exportImageBtn.setStyle("-fx-background-color:#f59e0b; -fx-text-fill:white; -fx-font-weight:bold;");
        exportImageBtn.setOnAction(e -> {
            VBox snapshotContainer = new VBox(10);
            snapshotContainer.getChildren().addAll(logo, company, deviceLabel, table, sep, totalLabel, exceededLabel, buttons);
            exportPriceDialogAsImage(snapshotContainer, deviceName, serial, details, total, finalExceededTotal);
        });

        buttons.getChildren().addAll(exportExcelBtn, exportImageBtn);

        container.getChildren().addAll(logo, company, deviceLabel, table, sep, totalLabel, exceededLabel, buttons);

        dialog.getDialogPane().setContent(container);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    // ✅ تصدير Excel (معدل)
    private void exportPriceDetailsToExcel(String deviceName, String serial, List<PriceDetail> details, double total, double exceededTotal, Map<String, Double> expectedQuantities) {
        FileChooser fc = new FileChooser();
        fc.setTitle("حفظ تقرير التسعير");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel", "*.xlsx"));
        fc.setInitialFileName("تسعير_"+serial+".xlsx");
        File file = fc.showSaveDialog(priceSerialBtn.getScene().getWindow());
        if(file==null) return;

        try(Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("تقرير التسعير");

            // تنسيق للأرقام
            CellStyle moneyStyle = wb.createCellStyle();
            DataFormat format = wb.createDataFormat();
            moneyStyle.setDataFormat(format.getFormat("#,##0.00"));

            // تنسيق للخلايا الحمراء (المتجاوزة)
            CellStyle exceededStyle = wb.createCellStyle();
            exceededStyle.setDataFormat(format.getFormat("#,##0.00"));
            exceededStyle.setFillForegroundColor(IndexedColors.RED.getIndex());
            exceededStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // شعار + الجهاز + السيريال
            Row row0 = sheet.createRow(0);
            row0.createCell(0).setCellValue("CHEM TECH");

            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("الجهاز: "+deviceName+" | السيريال: "+serial);

            Row header = sheet.createRow(3);
            String[] headers = {"المكون", "المتوقع", "الكمية المستخدمة", "سعر الوحدة", "الإجمالي"};
            for(int i=0;i<headers.length;i++) header.createCell(i).setCellValue(headers[i]);

            int r = 4;
            for(PriceDetail pd: details){
                Row row = sheet.createRow(r++);
                double expected = expectedQuantities.getOrDefault(pd.itemName, 0.0);
                boolean isExceeded = pd.qty > expected;

                row.createCell(0).setCellValue(pd.itemName);

                // المتوقع
                Cell expectedCell = row.createCell(1);
                expectedCell.setCellValue(expected);
                expectedCell.setCellStyle(moneyStyle);

                // الكمية المستخدمة
                Cell qtyCell = row.createCell(2);
                qtyCell.setCellValue(pd.qty);

                // سعر الوحدة
                Cell priceCell = row.createCell(3);
                priceCell.setCellValue(pd.price);

                // الإجمالي
                Cell subtotalCell = row.createCell(4);
                subtotalCell.setCellValue(pd.subtotal);

                // ✅ تلوين الخلايا المتجاوزة
                if (isExceeded) {
                    qtyCell.setCellStyle(exceededStyle);
                    subtotalCell.setCellStyle(exceededStyle);
                } else {
                    qtyCell.setCellStyle(moneyStyle);
                    priceCell.setCellStyle(moneyStyle);
                    subtotalCell.setCellStyle(moneyStyle);
                }
            }

            // ✅ إجمالي المتجاوز
            Row exceededRow = sheet.createRow(r++);
            exceededRow.createCell(3).setCellValue("إجمالي التجاوز");
            Cell exceededCell = exceededRow.createCell(4);
            exceededCell.setCellValue(exceededTotal);
            exceededCell.setCellStyle(exceededStyle);

            // المجموع النهائي
            Row totalRow = sheet.createRow(r);
            totalRow.createCell(3).setCellValue("المجموع النهائي");
            totalRow.createCell(4).setCellValue(total);
            totalRow.getCell(4).setCellStyle(moneyStyle);

            try(FileOutputStream fos = new FileOutputStream(file)) { wb.write(fos); }
            showAlert("تم حفظ التقرير بنجاح: "+file.getAbsolutePath());

        }catch(Exception e){
            e.printStackTrace();
            showAlert("حدث خطأ أثناء التصدير: "+e.getMessage());
        }
    }

    // ✅ تصدير الصورة (معدل)
    private void exportPriceDialogAsImage(VBox container, String deviceName, String serial, List<PriceDetail> details, double total, double exceededTotal){
        FileChooser fc = new FileChooser();
        fc.setTitle("حفظ تقرير التسعير كصورة");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG", "*.png"));
        fc.setInitialFileName("تسعير_" + serial + ".png");
        File file = fc.showSaveDialog(priceSerialBtn.getScene().getWindow());
        if(file == null) return;

        try {
            WritableImage image = container.snapshot(new SnapshotParameters(), null);
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
            showAlert("تم حفظ الصورة بنجاح: " + file.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("حدث خطأ أثناء حفظ الصورة: " + e.getMessage());
        }
    }
    // ✅ دالة لجلب الكميات المتوقعة للجهاز
    private Map<String, Double> getExpectedQuantitiesForDevice(int deviceId) {
        Map<String, Double> expectedMap = new HashMap<>();

        String sql = """
        SELECT I.ItemName, DC.Quantity 
        FROM DeviceComponents DC
        JOIN Items I ON DC.ItemID = I.ItemID
        WHERE DC.DeviceID = ?
    """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, deviceId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                String itemName = rs.getString("ItemName");
                double quantity = rs.getDouble("Quantity");
                expectedMap.put(itemName, quantity);
            }

        } catch (Exception e) {
            e.printStackTrace();
            showAlert("حدث خطأ أثناء تحميل الكميات المتوقعة للجهاز.");
        }

        return expectedMap;
    }


    /// ////////////////////////////

    @FXML
    private void onShowAll() {
        filteredUsageList.setPredicate(null);
    }

    @FXML
    private void onExportToExcel() {
        if (filteredUsageList.isEmpty()) {
            showAlert("لا توجد بيانات للتصدير.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("حفظ ملف Excel");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
        );

        String fileName = "تقرير_السيريالات_" +
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".xlsx";
        fileChooser.setInitialFileName(fileName);

        File file = fileChooser.showSaveDialog(exportExcelBtn.getScene().getWindow());

        if (file != null) {
            try {
                exportToExcel(file, filteredUsageList);
                showAlert("تم التصدير بنجاح إلى: " + file.getAbsolutePath());
            } catch (Exception e) {
                e.printStackTrace();
                showAlert("حدث خطأ أثناء التصدير: " + e.getMessage());
            }
        }
    }

    private void exportToExcel(File file, ObservableList<UsageRow> data) throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("تقرير السيريالات");

        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);

        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        CellStyle cellStyle = workbook.createCellStyle();
        cellStyle.setBorderBottom(BorderStyle.THIN);
        cellStyle.setBorderTop(BorderStyle.THIN);
        cellStyle.setBorderLeft(BorderStyle.THIN);
        cellStyle.setBorderRight(BorderStyle.THIN);

        Row headerRow = sheet.createRow(0);
        String[] headers = {"المكون", "المفروض", "المستخدم", "الحالة", "آخر استخدام", "المستخدم"};

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
            sheet.autoSizeColumn(i);
        }

        int rowNum = 1;
        for (UsageRow row : data) {
            Row excelRow = sheet.createRow(rowNum++);
            Cell cell0 = excelRow.createCell(0); cell0.setCellValue(row.getItemName());
            Cell cell1 = excelRow.createCell(1); cell1.setCellValue(row.getExpected());
            Cell cell2 = excelRow.createCell(2); cell2.setCellValue(row.getUsed());
            Cell cell3 = excelRow.createCell(3); cell3.setCellValue(row.getStatus());
            Cell cell4 = excelRow.createCell(4); cell4.setCellValue(row.getUsedAt());
            Cell cell5 = excelRow.createCell(5); cell5.setCellValue(row.getUsedBy());
        }

        for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

        try (FileOutputStream fileOut = new FileOutputStream(file)) {
            workbook.write(fileOut);
        }

        workbook.close();
    }

    private String computeStatus(double expected, double used) {
        if (expected <= 0 && used > 0) return "تجاوز (لا يوجد متوقع)";
        if (used > expected) return String.format("تجاوز (%.2f > %.2f)", used, expected);
        if (used == expected) return "مطابق";
        return String.format("طبيعي (%.2f / %.2f)", used, expected);
    }

    private void showAlert(String txt) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION, txt, ButtonType.OK);
            a.showAndWait();
        });
    }

    // ✅ كلاس لتمثيل الجهاز
    public static class Device {
        private final int id;
        private final String name;

        public Device(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() { return id; }
        public String getName() {
            return name;
        }


        @Override
        public String toString() {
            return name;
        }
    }

    // ✅ كائن عرض الصفوف
    public static class UsageRow {
        private final String itemName, expected, used, status, usedAt, usedBy;

        public UsageRow(String itemName, String expected, String used, String status, String usedAt, String usedBy) {
            this.itemName = itemName;
            this.expected = expected;
            this.used = used;
            this.status = status;
            this.usedAt = usedAt;
            this.usedBy = usedBy;
        }

        public String getItemName() { return itemName; }
        public String getExpected() { return expected; }
        public String getUsed() { return used; }
        public String getStatus() { return status; }
        public String getUsedAt() { return usedAt; }
        public String getUsedBy() { return usedBy; }
    }
}
