package app.controllers;

import app.current_user.CurrentUser;
import app.db.DatabaseConnection;
import app.services.LogService;
import app.utils.RawThermalPrinter;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.sql.*;
import java.util.List;
import java.util.Optional;

public class StockViewController {

    @FXML private TableView<ItemData> stockTable;
    @FXML private TableColumn<ItemData, String> nameColumn;
    @FXML private TableColumn<ItemData, String> unitColumn;
    @FXML private TableColumn<ItemData, Double> quantityColumn;
    @FXML private TableColumn<ItemData, Double> minQuantityColumn;
    @FXML private TableColumn<ItemData, String> statusColumn;
    @FXML private TableColumn<ItemData, String> codeColumn;
    @FXML private TextField searchField;
    @FXML private Button refreshButton;
    @FXML private ComboBox<String> statusFilterCombo;

    private final ObservableList<ItemData> allItems = FXCollections.observableArrayList();
    private StockOutput currentStockOutput;

    @FXML
    public void initialize() {
        codeColumn.setCellValueFactory(new PropertyValueFactory<>("itemCode"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        unitColumn.setCellValueFactory(new PropertyValueFactory<>("unit"));
        quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        minQuantityColumn.setCellValueFactory(new PropertyValueFactory<>("minQuantity"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        loadStockData();

        searchField.textProperty().addListener((obs, oldValue, newValue) -> {
            filterItems(newValue);
        });

        statusFilterCombo.setItems(FXCollections.observableArrayList("الكل", "✅ OK", "⚠️ Low Stock"));
        statusFilterCombo.setValue("الكل");
        statusFilterCombo.setOnAction(e -> filterByStatus());
    }

    private void filterItems(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            filterByStatus();
            return;
        }

        ObservableList<ItemData> filtered = FXCollections.observableArrayList();
        String searchTerm = keyword.toLowerCase().trim();

        for (ItemData item : allItems) {
            boolean matchesName = item.getItemName().toLowerCase().contains(searchTerm);
            boolean matchesCode = item.getItemCode() != null && item.getItemCode().toLowerCase().contains(searchTerm);
            boolean matchesQuantity = String.valueOf(item.getQuantity()).contains(searchTerm);
            boolean matchesId = String.valueOf(item.getItemId()).contains(searchTerm);
            boolean matchesStatus = item.getStatus().toLowerCase().contains(searchTerm);

            // Search by name OR code (main change here)
            if (matchesName || matchesCode || matchesQuantity || matchesId || matchesStatus) {
                filtered.add(item);
            }
        }
        stockTable.setItems(filtered);
    }

    private void loadStockData() {
        allItems.clear();

        String query = """
        SELECT 
            i.ItemID,
            i.ItemCode AS ItemCode,
            i.ItemName AS ItemName,
            u.UnitName AS UnitName,
            sb.Quantity AS Quantity,
            i.MinQuantity AS MinQuantity
        FROM StockBalances sb
        INNER JOIN Items i ON sb.ItemID = i.ItemID
        INNER JOIN Units u ON i.UnitID = u.UnitID
        ORDER BY i.ItemName
    """;

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                int itemId = rs.getInt("ItemID");
                String code = rs.getString("ItemCode");
                String name = rs.getString("ItemName");
                String unit = rs.getString("UnitName");
                double qty = rs.getDouble("Quantity");
                double minQty = rs.getDouble("MinQuantity");

                String status = (qty < minQty) ? "⚠️ Low Stock" : "✅ OK";

                allItems.add(new ItemData(itemId, code, name, unit, qty, minQty, status));
            }

            stockTable.setItems(allItems);

        } catch (SQLException e) {
            e.printStackTrace();
            showError("خطأ في تحميل بيانات المخزون: " + e.getMessage());
        }
    }
    private void filterByStatus() {
        String selected = statusFilterCombo.getValue();
        if (selected == null || selected.equals("الكل")) {
            stockTable.setItems(allItems);
            return;
        }

        ObservableList<ItemData> filtered = FXCollections.observableArrayList();
        for (ItemData item : allItems) {
            if (item.getStatus().equals(selected)) {
                filtered.add(item);
            }
        }
        stockTable.setItems(filtered);
    }

//    private void filterItems(String keyword) {
//        if (keyword == null || keyword.trim().isEmpty()) {
//            filterByStatus();
//            return;
//        }
//
//        ObservableList<ItemData> filtered = FXCollections.observableArrayList();
//        String searchTerm = keyword.toLowerCase().trim();
//
//        for (ItemData item : allItems) {
//            boolean matchesName = item.getItemName().toLowerCase().contains(searchTerm);
//            boolean matchesQuantity = String.valueOf(item.getQuantity()).contains(searchTerm);
//            boolean matchesId = String.valueOf(item.getItemId()).contains(searchTerm);
//            boolean matchesStatus = item.getStatus().toLowerCase().contains(searchTerm);
//
//            if (matchesName || matchesQuantity || matchesId || matchesStatus) {
//                filtered.add(item);
//            }
//        }
//        stockTable.setItems(filtered);
//    }

    @FXML
    private void onAddStock() {
        ItemData selectedItem = stockTable.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            showError("يرجى اختيار صنف أولاً!");
            return;
        }

        Dialog<StockInput> dialog = createStockDialog("إضافة كمية", "أدخل الكمية المضافة:");

        Optional<StockInput> result = dialog.showAndWait();
        if (result.isPresent()) {
            StockInput input = result.get();
            try {
                double qty = input.getQuantity();
                if (qty <= 0) {
                    showError("الكمية يجب أن تكون أكبر من الصفر!");
                    return;
                }

                String description = String.format("تم إضافة %.2f وحدة من الصنف %s - الملاحظات: %s",
                        qty, selectedItem.getItemName(),
                        input.getNotes().isEmpty() ? "لا توجد ملاحظات" : input.getNotes());

                LogService.addLog("STOCK_IN", description);
                updateStock(selectedItem, qty, "IN", input.getNotes());
                loadStockData();

                showInfo("تم إضافة الكمية بنجاح!");

            } catch (NumberFormatException e) {
                showError("قيمة الكمية غير صالحة!");
            }
        }
    }

