package app.controllers;

import app.current_user.CurrentUser;
import app.db.DatabaseConnection;
import app.services.LogService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Predicate;

public class SerialMaintenanceHistoryController {

    @FXML private TableView<SerialMaintenanceData> dataTable;
    @FXML private TableColumn<SerialMaintenanceData, String> serialColumn;
    @FXML private TableColumn<SerialMaintenanceData, String> deviceColumn;
    @FXML private TableColumn<SerialMaintenanceData, String> itemNameColumn;
    @FXML private TableColumn<SerialMaintenanceData, Double> quantityColumn;
    @FXML private TableColumn<SerialMaintenanceData, Double> unitPriceColumn;
    @FXML private TableColumn<SerialMaintenanceData, Double> totalPriceColumn;
    @FXML private TableColumn<SerialMaintenanceData, String> receiverColumn;
    @FXML private TableColumn<SerialMaintenanceData, String> employeeColumn;
    @FXML private TableColumn<SerialMaintenanceData, String> dateColumn;
    @FXML private TableColumn<SerialMaintenanceData, String> notesColumn;

    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;
    @FXML private TextField searchField;
    @FXML private Label totalCostLabel;
    @FXML private Label totalItemsLabel;
    @FXML private Label uniqueSerialsLabel;

    private final ObservableList<SerialMaintenanceData> allData = FXCollections.observableArrayList();
    private final FilteredList<SerialMaintenanceData> filteredData = new FilteredList<>(allData);
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML
    public void initialize() {
        setupTableColumns();
        setupDateFilters();
        setupSearchFilter();
        loadData();
        updateStatistics();
    }

