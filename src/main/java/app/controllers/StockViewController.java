package app.controllers;

import app.db.DatabaseConnection;
import app.services.LogService;
import app.utils.RawThermalPrinter;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
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
    @FXML private TextField searchField;
    @FXML private Button refreshButton;
    @FXML private ComboBox<String> statusFilterCombo;

    private final ObservableList<ItemData> allItems = FXCollections.observableArrayList();
    private StockOutput currentStockOutput;

    @FXML
    public void initialize() {
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        unitColumn.setCellValueFactory(new PropertyValueFactory<>("unit"));
        quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        minQuantityColumn.setCellValueFactory(new PropertyValueFactory<>("minQuantity"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        loadStockData();

        // ✅ إضافة سيرش فوري
        searchField.textProperty().addListener((obs, oldValue, newValue) -> {
            filterItems(newValue);
        });

        statusFilterCombo.setItems(FXCollections.observableArrayList("الكل", "✅ OK", "⚠️ Low Stock"));
        statusFilterCombo.setValue("الكل");
        statusFilterCombo.setOnAction(e -> filterByStatus());
    }

    private void loadStockData() {
        allItems.clear();

        String query = """
            SELECT 
                i.ItemID,
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
                String name = rs.getString("ItemName");
                String unit = rs.getString("UnitName");
                double qty = rs.getDouble("Quantity");
                double minQty = rs.getDouble("MinQuantity");

                String status = (qty < minQty) ? "⚠️ Low Stock" : "✅ OK";

                allItems.add(new ItemData(itemId, name, unit, qty, minQty, status));
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

    // ✅ سيرش متقدم بالاسم والكمية والـ ID
    private void filterItems(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            filterByStatus();
            return;
        }

        ObservableList<ItemData> filtered = FXCollections.observableArrayList();
        String searchTerm = keyword.toLowerCase().trim();

        for (ItemData item : allItems) {
            // البحث بالاسم
            boolean matchesName = item.getItemName().toLowerCase().contains(searchTerm);

            // البحث بالكمية
            boolean matchesQuantity = String.valueOf(item.getQuantity()).contains(searchTerm);

            // البحث بالـ ID
            boolean matchesId = String.valueOf(item.getItemId()).contains(searchTerm);

            // البحث بالحالة
            boolean matchesStatus = item.getStatus().toLowerCase().contains(searchTerm);

            if (matchesName || matchesQuantity || matchesId || matchesStatus) {
                filtered.add(item);
            }
        }
        stockTable.setItems(filtered);
    }

    // ✅ إضافة كمية (IN) - مع وصف واضح
    @FXML
    private void onAddStock() {
        ItemData selectedItem = stockTable.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            showError("⚠️ يرجى اختيار صنف أولاً!");
            return;
        }

        Dialog<StockInput> dialog = createStockDialog("إضافة كمية", "أدخل الكمية المضافة:");

        Optional<StockInput> result = dialog.showAndWait();
        if (result.isPresent()) {
            StockInput input = result.get();
            try {
                double qty = input.getQuantity();
                if (qty <= 0) {
                    showError("❌ الكمية يجب أن تكون أكبر من الصفر!");
                    return;
                }

                // ✅ وصف واضح بدون رموز
                String description = String.format("تم إضافة %.2f وحدة من الصنف %s - الملاحظات: %s",
                        qty, selectedItem.getItemName(),
                        input.getNotes().isEmpty() ? "لا توجد ملاحظات" : input.getNotes());

                LogService.addLog("STOCK_IN", description);
                updateStock(selectedItem, qty, "IN", input.getNotes());
                loadStockData();

                showInfo("✅ تم إضافة الكمية بنجاح!");

            } catch (NumberFormatException e) {
                showError("❌ قيمة الكمية غير صالحة!");
            }
        }
    }

    // ✅ صرف كمية (OUT) - مع وصف واضح
    @FXML
    private void onRemoveStock() {
        ItemData selectedItem = stockTable.getSelectionModel().getSelectedItem();

        if (selectedItem == null) {
            showError("⚠️ يرجى اختيار صنف أولاً!");
            return;
        }

        Dialog<StockOutput> dialog = createStockOutDialog("صرف كمية", "أدخل الكمية المراد صرفها:");
        Optional<StockOutput> result = dialog.showAndWait();

        if (result.isPresent()) {
            StockOutput input = result.get();

            try {
                double qty = input.getQuantity();

                if (qty <= 0) {
                    showError("❌ الكمية يجب أن تكون أكبر من الصفر!");
                    return;
                }

                if (qty > selectedItem.getQuantity()) {
                    showError("❌ الكمية المطلوبة تتجاوز المخزون المتاح!");
                    return;
                }

                // ✅ التحقق من إدخال المستلم في حالة الصيانة
                if (input.getUsageType().equals("صيانة") && (input.getReceiver() == null || input.getReceiver().trim().isEmpty())) {
                    showError("❌ في حالة الصيانة يجب إدخال اسم المستلم!");
                    return;
                }

                // ✅ خزن بيانات الصرف مؤقتًا لاستخدامها في updateStock()
                currentStockOutput = input;

                String description = String.format(
                        "تم صرف %.2f وحدة من الصنف %s - المستلم: %s - نوع الاستخدام: %s - الملاحظات: %s",
                        qty, selectedItem.getItemName(),
                        input.getReceiver() != null ? input.getReceiver() : "غير محدد",
                        input.getUsageType(),
                        input.getNotes().isEmpty() ? "لا توجد ملاحظات" : input.getNotes()
                );

                LogService.addLog("STOCK_OUT", description);

                // ✅ تنفيذ عملية الصرف وتسجيلها في الجداول
                updateStock(selectedItem, -qty, "OUT", input.getReceiver() + " - " + input.getNotes());

                // ✅ تحديث الجدول بعد العملية
                loadStockData();

                showInfo("✅ تم صرف الكمية بنجاح!");
            } catch (NumberFormatException e) {
                showError("❌ قيمة الكمية غير صالحة!");
            } finally {
                // ✅ تفريغ القيمة بعد العملية لتجنب التداخل مع العمليات التالية
                currentStockOutput = null;
            }
        }
    }

    // ✅ إنشاء دايلوج للإضافة
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

        // التحقق من البيانات
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButton) {
                if (quantityField.getText().trim().isEmpty()) {
                    showError("❌ يرجى إدخال الكمية!");
                    return null;
                }
                try {
                    double qty = Double.parseDouble(quantityField.getText());
                    return new StockInput(qty, notesField.getText());
                } catch (NumberFormatException e) {
                    showError("❌ قيمة الكمية غير صالحة!");
                    return null;
                }
            }
            return null;
        });

        return dialog;
    }
    // ✅ إنشاء Combobox مع خاصية البحث والأوتوكومبليت
    private <T> void setupSearchableComboBox(ComboBox<T> comboBox, ObservableList<T> items) {
        comboBox.setItems(items);
        comboBox.setEditable(true);

        TextField editor = comboBox.getEditor();
        FilteredList<T> filteredItems = new FilteredList<>(items);

        editor.textProperty().addListener((obs, oldValue, newValue) -> {
            filteredItems.setPredicate(item -> {
                if (newValue == null || newValue.isEmpty()) return true;
                String filterText = newValue.toLowerCase();
                return item.toString().toLowerCase().contains(filterText);
            });

            // تحديث القائمة المنسدلة
            comboBox.setItems(filteredItems);
            comboBox.show();
        });

        // إعادة تعيين القائمة عند فقدان التركيز
        editor.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                comboBox.setItems(items);
            }
        });
    }
    // ✅ إنشاء دايلوج للصرف مع التحكم الديناميكي حسب نوع الاستخدام
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
        TextArea notesField = new TextArea();
        notesField.setPromptText("ملاحظات (اختياري)");
        notesField.setPrefRowCount(2);

        // ✅ إعداد نوع الاستخدام
        usageType.setItems(FXCollections.observableArrayList("جهاز جديد", "صيانة", "توالف"));
        usageType.setValue("جهاز جديد");

        // ✅ تحميل الأجهزة
        try (Connection conn = DatabaseConnection.getConnection()) {
            ResultSet rs = conn.prepareStatement("SELECT DeviceName FROM Devices").executeQuery();
            while (rs.next()) deviceCombo.getItems().add(rs.getString(1));
        } catch (Exception e) { e.printStackTrace(); }

        // ✅ تحميل السيريالات عند اختيار الجهاز
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

        // ✅ التحكم الديناميكي في الحقول حسب نوع الاستخدام
        usageType.setOnAction(e -> {
            String selectedUsage = usageType.getValue();
            boolean isNewDevice = "جهاز جديد".equals(selectedUsage);
            boolean isMaintenance = "صيانة".equals(selectedUsage);
            boolean isScrap = "توالف".equals(selectedUsage);

            // ✅ تفعيل/تعطيل الحقول حسب نوع الاستخدام
            deviceCombo.setDisable(!isNewDevice);
            serialCombo.setDisable(!isNewDevice);
            newSerialField.setDisable(!isNewDevice);

            // ✅ تفعيل حقل المستلم في حالة الصيانة فقط
            receiverField.setDisable(!isMaintenance && !isNewDevice);

            // ✅ إعادة تعيين الحقول المعطلة
            if (!isNewDevice) {
                deviceCombo.setValue(null);
                serialCombo.setValue(null);
                newSerialField.clear();
            }
            if (!isMaintenance) {
                receiverField.clear();
            }
        });

        // ✅ إضافة الحقول للشبكة
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

        grid.add(new Label("ملاحظات:"), 0, 6);
        grid.add(notesField, 1, 6);

        dialog.getDialogPane().setContent(grid);

        // ✅ تطبيق الإعدادات الأولية
        usageType.fireEvent(new javafx.event.ActionEvent());

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == outButton) {
                try {
                    double qty = Double.parseDouble(quantityField.getText());
                    String usage = usageType.getValue();

                    // ✅ التحقق من البيانات المطلوبة حسب نوع الاستخدام
                    if ("جهاز جديد".equals(usage) && deviceCombo.getValue() == null) {
                        showError("❌ في حالة جهاز جديد يجب اختيار الجهاز!");
                        return null;
                    }

                    if ("صيانة".equals(usage) && (receiverField.getText() == null || receiverField.getText().trim().isEmpty())) {
                        showError("❌ في حالة الصيانة يجب إدخال اسم المستلم!");
                        return null;
                    }

                    return new StockOutput(
                            qty,
                            receiverField.getText(),
                            notesField.getText(),
                            deviceCombo.getValue(),
                            serialCombo.getValue(),
                            newSerialField.getText(),
                            usageType.getValue()
                    );
                } catch (Exception e) {
                    showError("❌ رجاء إدخال البيانات صحيحة!");
                    return null;
                }
            }
            return null;
        });

        return dialog;
    }

    // ✅ تحديث الكمية وتسجيل العملية مع تسجيل في جداول الصيانة والتوالف
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

            // ✅ تسجيل العملية في StockTransactions
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO StockTransactions (ItemID, TransactionType, Quantity, ReceiverName, Notes, EmployeeID) VALUES (?, ?, ?, ?, ?, ?); SELECT SCOPE_IDENTITY() AS TransactionID;"
            )) {
                ps.setInt(1, item.getItemId());
                ps.setString(2, type);
                ps.setDouble(3, Math.abs(qtyChange));
                ps.setString(4, receiver.isEmpty() ? "System" : receiver);
                ps.setString(5, cleanNotes);
                ps.setInt(6, app.current_user.CurrentUser.getId());

                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    transactionId = rs.getInt("TransactionID");
                }
            }

            // ✅ لو العملية OUT فقط وبيانات السيريال موجودة
            if (type.equals("OUT") && currentStockOutput != null) {
                StockOutput output = currentStockOutput;
                String usageType = output.getUsageType();

                // ✅ تسجيل في جدول الصيانة إذا كان النوع "صيانة"
                if ("صيانة".equals(usageType)) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO MaintenanceItems (ItemID, Quantity, ReceiverName, Notes, AddedBy) VALUES (?, ?, ?, ?, ?)"
                    )) {
                        ps.setInt(1, item.getItemId());
                        ps.setDouble(2, Math.abs(qtyChange));
                        ps.setString(3, output.getReceiver());
                        ps.setString(4, output.getNotes());
                        ps.setInt(5, app.current_user.CurrentUser.getId());
                        ps.executeUpdate();
                    }
                }

                // ✅ تسجيل في جدول التوالف إذا كان النوع "توالف"
                if ("توالف".equals(usageType)) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO ScrapItems (ItemID, Quantity, Notes, AddedBy) VALUES (?, ?, ?, ?)"
                    )) {
                        ps.setInt(1, item.getItemId());
                        ps.setDouble(2, Math.abs(qtyChange));
                        ps.setString(3, output.getNotes());
                        ps.setInt(4, app.current_user.CurrentUser.getId());
                        ps.executeUpdate();
                    }
                }

                // ✅ التعامل مع السيريالات فقط في حالة "جهاز جديد"
                if ("جهاز جديد".equals(usageType)) {
                    int serialId = 0;

                    String newSerial = output.getNewSerial();
                    String selectedSerial = output.getSerialNumber();

                    // ✅ لو المستخدم اختار سيريال من الكومبو بوكس
                    if (selectedSerial != null && !selectedSerial.trim().isEmpty()) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "SELECT SerialID FROM DeviceSerials WHERE SerialNumber = ?"
                        )) {
                            ps.setString(1, selectedSerial.trim());
                            ResultSet rs = ps.executeQuery();
                            if (rs.next()) {
                                serialId = rs.getInt("SerialID");
                                System.out.println("✅ Using existing SerialID: " + serialId);
                            } else {
                                showError("⚠️ السيريال المختار غير موجود!");
                                conn.rollback();
                                return;
                            }
                        }

                    } else if (newSerial != null && !newSerial.trim().isEmpty()) {
                        // ✅ في حالة إضافة سيريال جديد
                        try (PreparedStatement check = conn.prepareStatement(
                                "SELECT COUNT(*) FROM DeviceSerials WHERE SerialNumber = ?"
                        )) {
                            check.setString(1, newSerial.trim());
                            ResultSet rs = check.executeQuery();
                            if (rs.next() && rs.getInt(1) > 0) {
                                showError("❌ هذا السيريال موجود بالفعل!");
                                conn.rollback();
                                return;
                            }
                        }

                        try (PreparedStatement ps = conn.prepareStatement(
                                "INSERT INTO DeviceSerials (DeviceID, SerialNumber, AddedBy) OUTPUT INSERTED.SerialID " +
                                        "VALUES ((SELECT DeviceID FROM Devices WHERE DeviceName = ?), ?, ?)"
                        )) {
                            ps.setString(1, output.getDeviceName());
                            ps.setString(2, newSerial.trim());
                            ps.setInt(3, app.current_user.CurrentUser.getId());
                            ResultSet rs = ps.executeQuery();
                            if (rs.next()) {
                                serialId = rs.getInt(1);
                                System.out.println("🆕 Created new SerialID: " + serialId);
                            }
                        }

                    } else {
                        showError("⚠️ اختر سيريال موجود أو أضف سيريال جديد أولًا!");
                        conn.rollback();
                        return;
                    }

                    // ✅ ربط الصنف المستخدم بالسيريال
                    if (serialId > 0) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "INSERT INTO SerialComponentUsage (SerialID, ItemID, Quantity, TransactionID, UsedBy) VALUES (?, ?, ?, ?, ?)"
                        )) {
                            ps.setInt(1, serialId);
                            ps.setInt(2, item.getItemId());
                            ps.setDouble(3, Math.abs(qtyChange));
                            ps.setInt(4, transactionId);
                            ps.setInt(5, app.current_user.CurrentUser.getId());
                            ps.executeUpdate();
                        }
                    }
                }
            }

            conn.commit();

            // ✅ طباعة الريسيت بعد عملية الصرف فقط
            if (type.equals("OUT")) {
                try {
                    RawThermalPrinter.printReceiptAsImage(
                            item.getItemName(),
                            item.getUnit(),
                            Math.abs(qtyChange),
                            receiver,
                            app.current_user.CurrentUser.getName(),
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
            String[] columns = {"اسم الصنف", "الوحدة", "الكمية", "الحد الأدنى", "الحالة"};
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
                row.createCell(0).setCellValue(item.getItemName());
                row.createCell(1).setCellValue(item.getUnit());
                row.createCell(2).setCellValue(item.getQuantity());
                row.createCell(3).setCellValue(item.getMinQuantity());
                row.createCell(4).setCellValue(item.getStatus());
            }

            for (int i = 0; i < columns.length; i++) sheet.autoSizeColumn(i);

            try (FileOutputStream out = new FileOutputStream(file)) {
                workbook.write(out);
            }
            workbook.close();

            // ✅ تسجيل عملية التصدير في اللوج
            LogService.addLog("EXPORT_REPORT", "تم تصدير تقرير المخزون إلى Excel");

            showInfo("✅ تم تصدير الملف بنجاح:\n" + file.getAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
            showError("حدث خطأ أثناء التصدير: " + e.getMessage());
        }
    }

    // ✅ تحديث الجدول
    @FXML
    private void onRefreshClicked() {
        loadStockData();
        // ✅ تسجيل عملية التحديث في اللوج
        LogService.addLog("REFRESH_DATA", "تم تحديث بيانات المخزون");
        showInfo("✅ تم تحديث البيانات");
    }

    // ✅ تنبيهات
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

    // ✅ كلاسات للبيانات
    public static class ItemData {
        private final int itemId;
        private final String itemName;
        private final String unit;
        private final double quantity;
        private final double minQuantity;
        private final String status;

        public ItemData(int itemId, String itemName, String unit, double quantity, double minQuantity, String status) {
            this.itemId = itemId;
            this.itemName = itemName;
            this.unit = unit;
            this.quantity = quantity;
            this.minQuantity = minQuantity;
            this.status = status;
        }

        public int getItemId() { return itemId; }
        public String getItemName() { return itemName; }
        public String getUnit() { return unit; }
        public double getQuantity() { return quantity; }
        public double getMinQuantity() { return minQuantity; }
        public String getStatus() { return status; }
    }

    // ✅ كلاس لإدخال البيانات للإضافة
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

    // ✅ كلاس لإدخال البيانات للصرف
    private static class StockOutput {
        private final double quantity;
        private final String receiver;
        private final String notes;
        private final String deviceName;
        private final String serialNumber;
        private final String newSerial;
        private final String usageType;

        public StockOutput(double quantity, String receiver, String notes,
                           String deviceName, String serialNumber, String newSerial, String usageType) {
            this.quantity = quantity;
            this.receiver = receiver;
            this.notes = notes;
            this.deviceName = deviceName;
            this.serialNumber = serialNumber;
            this.newSerial = newSerial;
            this.usageType = usageType;
        }

        public double getQuantity() { return quantity; }
        public String getReceiver() { return receiver; }
        public String getNotes() { return notes; }
        public String getDeviceName() { return deviceName; }
        public String getSerialNumber() { return serialNumber; }
        public String getNewSerial() { return newSerial; }
        public String getUsageType() { return usageType; }
    }
}