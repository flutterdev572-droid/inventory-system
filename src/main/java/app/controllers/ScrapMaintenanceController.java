package app.controllers;

import app.current_user.CurrentUser;
import app.db.DatabaseConnection;
import app.services.LogService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.sql.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ScrapMaintenanceController {

    @FXML private TableView<ScrapMaintenanceData> dataTable;
    @FXML private TableColumn<ScrapMaintenanceData, String> typeColumn;
    @FXML private TableColumn<ScrapMaintenanceData, String> itemNameColumn;
    @FXML private TableColumn<ScrapMaintenanceData, Double> quantityColumn;
    @FXML private TableColumn<ScrapMaintenanceData, String> receiverColumn;
    @FXML private TableColumn<ScrapMaintenanceData, String> employeeColumn;
    @FXML private TableColumn<ScrapMaintenanceData, String> dateColumn;
    @FXML private TableColumn<ScrapMaintenanceData, String> notesColumn;

    @FXML private ComboBox<String> typeFilterCombo;
    @FXML private TextField searchField;

    private final ObservableList<ScrapMaintenanceData> allData = FXCollections.observableArrayList();
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML
    public void initialize() {
        setupTableColumns();
        setupFilters();
        loadData();
    }

    private void setupTableColumns() {
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
        itemNameColumn.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        receiverColumn.setCellValueFactory(new PropertyValueFactory<>("receiver"));
        employeeColumn.setCellValueFactory(new PropertyValueFactory<>("employeeName"));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("dateAdded"));
        notesColumn.setCellValueFactory(new PropertyValueFactory<>("notes"));
    }

    private void setupFilters() {
        typeFilterCombo.setItems(FXCollections.observableArrayList("الكل", "توالف", "صيانة"));
        typeFilterCombo.setValue("الكل");

        typeFilterCombo.setOnAction(e -> filterData());

        searchField.textProperty().addListener((obs, oldValue, newValue) -> {
            filterData();
        });
    }

    private void loadData() {
        allData.clear();

        try (Connection conn = DatabaseConnection.getConnection()) {
            // ✅ تحميل بيانات التوالف
            loadScrapData(conn);

            // ✅ تحميل بيانات الصيانة
            loadMaintenanceData(conn);

            dataTable.setItems(allData);

        } catch (SQLException e) {
            e.printStackTrace();
            showError("خطأ في تحميل البيانات: " + e.getMessage());
        }
    }
    @FXML
    private void onSerialHistoryClicked() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/SerialMaintenanceHistory.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("تاريخ صيانة السيريالات");
            stage.setScene(new Scene(root, 1200, 700));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
            showError("خطأ في فتح نافذة تاريخ الصيانة: " + e.getMessage());
        }
    }
    private void loadScrapData(Connection conn) throws SQLException {
        String scrapQuery = """
        SELECT 
            s.ScrapID,
            i.ItemName,
            s.Quantity,
            s.Notes,
            s.DateAdded,
            s.AddedBy
        FROM ScrapItems s
        INNER JOIN Items i ON s.ItemID = i.ItemID
        ORDER BY s.DateAdded DESC
    """;

        try (PreparedStatement ps = conn.prepareStatement(scrapQuery);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String itemName = rs.getString("ItemName");
                double quantity = rs.getDouble("Quantity");
                String notes = rs.getString("Notes");
                Timestamp dateAdded = rs.getTimestamp("DateAdded");

                // ✅ نعرض اسم الكارنت يوزر
                String employeeName = CurrentUser.getName();

                allData.add(new ScrapMaintenanceData(
                        "توالف", itemName, quantity, "غير محدد",
                        employeeName, dateAdded.toLocalDateTime().format(dateFormatter), notes
                ));
            }
        }
    }

    private void loadMaintenanceData(Connection conn) throws SQLException {
        String maintenanceQuery = """
        SELECT 
            m.MaintenanceID,
            i.ItemName,
            m.Quantity,
            m.ReceiverName,
            m.Notes,
            m.DateAdded,
            m.AddedBy
        FROM MaintenanceItems m
        INNER JOIN Items i ON m.ItemID = i.ItemID
        ORDER BY m.DateAdded DESC
    """;

        try (PreparedStatement ps = conn.prepareStatement(maintenanceQuery);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String itemName = rs.getString("ItemName");
                double quantity = rs.getDouble("Quantity");
                String receiver = rs.getString("ReceiverName");
                String notes = rs.getString("Notes");
                Timestamp dateAdded = rs.getTimestamp("DateAdded");

                // ✅ بدل جلب الاسم من الجدول → نستخدم الكارنت يوزر
                String employeeName = CurrentUser.getName();

                allData.add(new ScrapMaintenanceData(
                        "صيانة", itemName, quantity, receiver,
                        employeeName, dateAdded.toLocalDateTime().format(dateFormatter), notes
                ));
            }
        }
    }

    private void filterData() {
        String typeFilter = typeFilterCombo.getValue();
        String searchTerm = searchField.getText().toLowerCase().trim();

        if ((typeFilter == null || typeFilter.equals("الكل")) &&
                (searchTerm.isEmpty())) {
            dataTable.setItems(allData);
            return;
        }

        ObservableList<ScrapMaintenanceData> filtered = FXCollections.observableArrayList();

        for (ScrapMaintenanceData item : allData) {
            boolean matchesType = typeFilter.equals("الكل") || item.getType().equals(typeFilter);

            boolean matchesSearch = searchTerm.isEmpty() ||
                    item.getItemName().toLowerCase().contains(searchTerm) ||
                    item.getReceiver().toLowerCase().contains(searchTerm) ||
                    item.getEmployeeName().toLowerCase().contains(searchTerm) ||
                    item.getNotes().toLowerCase().contains(searchTerm) ||
                    String.valueOf(item.getQuantity()).contains(searchTerm);

            if (matchesType && matchesSearch) {
                filtered.add(item);
            }
        }

        dataTable.setItems(filtered);
    }

    @FXML
    private void onExportClicked() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("اختر مكان حفظ ملف Excel");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
            fileChooser.setInitialFileName("Scrap_Maintenance_Report.xlsx");
            java.io.File file = fileChooser.showSaveDialog(null);
            if (file == null) return;

            Workbook workbook = new XSSFWorkbook();

            // ✅ تصدير التوالف
            exportScrapData(workbook);

            // ✅ تصدير الصيانة
            exportMaintenanceData(workbook);

            try (FileOutputStream out = new FileOutputStream(file)) {
                workbook.write(out);
            }
            workbook.close();

            LogService.addLog("EXPORT_REPORT", "تم تصدير تقرير التوالف والصيانة إلى Excel");
            showInfo("✅ تم تصدير الملف بنجاح:\n" + file.getAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
            showError("حدث خطأ أثناء التصدير: " + e.getMessage());
        }
    }

    private void exportScrapData(Workbook workbook) throws SQLException {
        Sheet sheet = workbook.createSheet("التوالف");

        Row header = sheet.createRow(0);
        String[] columns = {"اسم الصنف", "الكمية", "المسؤول", "التاريخ", "ملاحظات"};
        createHeaderRow(header, columns, workbook);

        String query = """
        SELECT i.ItemName, s.Quantity, s.DateAdded, s.Notes
        FROM ScrapItems s
        INNER JOIN Items i ON s.ItemID = i.ItemID
        ORDER BY s.DateAdded DESC
    """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            int rowIdx = 1;
            while (rs.next()) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(rs.getString("ItemName"));
                row.createCell(1).setCellValue(rs.getDouble("Quantity"));
                row.createCell(2).setCellValue(CurrentUser.getName()); // ← هنا
                row.createCell(3).setCellValue(rs.getTimestamp("DateAdded").toString());
                row.createCell(4).setCellValue(rs.getString("Notes") != null ? rs.getString("Notes") : "");
            }
        }

        for (int i = 0; i < columns.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void exportMaintenanceData(Workbook workbook) throws SQLException {
        Sheet sheet = workbook.createSheet("الصيانة");

        Row header = sheet.createRow(0);
        String[] columns = {"اسم الصنف", "الكمية", "المستلم", "المسؤول", "التاريخ", "ملاحظات"};
        createHeaderRow(header, columns, workbook);

        String query = """
        SELECT i.ItemName, m.Quantity, m.ReceiverName, m.DateAdded, m.Notes
        FROM MaintenanceItems m
        INNER JOIN Items i ON m.ItemID = i.ItemID
        ORDER BY m.DateAdded DESC
    """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            int rowIdx = 1;
            while (rs.next()) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(rs.getString("ItemName"));
                row.createCell(1).setCellValue(rs.getDouble("Quantity"));
                row.createCell(2).setCellValue(rs.getString("ReceiverName"));
                row.createCell(3).setCellValue(CurrentUser.getName()); // ← هنا
                row.createCell(4).setCellValue(rs.getTimestamp("DateAdded").toString());
                row.createCell(5).setCellValue(rs.getString("Notes") != null ? rs.getString("Notes") : "");
            }
        }

        for (int i = 0; i < columns.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }


    private void createHeaderRow(Row header, String[] columns, Workbook workbook) {
        for (int i = 0; i < columns.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(columns[i]);
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
            cell.setCellStyle(style);
        }
    }

    @FXML
    private void onRefreshClicked() {
        loadData();
        LogService.addLog("REFRESH_DATA", "تم تحديث بيانات التوالف والصيانة");
        showInfo("✅ تم تحديث البيانات");
    }

    // ✅ كلاس للبيانات
    public static class ScrapMaintenanceData {
        private final String type;
        private final String itemName;
        private final double quantity;
        private final String receiver;
        private final String employeeName;
        private final String dateAdded;
        private final String notes;

        public ScrapMaintenanceData(String type, String itemName, double quantity,
                                    String receiver, String employeeName, String dateAdded, String notes) {
            this.type = type;
            this.itemName = itemName;
            this.quantity = quantity;
            this.receiver = receiver;
            this.employeeName = employeeName;
            this.dateAdded = dateAdded;
            this.notes = notes;
        }

        public String getType() { return type; }
        public String getItemName() { return itemName; }
        public double getQuantity() { return quantity; }
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