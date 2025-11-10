package app.controllers;

import app.db.DatabaseConnection;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class DeviceExitController {

    @FXML
    private TableView<FinishedDevice> exitTable;

    @FXML
    private TableColumn<FinishedDevice, String> colDevice;

    @FXML
    private TableColumn<FinishedDevice, String> colSerial;

    @FXML
    private TableColumn<FinishedDevice, Double> colPrice;

    @FXML
    private TableColumn<FinishedDevice, Double> colExceeded;

    @FXML
    private TableColumn<FinishedDevice, String> colDate;

    @FXML
    private TableColumn<FinishedDevice, String> colDeliveredBy;

    @FXML
    private TableColumn<FinishedDevice, String> colDeliveredTo;

    @FXML
    private TextField searchField;

    @FXML
    private DatePicker startDatePicker;

    @FXML
    private DatePicker endDatePicker;

    @FXML
    private Button filterBtn;

    @FXML
    private Button clearFilterBtn;

    @FXML
    private Button exportBtn;

    private ObservableList<FinishedDevice> deviceList = FXCollections.observableArrayList();
    private ObservableList<FinishedDevice> filteredList = FXCollections.observableArrayList();

    public void initialize() {
        setupTableColumns();
        setupEventHandlers();
        loadData();
    }

    private void setupTableColumns() {
        colDevice.setCellValueFactory(new PropertyValueFactory<>("deviceName"));
        colSerial.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("finalPrice"));
        colExceeded.setCellValueFactory(new PropertyValueFactory<>("exceededPrice"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("exitDate"));
        colDeliveredBy.setCellValueFactory(new PropertyValueFactory<>("deliveredBy"));
        colDeliveredTo.setCellValueFactory(new PropertyValueFactory<>("deliveredTo"));

        // تنسيق الأعمدة الرقمية لعرض العملة
        colPrice.setCellFactory(column -> new TableCell<FinishedDevice, Double>() {
            @Override
            protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                if (empty || price == null) {
                    setText(null);
                } else {
                    setText(String.format("%,.2f ج.م", price));
                    setStyle("-fx-text-fill: #2e7d32; -fx-font-weight: bold;");
                }
            }
        });

        colExceeded.setCellFactory(column -> new TableCell<FinishedDevice, Double>() {
            @Override
            protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                if (empty || price == null) {
                    setText(null);
                } else {
                    setText(String.format("%,.2f ج.م", price));
                    if (price > 0) {
                        setStyle("-fx-text-fill: #d32f2f; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #666;");
                    }
                }
            }
        });

        // جعل عرض الأعمدة مناسباً
        colDevice.setPrefWidth(150);
        colSerial.setPrefWidth(120);
        colPrice.setPrefWidth(120);
        colExceeded.setPrefWidth(120);
        colDate.setPrefWidth(150);
        colDeliveredBy.setPrefWidth(120);
        colDeliveredTo.setPrefWidth(120);
    }

    private void setupEventHandlers() {
        // البحث أثناء الكتابة
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterTable());

        // زر التصفية
        filterBtn.setOnAction(e -> filterByDate());

        // زر مسح الفلتر
        clearFilterBtn.setOnAction(e -> clearFilters());

        // زر التصدير
        exportBtn.setOnAction(e -> exportToExcel());
    }

    private void loadData() {
        deviceList.clear();

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DatabaseConnection.getConnection();
            String sql = "SELECT DeviceName, SerialNumber, FinalPrice, ExceededPrice, ExitDate, DeliveredBy, DeliveredTo FROM DeviceExit ORDER BY ExitDate DESC";
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();

            while (rs.next()) {
                deviceList.add(new FinishedDevice(
                        rs.getString("DeviceName"),
                        rs.getString("SerialNumber"),
                        rs.getDouble("FinalPrice"),
                        rs.getDouble("ExceededPrice"),
                        formatDate(rs.getTimestamp("ExitDate")),
                        rs.getString("DeliveredBy"),
                        rs.getString("DeliveredTo")
                ));
            }

            filteredList.setAll(deviceList);
            exitTable.setItems(filteredList);

            showAlert("تم تحميل " + deviceList.size() + " جهاز", Alert.AlertType.INFORMATION);

        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("خطأ في تحميل البيانات: " + e.getMessage(), Alert.AlertType.ERROR);
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void filterByDate() {
        LocalDate startDate = startDatePicker.getValue();
        LocalDate endDate = endDatePicker.getValue();

        if (startDate == null && endDate == null) {
            filteredList.setAll(deviceList);
            return;
        }

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DatabaseConnection.getConnection();
            StringBuilder sql = new StringBuilder(
                    "SELECT DeviceName, SerialNumber, FinalPrice, ExceededPrice, ExitDate, DeliveredBy, DeliveredTo FROM DeviceExit WHERE 1=1"
            );

            if (startDate != null) {
                sql.append(" AND CAST(ExitDate AS DATE) >= ?");
            }
            if (endDate != null) {
                sql.append(" AND CAST(ExitDate AS DATE) <= ?");
            }
            sql.append(" ORDER BY ExitDate DESC");

            stmt = conn.prepareStatement(sql.toString());

            int paramIndex = 1;
            if (startDate != null) {
                stmt.setDate(paramIndex++, Date.valueOf(startDate));
            }
            if (endDate != null) {
                stmt.setDate(paramIndex, Date.valueOf(endDate));
            }

            rs = stmt.executeQuery();

            filteredList.clear();
            while (rs.next()) {
                filteredList.add(new FinishedDevice(
                        rs.getString("DeviceName"),
                        rs.getString("SerialNumber"),
                        rs.getDouble("FinalPrice"),
                        rs.getDouble("ExceededPrice"),
                        formatDate(rs.getTimestamp("ExitDate")),
                        rs.getString("DeliveredBy"),
                        rs.getString("DeliveredTo")
                ));
            }

            exitTable.setItems(filteredList);
            showAlert("تم العثور على " + filteredList.size() + " جهاز", Alert.AlertType.INFORMATION);

        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("خطأ في التصفية: " + e.getMessage(), Alert.AlertType.ERROR);
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void filterTable() {
        String query = searchField.getText();
        if (query == null || query.trim().isEmpty()) {
            exitTable.setItems(filteredList);
            return;
        }

        String lowerQuery = query.toLowerCase();

        ObservableList<FinishedDevice> searchFiltered = FXCollections.observableArrayList();
        for (FinishedDevice d : filteredList) {
            if (d.getDeviceName().toLowerCase().contains(lowerQuery) ||
                    d.getSerialNumber().toLowerCase().contains(lowerQuery) ||
                    d.getDeliveredBy().toLowerCase().contains(lowerQuery) ||
                    d.getDeliveredTo().toLowerCase().contains(lowerQuery) ||
                    String.valueOf(d.getFinalPrice()).contains(lowerQuery) ||
                    String.valueOf(d.getExceededPrice()).contains(lowerQuery)) {
                searchFiltered.add(d);
            }
        }

        exitTable.setItems(searchFiltered);
    }

    private void clearFilters() {
        startDatePicker.setValue(null);
        endDatePicker.setValue(null);
        searchField.clear();
        filteredList.setAll(deviceList);
        exitTable.setItems(filteredList);
        showAlert("تم مسح جميع الفلاتر", Alert.AlertType.INFORMATION);
    }

    private void exportToExcel() {
        if (filteredList.isEmpty()) {
            showAlert("لا توجد بيانات للتصدير", Alert.AlertType.WARNING);
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("حفظ تقرير الأجهزة");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
        );

        String fileName = "تقرير_الأجهزة_" +
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".xlsx";
        fileChooser.setInitialFileName(fileName);

        File file = fileChooser.showSaveDialog(exportBtn.getScene().getWindow());
        if (file == null) return;

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("الأجهزة التي خرجت");

            // تنسيقات
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle moneyStyle = workbook.createCellStyle();
            moneyStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

            // العنوان
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("تقرير الأجهزة التي خرجت من المصنع");
            titleCell.setCellStyle(headerStyle);

            // الرأس
            Row headerRow = sheet.createRow(2);
            String[] headers = {"اسم الجهاز", "السيريال", "السعر النهائي", "السعر المتجاوز", "التاريخ", "المرسل", "المستلم"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.autoSizeColumn(i);
            }

            // البيانات
            int rowNum = 3;
            for (FinishedDevice device : filteredList) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(device.getDeviceName());
                row.createCell(1).setCellValue(device.getSerialNumber());

                Cell priceCell = row.createCell(2);
                priceCell.setCellValue(device.getFinalPrice());
                priceCell.setCellStyle(moneyStyle);

                Cell exceededCell = row.createCell(3);
                exceededCell.setCellValue(device.getExceededPrice());
                exceededCell.setCellStyle(moneyStyle);

                row.createCell(4).setCellValue(device.getExitDate());
                row.createCell(5).setCellValue(device.getDeliveredBy());
                row.createCell(6).setCellValue(device.getDeliveredTo());
            }

            // الإجماليات
            Row totalRow = sheet.createRow(rowNum + 1);
            totalRow.createCell(0).setCellValue("الإجماليات:");

            Cell totalPriceCell = totalRow.createCell(2);
            totalPriceCell.setCellValue(filteredList.stream().mapToDouble(FinishedDevice::getFinalPrice).sum());
            totalPriceCell.setCellStyle(moneyStyle);

            Cell totalExceededCell = totalRow.createCell(3);
            totalExceededCell.setCellValue(filteredList.stream().mapToDouble(FinishedDevice::getExceededPrice).sum());
            totalExceededCell.setCellStyle(moneyStyle);

            try (FileOutputStream fos = new FileOutputStream(file)) {
                workbook.write(fos);
            }

            showAlert("تم تصدير التقرير بنجاح إلى: " + file.getAbsolutePath(), Alert.AlertType.INFORMATION);

        } catch (Exception e) {
            e.printStackTrace();
            showAlert("خطأ في التصدير: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private String formatDate(Timestamp timestamp) {
        if (timestamp == null) return "-";
        LocalDateTime dateTime = timestamp.toLocalDateTime();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        return dateTime.format(formatter);
    }

    private void showAlert(String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(type == Alert.AlertType.ERROR ? "خطأ" : "معلومة");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static class FinishedDevice {
        private String deviceName;
        private String serialNumber;
        private double finalPrice;
        private double exceededPrice;
        private String exitDate;
        private String deliveredBy;
        private String deliveredTo;

        public FinishedDevice(String deviceName, String serialNumber, double finalPrice,
                              double exceededPrice, String exitDate, String deliveredBy,
                              String deliveredTo) {
            this.deviceName = deviceName;
            this.serialNumber = serialNumber;
            this.finalPrice = finalPrice;
            this.exceededPrice = exceededPrice;
            this.exitDate = exitDate;
            this.deliveredBy = deliveredBy;
            this.deliveredTo = deliveredTo;
        }

        public String getDeviceName() { return deviceName; }
        public String getSerialNumber() { return serialNumber; }
        public double getFinalPrice() { return finalPrice; }
        public double getExceededPrice() { return exceededPrice; }
        public String getExitDate() { return exitDate; }
        public String getDeliveredBy() { return deliveredBy; }
        public String getDeliveredTo() { return deliveredTo; }
    }
}