    @FXML
    private void onRemoveStock() {
        ItemData selectedItem = stockTable.getSelectionModel().getSelectedItem();

        if (selectedItem == null) {
            showError("يرجى اختيار صنف أولاً!");
            return;
        }

        Dialog<StockOutput> dialog = createStockOutDialog("صرف كمية", "أدخل الكمية المراد صرفها:");
        Optional<StockOutput> result = dialog.showAndWait();

        if (result.isPresent()) {
            StockOutput input = result.get();

            try {
                double qty = input.getQuantity();

                if (qty <= 0) {
                    showError("الكمية يجب أن تكون أكبر من الصفر!");
                    return;
                }

                if (qty > selectedItem.getQuantity()) {
                    showError("الكمية المطلوبة تتجاوز المخزون المتاح!");
                    return;
                }

                if (input.getUsageType().equals("جهاز جديد")) {
                    // ✅ إذا تم التعامل مع الطلب المتجاوز في checkDeviceComponents، لا نكمل
                    boolean shouldContinue = checkDeviceComponents(selectedItem.getItemId(), input.getDeviceName(), qty, input.getSerialNumber(), input.getNewSerial());
                    if (!shouldContinue) {
                        return; // توقف هنا إذا تم التعامل مع الطلب المتجاوز
                    }
                }

                if (input.getUsageType().equals("صيانة") && (input.getReceiver() == null || input.getReceiver().trim().isEmpty())) {
                    showError("في حالة الصيانة يجب إدخال اسم المستلم!");
                    return;
                }

                currentStockOutput = input;

                String description = String.format(
                        "تم صرف %.2f وحدة من الصنف %s - المستلم: %s - نوع الاستخدام: %s - الملاحظات: %s",
                        qty, selectedItem.getItemName(),
                        input.getReceiver() != null ? input.getReceiver() : "غير محدد",
                        input.getUsageType(),
                        input.getNotes().isEmpty() ? "لا توجد ملاحظات" : input.getNotes()
                );

                LogService.addLog("STOCK_OUT", description);

                // ✅ هذا السطر يتم تنفيذه فقط إذا لم يكن هناك طلب متجاوز
                updateStock(selectedItem, -qty, "OUT", input.getReceiver() + " - " + input.getNotes());

                loadStockData();

                showInfo("تم صرف الكمية بنجاح!");
            } catch (NumberFormatException e) {
                showError("قيمة الكمية غير صالحة!");
            } finally {
                currentStockOutput = null;
            }
        }
    }