    private void setupTableColumns() {
        serialColumn.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));
        deviceColumn.setCellValueFactory(new PropertyValueFactory<>("deviceName"));
        itemNameColumn.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        unitPriceColumn.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));
        totalPriceColumn.setCellValueFactory(new PropertyValueFactory<>("totalPrice"));
        receiverColumn.setCellValueFactory(new PropertyValueFactory<>("receiver"));
        employeeColumn.setCellValueFactory(new PropertyValueFactory<>("employeeName"));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("dateAdded"));
        notesColumn.setCellValueFactory(new PropertyValueFactory<>("notes"));

        // تنسيق الأعمدة الرقمية
        quantityColumn.setCellFactory(col -> new TableCell<SerialMaintenanceData, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%.2f", item));
                }
            }
        });

        unitPriceColumn.setCellFactory(col -> new TableCell<SerialMaintenanceData, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%.2f جنيه", item));
                }
            }
        });

        totalPriceColumn.setCellFactory(col -> new TableCell<SerialMaintenanceData, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%.2f جنيه", item));
                    setStyle("-fx-font-weight: bold; -fx-text-fill: #D32F2F;");
                }
            }
        });
    }

    private void setupDateFilters() {
        // تعيين التاريخ الافتراضي (آخر 30 يوم)
        fromDatePicker.setValue(LocalDate.now().minusDays(30));
        toDatePicker.setValue(LocalDate.now());

        // إضافة listeners للتحديث التلقائي
        fromDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        toDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());
    }

    private void setupSearchFilter() {
        // جعل البحث في الوقت الحقيقي
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            applyFilters();
        });

        // إضافة placeholder للبحث
        searchField.setPromptText("ابحث في السيريال، العنصر، المستلم، الموظف...");

        // استخدام SortedList للفلترة
        SortedList<SerialMaintenanceData> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(dataTable.comparatorProperty());
        dataTable.setItems(sortedData);
    }

    private void applyFilters() {
        filteredData.setPredicate(createCombinedPredicate());
        updateStatistics();
    }

    private Predicate<SerialMaintenanceData> createCombinedPredicate() {
        return item -> {
            // فلترة بالبحث
            String searchTerm = searchField.getText().toLowerCase().trim();
            if (!searchTerm.isEmpty()) {
                boolean matchesSearch =
                        (item.getSerialNumber() != null && item.getSerialNumber().toLowerCase().contains(searchTerm)) ||
                                (item.getDeviceName() != null && item.getDeviceName().toLowerCase().contains(searchTerm)) ||
                                (item.getItemName() != null && item.getItemName().toLowerCase().contains(searchTerm)) ||
                                (item.getReceiver() != null && item.getReceiver().toLowerCase().contains(searchTerm)) ||
                                (item.getEmployeeName() != null && item.getEmployeeName().toLowerCase().contains(searchTerm)) ||
                                (item.getNotes() != null && item.getNotes().toLowerCase().contains(searchTerm));

                if (!matchesSearch) {
                    return false;
                }
            }

            // فلترة بالتاريخ
            LocalDate fromDate = fromDatePicker.getValue();
            LocalDate toDate = toDatePicker.getValue();

            if (fromDate != null || toDate != null) {
                try {
                    String dateStr = item.getDateAdded();
                    LocalDate itemDate = LocalDate.parse(dateStr.substring(0, 10));

                    if (fromDate != null && itemDate.isBefore(fromDate)) {
                        return false;
                    }
                    if (toDate != null && itemDate.isAfter(toDate)) {
                        return false;
                    }
                } catch (Exception e) {
                    return false;
                }
            }

            return true;
        };
    }

    private void loadData() {
        allData.clear();

        String query = """
            SELECT 
                mi.DeviceSerial AS SerialNumber,
                mi.ReceiverName,
                i.ItemName,
                mi.Quantity,
                COALESCE(ip.UnitPrice, 0) AS UnitPrice,
                (mi.Quantity * COALESCE(ip.UnitPrice, 0)) AS TotalPrice,
                mi.DateAdded,
                mi.Notes,
                mi.AddedBy
            FROM MaintenanceItems mi
            INNER JOIN Items i ON mi.ItemID = i.ItemID
            LEFT JOIN ItemPrices ip ON i.ItemID = ip.ItemID
            ORDER BY mi.DateAdded DESC
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String serialNumber = rs.getString("SerialNumber");
                String receiver = rs.getString("ReceiverName");
                String itemName = rs.getString("ItemName");
                double quantity = rs.getDouble("Quantity");
                double unitPrice = rs.getDouble("UnitPrice");
                double totalPrice = rs.getDouble("TotalPrice");

                // هنا بنحول AddedBy لاسم الموظف
                int addedByUserId = rs.getInt("AddedBy");
                String employeeName = getEmployeeName(addedByUserId);

                Timestamp dateAdded = rs.getTimestamp("DateAdded");
                String notes = rs.getString("Notes");
                String formattedDate = dateAdded != null ?
                        dateAdded.toLocalDateTime().format(dateFormatter) : "غير محدد";

                allData.add(new SerialMaintenanceData(
                        serialNumber != null ? serialNumber : "بدون سيريال",
                        "جهاز الصيانة",
                        itemName, quantity, unitPrice, totalPrice,
                        receiver, employeeName, formattedDate, notes
                ));
            }

            applyFilters(); // تطبيق الفلترة بعد التحميل

        } catch (SQLException e) {
            e.printStackTrace();
            showError("خطأ في تحميل بيانات الصيانة: " + e.getMessage());
        }
    }

    private String getEmployeeName(int userId) {
        if (userId == CurrentUser.getId()) {
            return CurrentUser.getName();
        }
        return "موظف #" + userId;
    }

    @FXML
    private void onSearchClicked() {
        // خلاص السيرش بيشتغل في الوقت الحقيقي، لكن ممكن نضيف تركيز على حقل البحث
        searchField.requestFocus();
    }

    @FXML
    private void onResetClicked() {
        fromDatePicker.setValue(LocalDate.now().minusDays(30));
        toDatePicker.setValue(LocalDate.now());
        searchField.clear();
        applyFilters();
    }

    private void updateStatistics() {
        ObservableList<SerialMaintenanceData> currentData = dataTable.getItems();
        double totalCost = 0;
        int totalItems = currentData.size();

        // حساب إجمالي التكلفة
        for (SerialMaintenanceData item : currentData) {
            totalCost += item.getTotalPrice();
        }

        // حساب عدد السيريالات الفريدة
        long uniqueSerials = currentData.stream()
                .map(SerialMaintenanceData::getSerialNumber)
                .distinct()
                .count();

        totalCostLabel.setText(String.format("إجمالي التكلفة: %.2f جنيه", totalCost));
        totalItemsLabel.setText(String.format("إجمالي العناصر: %d", totalItems));
        uniqueSerialsLabel.setText(String.format("عدد السيريالات: %d", uniqueSerials));
    }

    @FXML
    private void onExportClicked() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("اختر مكان حفظ ملف Excel");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
            fileChooser.setInitialFileName("Serial_Maintenance_History_" + System.currentTimeMillis() + ".xlsx");
            java.io.File file = fileChooser.showSaveDialog(dataTable.getScene().getWindow());

            if (file == null) return;

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("تاريخ صيانة السيريالات");

            // رأس الجدول
            Row header = sheet.createRow(0);
            String[] columns = {"السيريال", "الجهاز", "العنصر", "الكمية", "سعر الوحدة", "السعر الإجمالي",
                    "المستلم", "المسؤول", "التاريخ", "ملاحظات"};

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            // البيانات
            List<SerialMaintenanceData> exportData = dataTable.getItems();
            int rowIdx = 1;
            for (SerialMaintenanceData item : exportData) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(item.getSerialNumber());
                row.createCell(1).setCellValue(item.getDeviceName());
                row.createCell(2).setCellValue(item.getItemName());
                row.createCell(3).setCellValue(item.getQuantity());
                row.createCell(4).setCellValue(item.getUnitPrice());
                row.createCell(5).setCellValue(item.getTotalPrice());
                row.createCell(6).setCellValue(item.getReceiver());
                row.createCell(7).setCellValue(item.getEmployeeName());
                row.createCell(8).setCellValue(item.getDateAdded());
                row.createCell(9).setCellValue(item.getNotes());
            }

            // تعديل الأعمدة
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream out = new FileOutputStream(file)) {
                workbook.write(out);
            }
            workbook.close();

            LogService.addLog("EXPORT_REPORT", "تم تصدير تقرير تاريخ صيانة السيريالات إلى Excel");
            showInfo("✅ تم تصدير الملف بنجاح:\n" + file.getAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
            showError("حدث خطأ أثناء التصدير: " + e.getMessage());
        }
    }

    @FXML
    private void onRefreshClicked() {
        loadData();
        LogService.addLog("REFRESH_DATA", "تم تحديث بيانات تاريخ صيانة السيريالات");
        showInfo("✅ تم تحديث البيانات");
    }

    // ✅ كلاس للبيانات
    public static class SerialMaintenanceData {
        private final String serialNumber;
        private final String deviceName;
        private final String itemName;
        private final double quantity;
        private final double unitPrice;
        private final double totalPrice;
        private final String receiver;
        private final String employeeName;
        private final String dateAdded;
        private final String notes;

        public SerialMaintenanceData(String serialNumber, String deviceName, String itemName,
                                     double quantity, double unitPrice, double totalPrice,
                                     String receiver, String employeeName, String dateAdded, String notes) {
            this.serialNumber = serialNumber;
            this.deviceName = deviceName;
            this.itemName = itemName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.totalPrice = totalPrice;
            this.receiver = receiver;
            this.employeeName = employeeName;
            this.dateAdded = dateAdded;
            this.notes = notes;
        }

        // Getters
        public String getSerialNumber() { return serialNumber; }
        public String getDeviceName() { return deviceName; }
        public String getItemName() { return itemName; }
        public double getQuantity() { return quantity; }
        public double getUnitPrice() { return unitPrice; }
        public double getTotalPrice() { return totalPrice; }
        public String getReceiver() { return receiver; }
        public String getEmployeeName() { return employeeName; }
        public String getDateAdded() { return dateAdded; }
        public String getNotes() { return notes; }
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
}