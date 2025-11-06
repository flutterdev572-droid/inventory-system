package app.controllers;

import app.db.DatabaseConnection;
import app.current_user.CurrentUser;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyEvent;
import javafx.stage.FileChooser;
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
import java.util.List;

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