    private boolean checkDeviceComponents(int itemId, String deviceName, double requestedQty, String serialNumber, String newSerial) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String finalSerial = (serialNumber != null && !serialNumber.trim().isEmpty()) ? serialNumber : newSerial;
            if (finalSerial == null || finalSerial.trim().isEmpty()) {
                showError("يجب اختيار سيريال موجود أو إدخال سيريال جديد!");
                return false;
            }

            int serialId = getOrCreateSerialId(deviceName, finalSerial, conn);
            if (serialId == 0) return false;

            double usedQuantity = getUsedQuantityForSerial(serialId, itemId, conn);
            double allowedQuantity = getAllowedQuantityForDevice(deviceName, itemId, conn);

            double remainingQuantity = allowedQuantity - usedQuantity;

            if (requestedQty > remainingQuantity) {
                // ✅ إذا كان هناك تجاوز، تعامل معه وأعد false لوقف العملية
                return showExceedWarning(itemId, serialId, requestedQty, remainingQuantity, finalSerial);
            }

            return true; // ✅ لا يوجد تجاوز، أكمل العملية الطبيعية

        } catch (SQLException e) {
            e.printStackTrace();
            showError("خطأ في التحقق من مكونات الجهاز: " + e.getMessage());
            return false;
        }
    }
    private double getUsedQuantityForSerial(int serialId, int itemId, Connection conn) throws SQLException {
        String query = "SELECT SUM(Quantity) FROM SerialComponentUsage WHERE SerialID = ? AND ItemID = ?";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, serialId);
            ps.setInt(2, itemId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getDouble(1) : 0;
        }
    }

    private double getAllowedQuantityForDevice(String deviceName, int itemId, Connection conn) throws SQLException {
        String query = "SELECT Quantity FROM DeviceComponents WHERE DeviceID = (SELECT DeviceID FROM Devices WHERE DeviceName = ?) AND ItemID = ?";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, deviceName);
            ps.setInt(2, itemId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getDouble(1) : 0;
        }
    }

    private int getOrCreateSerialId(String deviceName, String serialNumber, Connection conn) throws SQLException {
        String checkQuery = "SELECT SerialID FROM DeviceSerials WHERE SerialNumber = ?";
        try (PreparedStatement ps = conn.prepareStatement(checkQuery)) {
            ps.setString(1, serialNumber);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("SerialID");
            }
        }

        String insertQuery = "INSERT INTO DeviceSerials (DeviceID, SerialNumber, AddedBy) OUTPUT INSERTED.SerialID VALUES ((SELECT DeviceID FROM Devices WHERE DeviceName = ?), ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertQuery)) {
            ps.setString(1, deviceName);
            ps.setString(2, serialNumber);
            ps.setInt(3, CurrentUser.getId());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    private boolean showExceedWarning(int itemId, int serialId, double requestedQty, double remainingQty, String serialNumber) {
        double exceededQty = requestedQty - remainingQty;

        Dialog<ExceedRequest> dialog = new Dialog<>();
        dialog.setTitle("تحذير تجاوز الكمية المسموحة");
        dialog.setHeaderText("الكمية المطلوبة تتجاوز الكمية المسموحة للجهاز!\n\n" +
                "الكمية المتبقية المسموحة: " + remainingQty +
                "\nالكمية المطلوبة: " + requestedQty +
                "\nالكمية الزائدة: " + exceededQty +
                "\n\nسيتم صرف الكمية المسموحة (" + remainingQty + ") الآن، وإرسال طلب للكمية الزائدة للمدير.");

        ButtonType approveButton = new ButtonType("إرسال طلب للمدير", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("إلغاء", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(approveButton, cancelButton);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        TextField reasonField = new TextField();
        reasonField.setPromptText("سبب الطلب");
        TextField defectiveField = new TextField();
        defectiveField.setPromptText("رقم القطعة المعيبة (إن وجد)");

        grid.add(new Label("السبب:"), 0, 0);
        grid.add(reasonField, 1, 0);
        grid.add(new Label("رقم القطعة المعيبة:"), 0, 1);
        grid.add(defectiveField, 1, 1);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == approveButton) {
                if (reasonField.getText().trim().isEmpty()) {
                    showError("يجب إدخال سبب الطلب!");
                    return null;
                }
                return new ExceedRequest(reasonField.getText(), defectiveField.getText());
            }
            return null;
        });

        Optional<ExceedRequest> result = dialog.showAndWait();
        if (result.isPresent()) {
            ExceedRequest request = result.get();
            boolean success = createStockRequest(itemId, serialId, requestedQty, remainingQty, request.getReason(), request.getDefectiveNumber(), serialNumber);
            // ✅ إذا نجح إنشاء الطلب، أعد false لوقف العملية الرئيسية
            return !success;
        }
        // ✅ إذا ألغى المستخدم، أعد false لوقف العملية
        return false;
    }
    private boolean createStockRequest(int itemId, int serialId, double requestedQty, double remainingQty, String reason, String defectiveNumber, String serialNumber) {
        double exceededQty = requestedQty - remainingQty;

        try (Connection conn = DatabaseConnection.getConnection()) {
            // 1. First, out the remaining allowed quantity immediately
            outAllowedQuantity(itemId, serialId, remainingQty, conn);

            // 2. Then create request for exceeded quantity only
            String query = "INSERT INTO StockRequests (SerialID, ItemID, RequestedQuantity, Reason, DefectiveNumber, AssignedToEmployee, RequestedBy, RequestedByName) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setInt(1, serialId);
                ps.setInt(2, itemId);
                ps.setDouble(3, exceededQty); // Only the exceeded part
                ps.setString(4, reason);
                ps.setString(5, defectiveNumber);
                ps.setString(6, "مدير النظام");
                ps.setInt(7, CurrentUser.getId());
                ps.setString(8, CurrentUser.getName());
                ps.executeUpdate();
            }

            showInfo("تم صرف الكمية المسموحة (" + remainingQty + ") الآن، وتم إرسال طلب للكمية الزائدة (" + exceededQty + ") للمدير");
            return true;

        } catch (SQLException e) {
            e.printStackTrace();
            showError("خطأ في إنشاء طلب الصرف: " + e.getMessage());
            return false;
        }
    }

    private void outAllowedQuantity(int itemId, int serialId, double quantity, Connection conn) throws SQLException {
        // Get item data for the transaction
        ItemData item = getItemData(itemId, conn);

        if (item != null) {
            // Perform immediate stock out for allowed quantity
            String description = "صرف كمية مسموحة للجهاز - السيريال: " + serialId;
            updateStock(item, -quantity, "OUT", "System - " + description);

            // Record in SerialComponentUsage
            String usageQuery = "INSERT INTO SerialComponentUsage (SerialID, ItemID, Quantity, UsedBy) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(usageQuery)) {
                ps.setInt(1, serialId);
                ps.setInt(2, itemId);
                ps.setDouble(3, quantity);
                ps.setInt(4, CurrentUser.getId());
                ps.executeUpdate();
            }
        }
    }

    private ItemData getItemData(int itemId, Connection conn) throws SQLException {
        String query = "SELECT i.ItemID, i.ItemCode, i.ItemName, u.UnitName, sb.Quantity, i.MinQuantity " +
                "FROM Items i INNER JOIN Units u ON i.UnitID = u.UnitID " +
                "INNER JOIN StockBalances sb ON i.ItemID = sb.ItemID " +
                "WHERE i.ItemID = ?";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, itemId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String status = (rs.getDouble("Quantity") < rs.getDouble("MinQuantity")) ? "⚠️ Low Stock" : "✅ OK";
                return new ItemData(
                        rs.getInt("ItemID"),
                        rs.getString("ItemCode"),
                        rs.getString("ItemName"),
                        rs.getString("UnitName"),
                        rs.getDouble("Quantity"),
                        rs.getDouble("MinQuantity"),
                        status
                );
            }
        }
        return null;
    }
    private Dialog<StockInput> createStockDialog(String title, String content) {
        Dialog<StockInput> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(content);

        ButtonType addButton = new ButtonType("إضافة", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButton, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        TextField quantityField = new TextField();
        quantityField.setPromptText("الكمية");
        TextArea notesField = new TextArea();
        notesField.setPromptText("ملاحظات (اختياري)");
        notesField.setPrefRowCount(3);

        grid.add(new Label("الكمية:"), 0, 0);
        grid.add(quantityField, 1, 0);
        grid.add(new Label("ملاحظات:"), 0, 1);
        grid.add(notesField, 1, 1);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButton) {
                if (quantityField.getText().trim().isEmpty()) {
                    showError("يرجى إدخال الكمية!");
                    return null;
                }
                try {
                    double qty = Double.parseDouble(quantityField.getText());
                    return new StockInput(qty, notesField.getText());
                } catch (NumberFormatException e) {
                    showError("قيمة الكمية غير صالحة!");
                    return null;
                }
            }
            return null;
        });

        return dialog;
    }

    private Dialog<StockOutput> createStockOutDialog(String title, String content) {
        Dialog<StockOutput> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(content);

        ButtonType outButton = new ButtonType("صرف", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(outButton, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        TextField quantityField = new TextField();
        quantityField.setPromptText("الكمية");

        ComboBox<String> deviceCombo = new ComboBox<>();
        ComboBox<String> serialCombo = new ComboBox<>();
        TextField newSerialField = new TextField();
        ComboBox<String> usageType = new ComboBox<>();
        TextField receiverField = new TextField();
        receiverField.setPromptText("اسم المستلم");

        TextField maintenanceDeviceField = new TextField();
        maintenanceDeviceField.setPromptText("اسم الجهاز أو السيريال (للصيانة)");

        TextArea notesField = new TextArea();
        notesField.setPromptText("ملاحظات (اختياري)");
        notesField.setPrefRowCount(2);

        usageType.setItems(FXCollections.observableArrayList("جهاز جديد", "صيانة", "توالف"));
        usageType.setValue("جهاز جديد");

        try (Connection conn = DatabaseConnection.getConnection()) {
            ResultSet rs = conn.prepareStatement("SELECT DeviceName FROM Devices").executeQuery();
            while (rs.next()) deviceCombo.getItems().add(rs.getString(1));
        } catch (Exception e) { e.printStackTrace(); }

        deviceCombo.setOnAction(e -> {
            serialCombo.getItems().clear();
            if (deviceCombo.getValue() != null) {
                try (Connection conn = DatabaseConnection.getConnection()) {
                    PreparedStatement ps = conn.prepareStatement(
                            "SELECT SerialNumber FROM DeviceSerials WHERE DeviceID = (SELECT DeviceID FROM Devices WHERE DeviceName = ?)"
                    );
                    ps.setString(1, deviceCombo.getValue());
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) serialCombo.getItems().add(rs.getString(1));
                } catch (Exception ex) { ex.printStackTrace(); }
            }
        });

        usageType.setOnAction(e -> {
            String selectedUsage = usageType.getValue();
            boolean isNewDevice = "جهاز جديد".equals(selectedUsage);
            boolean isMaintenance = "صيانة".equals(selectedUsage);
            boolean isScrap = "توالف".equals(selectedUsage);

            deviceCombo.setDisable(!isNewDevice);
            serialCombo.setDisable(!isNewDevice);
            newSerialField.setDisable(!isNewDevice);

            receiverField.setDisable(!isMaintenance && !isNewDevice);

            maintenanceDeviceField.setDisable(!isMaintenance);

            if (!isNewDevice) {
                deviceCombo.setValue(null);
                serialCombo.setValue(null);
                newSerialField.clear();
            }
            if (!isMaintenance) {
                receiverField.clear();
                maintenanceDeviceField.clear();
            }
        });

        grid.add(new Label("الكمية:"), 0, 0);
        grid.add(quantityField, 1, 0);

        grid.add(new Label("نوع الاستخدام:"), 0, 1);
        grid.add(usageType, 1, 1);

        grid.add(new Label("الجهاز:"), 0, 2);
        grid.add(deviceCombo, 1, 2);

        grid.add(new Label("السيريال:"), 0, 3);
        grid.add(serialCombo, 1, 3);

        grid.add(new Label("أو أدخل سيريال جديد:"), 0, 4);
        grid.add(newSerialField, 1, 4);

        grid.add(new Label("المستلم:"), 0, 5);
        grid.add(receiverField, 1, 5);

        grid.add(new Label("الجهاز/السيريال (للصيانة):"), 0, 6);
        grid.add(maintenanceDeviceField, 1, 6);

        grid.add(new Label("ملاحظات:"), 0, 7);
        grid.add(notesField, 1, 7);

        dialog.getDialogPane().setContent(grid);

        usageType.fireEvent(new javafx.event.ActionEvent());

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == outButton) {
                try {
                    double qty = Double.parseDouble(quantityField.getText());
                    String usage = usageType.getValue();

                    if ("جهاز جديد".equals(usage) && deviceCombo.getValue() == null) {
                        showError("في حالة جهاز جديد يجب اختيار الجهاز!");
                        return null;
                    }

                    if ("صيانة".equals(usage) && (receiverField.getText() == null || receiverField.getText().trim().isEmpty())) {
                        showError("في حالة الصيانة يجب إدخال اسم المستلم!");
                        return null;
                    }

                    if ("صيانة".equals(usage) && (maintenanceDeviceField.getText() == null || maintenanceDeviceField.getText().trim().isEmpty())) {
                        showError("في حالة الصيانة يجب إدخال اسم الجهاز أو السيريال!");
                        return null;
                    }

                    return new StockOutput(
                            qty,
                            receiverField.getText(),
                            notesField.getText(),
                            deviceCombo.getValue(),
                            serialCombo.getValue(),
                            newSerialField.getText(),
                            usageType.getValue(),
                            maintenanceDeviceField.getText()
                    );
                } catch (Exception e) {
                    showError("رجاء إدخال البيانات صحيحة!");
                    return null;
                }
            }
            return null;
        });

        return dialog;
    }

    private void updateStock(ItemData item, double qtyChange, String type, String notes) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            String receiver = "";
            String cleanNotes = notes;

            if (type.equals("OUT")) {
                String[] parts = notes.split(" - ", 2);
                receiver = parts[0].trim();
                cleanNotes = (parts.length > 1) ? parts[1] : "";
            }

            int transactionId = 0;

            // 🧾 تسجيل العملية في StockTransactions
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO StockTransactions (ItemID, TransactionType, Quantity, ReceiverName, Notes, EmployeeID) " +
                            "VALUES (?, ?, ?, ?, ?, ?); SELECT SCOPE_IDENTITY() AS TransactionID;"
            )) {
                ps.setInt(1, item.getItemId());
                ps.setString(2, type);
                ps.setDouble(3, Math.abs(qtyChange));
                ps.setString(4, receiver.isEmpty() ? "System" : receiver);
                ps.setString(5, cleanNotes);
                ps.setInt(6, CurrentUser.getId());

                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    transactionId = rs.getInt("TransactionID");
                }
            }

            // 🧠 لو العملية صرف
            if (type.equals("OUT") && currentStockOutput != null) {
                StockOutput output = currentStockOutput;
                String usageType = output.getUsageType();

                // 🔧 حالة الصيانة
                if ("صيانة".equals(usageType)) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO MaintenanceItems (ItemID, Quantity, ReceiverName, DeviceSerial, Notes, AddedBy) VALUES (?, ?, ?, ?, ?, ?)"
                    )) {
                        ps.setInt(1, item.getItemId());
                        ps.setDouble(2, Math.abs(qtyChange));
                        ps.setString(3, output.getReceiver());
                        ps.setString(4, output.getMaintenanceDevice());
                        ps.setString(5, output.getNotes());
                        ps.setInt(6, CurrentUser.getId());
                        ps.executeUpdate();
                    }
                }

                // 🗑️ حالة التوالف
                else if ("توالف".equals(usageType)) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO ScrapItems (ItemID, Quantity, Notes, AddedBy) VALUES (?, ?, ?, ?)"
                    )) {
                        ps.setInt(1, item.getItemId());
                        ps.setDouble(2, Math.abs(qtyChange));
                        ps.setString(3, output.getNotes());
                        ps.setInt(4, CurrentUser.getId());
                        ps.executeUpdate();
                    }
                }

                // 🆕 حالة جهاز جديد
                else if ("جهاز جديد".equals(usageType)) {
                    int serialId = 0;

                    String newSerial = output.getNewSerial();
                    String selectedSerial = output.getSerialNumber();

                    // 🟢 لو اختار سيريال موجود
                    if (selectedSerial != null && !selectedSerial.trim().isEmpty()) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "SELECT SerialID FROM DeviceSerials WHERE SerialNumber = ?"
                        )) {
                            ps.setString(1, selectedSerial.trim());
                            ResultSet rs = ps.executeQuery();
                            if (rs.next()) {
                                serialId = rs.getInt("SerialID");
                            } else {
                                showError("السيريال المختار غير موجود!");
                                conn.rollback();
                                return;
                            }
                        }
                    }

                    // 🟢 لو كتب سيريال جديد
                    else if (newSerial != null && !newSerial.trim().isEmpty()) {
                        try (PreparedStatement check = conn.prepareStatement(
                                "SELECT SerialID FROM DeviceSerials WHERE SerialNumber = ?"
                        )) {
                            check.setString(1, newSerial.trim());
                            ResultSet rs = check.executeQuery();

                            if (rs.next()) {
                                // ✅ السيريال موجود بالفعل — استخدمه
                                serialId = rs.getInt("SerialID");
                            } else {
                                // ✅ السيريال غير موجود — أضفه
                                try (PreparedStatement ps = conn.prepareStatement(
                                        "INSERT INTO DeviceSerials (DeviceID, SerialNumber, AddedBy) OUTPUT INSERTED.SerialID " +
                                                "VALUES ((SELECT DeviceID FROM Devices WHERE DeviceName = ?), ?, ?)"
                                )) {
                                    ps.setString(1, output.getDeviceName());
                                    ps.setString(2, newSerial.trim());
                                    ps.setInt(3, CurrentUser.getId());
                                    ResultSet insertRs = ps.executeQuery();
                                    if (insertRs.next()) {
                                        serialId = insertRs.getInt(1);
                                    }
                                }
                            }
                        }
                    }

                    // 🚫 لا يوجد سيريال محدد أو مكتوب
                    else {
                        showError("اختر سيريال موجود أو أضف سيريال جديد أولًا!");
                        conn.rollback();
                        return;
                    }

                    // 🔗 ربط المكونات بالسيريال
                    if (serialId > 0) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "INSERT INTO SerialComponentUsage (SerialID, ItemID, Quantity, TransactionID, UsedBy) VALUES (?, ?, ?, ?, ?)"
                        )) {
                            ps.setInt(1, serialId);
                            ps.setInt(2, item.getItemId());
                            ps.setDouble(3, Math.abs(qtyChange));
                            ps.setInt(4, transactionId);
                            ps.setInt(5, CurrentUser.getId());
                            ps.executeUpdate();
                        }
                    }
                }
            }

            // ✅ تنفيذ العملية فعليًا
            conn.commit();

            // 🖨️ محاولة الطباعة
            if (type.equals("OUT")) {
                try {
                    RawThermalPrinter.printReceiptAsImage(
                            item.getItemName(),
                            item.getUnit(),
                            Math.abs(qtyChange),
                            receiver,
                            CurrentUser.getName(),
                            cleanNotes
                    );
                } catch (Exception e) {
                    showError("تم الصرف بنجاح، ولكن حدث خطأ أثناء الطباعة:\n" + e.getMessage());
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
            showError("خطأ في تحديث المخزون: " + e.getMessage());
        }
    }

    @FXML
    private void onExportClicked() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("اختر مكان حفظ ملف Excel");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
            fileChooser.setInitialFileName("Stock_Report.xlsx");
            java.io.File file = fileChooser.showSaveDialog(null);
            if (file == null) return;

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("Stock Data");

            Row header = sheet.createRow(0);
            String[] columns = {"كود الصنف", "اسم الصنف", "الوحدة", "الكمية", "الحد الأدنى", "الحالة"};
            for (int i = 0; i < columns.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
                CellStyle style = workbook.createCellStyle();
                Font font = workbook.createFont();
                font.setBold(true);
                style.setFont(font);
                cell.setCellStyle(style);
            }

            List<ItemData> dataToExport = stockTable.getItems();
            int rowIdx = 1;
            for (ItemData item : dataToExport) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(item.getItemCode() != null ? item.getItemCode() : "");
                row.createCell(1).setCellValue(item.getItemName());
                row.createCell(2).setCellValue(item.getUnit());
                row.createCell(3).setCellValue(item.getQuantity());
                row.createCell(4).setCellValue(item.getMinQuantity());
                row.createCell(5).setCellValue(item.getStatus());
            }

            for (int i = 0; i < columns.length; i++) sheet.autoSizeColumn(i);

            try (FileOutputStream out = new FileOutputStream(file)) {
                workbook.write(out);
            }
            workbook.close();

            LogService.addLog("EXPORT_REPORT", "تم تصدير تقرير المخزون إلى Excel");

            showInfo("تم تصدير الملف بنجاح:\n" + file.getAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
            showError("حدث خطأ أثناء التصدير: " + e.getMessage());
        }
    }

    @FXML
    private void onRefreshClicked() {
        loadStockData();
        LogService.addLog("REFRESH_DATA", "تم تحديث بيانات المخزون");
        showInfo("تم تحديث البيانات");
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("خطأ");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("تم بنجاح");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    public static class ItemData {
        private final int itemId;
        private final String itemCode;
        private final String itemName;
        private final String unit;
        private final double quantity;
        private final double minQuantity;
        private final String status;

        public ItemData(int itemId, String itemCode, String itemName, String unit, double quantity, double minQuantity, String status) {
            this.itemId = itemId;
            this.itemCode = itemCode;
            this.itemName = itemName;
            this.unit = unit;
            this.quantity = quantity;
            this.minQuantity = minQuantity;
            this.status = status;
        }

        public int getItemId() { return itemId; }
        public String getItemCode() { return itemCode; }
        public String getItemName() { return itemName; }
        public String getUnit() { return unit; }
        public double getQuantity() { return quantity; }
        public double getMinQuantity() { return minQuantity; }
        public String getStatus() { return status; }
    }
    private static class StockInput {
        private final double quantity;
        private final String notes;

        public StockInput(double quantity, String notes) {
            this.quantity = quantity;
            this.notes = notes;
        }

        public double getQuantity() { return quantity; }
        public String getNotes() { return notes; }
    }

    private static class StockOutput {
        private final double quantity;
        private final String receiver;
        private final String notes;
        private final String deviceName;
        private final String serialNumber;
        private final String newSerial;
        private final String usageType;
        private final String maintenanceDevice;

        public StockOutput(double quantity, String receiver, String notes,
                           String deviceName, String serialNumber, String newSerial,
                           String usageType, String maintenanceDevice) {
            this.quantity = quantity;
            this.receiver = receiver;
            this.notes = notes;
            this.deviceName = deviceName;
            this.serialNumber = serialNumber;
            this.newSerial = newSerial;
            this.usageType = usageType;
            this.maintenanceDevice = maintenanceDevice;
        }

        public double getQuantity() { return quantity; }
        public String getReceiver() { return receiver; }
        public String getNotes() { return notes; }
        public String getDeviceName() { return deviceName; }
        public String getSerialNumber() { return serialNumber; }
        public String getNewSerial() { return newSerial; }
        public String getUsageType() { return usageType; }
        public String getMaintenanceDevice() { return maintenanceDevice; }
    }

    private static class ExceedRequest {
        private final String reason;
        private final String defectiveNumber;

        public ExceedRequest(String reason, String defectiveNumber) {
            this.reason = reason;
            this.defectiveNumber = defectiveNumber;
        }

        public String getReason() { return reason; }
        public String getDefectiveNumber() { return defectiveNumber; }
    }
}