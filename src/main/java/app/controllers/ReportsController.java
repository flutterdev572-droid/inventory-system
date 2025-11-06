package app.controllers;

import app.db.DatabaseConnection;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.io.FileOutputStream;
import java.sql.*;
import java.time.LocalDate;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class ReportsController {

    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;
    @FXML private ComboBox<String> typeFilter;
    @FXML private Label statusLabel;

    @FXML private TableView<Transaction> transactionsTable;
    @FXML private TableColumn<Transaction, Integer> colTransID;
    @FXML private TableColumn<Transaction, String> colItemName;
    @FXML private TableColumn<Transaction, String> colType;
    @FXML private TableColumn<Transaction, Double> colQty;
    @FXML private TableColumn<Transaction, String> colDate;
    @FXML private TableColumn<Transaction, String> colEmp;
    @FXML private TableColumn<Transaction, String> colReceiver;
    @FXML private TableColumn<Transaction, String> colNotes;

    @FXML private TableView<Shortage> shortagesTable;
    @FXML private TableColumn<Shortage, String> colShortItem;
    @FXML private TableColumn<Shortage, Double> colShortCurrent;
    @FXML private TableColumn<Shortage, Double> colShortMin;
    @FXML private TableColumn<Shortage, String> colShortDate;

    @FXML private TableView<LogEntry> logsTable;
    @FXML private TableColumn<LogEntry, String> colLogAction;
    @FXML private TableColumn<LogEntry, String> colLogDesc;
    @FXML private TableColumn<LogEntry, String> colLogEmp;
    @FXML private TableColumn<LogEntry, String> colLogDate;
    @FXML private TextField searchField;
    @FXML private Label searchResultLabel;
    @FXML private ComboBox<String> itemSearchBox;
    @FXML private Label totalInLabel;
    @FXML private Label totalOutLabel;
    @FXML private Label netLabel;

    private ObservableList<String> itemNames = FXCollections.observableArrayList();


    private ObservableList<Transaction> transactionList = FXCollections.observableArrayList();
    private ObservableList<Shortage> shortageList = FXCollections.observableArrayList();
    private ObservableList<LogEntry> logList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        typeFilter.getItems().addAll("الكل", "IN", "OUT");
        typeFilter.setValue("الكل");

        setupColumns();
        loadData();
        loadItemNames(); // تحميل أسماء الأصناف في البحث
        setupAutoComplete();

        setupTableColumns();
    }
    private void loadItemNames() {
        itemNames.clear();
        String query = "SELECT DISTINCT ItemName FROM Items ORDER BY ItemName ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                itemNames.add(rs.getString("ItemName"));
            }
            itemSearchBox.setItems(itemNames);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    private void setupAutoComplete() {
        itemSearchBox.setEditable(true);

        itemSearchBox.getEditor().textProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue == null || newValue.isEmpty()) {
                itemSearchBox.hide();
                return;
            }

            ObservableList<String> filteredList = FXCollections.observableArrayList();
            for (String name : itemNames) {
                if (name.toLowerCase().contains(newValue.toLowerCase())) {
                    filteredList.add(name);
                }
            }
            itemSearchBox.setItems(filteredList);
            itemSearchBox.show();
        });
    }

    private void setupColumns() {
        colTransID.setCellValueFactory(new PropertyValueFactory<>("transactionID"));
        colItemName.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colEmp.setCellValueFactory(new PropertyValueFactory<>("employee"));
        colReceiver.setCellValueFactory(new PropertyValueFactory<>("receiver"));
        colNotes.setCellValueFactory(new PropertyValueFactory<>("notes"));

        colShortItem.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        colShortCurrent.setCellValueFactory(new PropertyValueFactory<>("currentQty"));
        colShortMin.setCellValueFactory(new PropertyValueFactory<>("minQty"));
        colShortDate.setCellValueFactory(new PropertyValueFactory<>("detectedAt"));

        colLogAction.setCellValueFactory(new PropertyValueFactory<>("action"));
        colLogDesc.setCellValueFactory(new PropertyValueFactory<>("description"));
        colLogEmp.setCellValueFactory(new PropertyValueFactory<>("employee"));
        colLogDate.setCellValueFactory(new PropertyValueFactory<>("logDate"));
    }

    private void setupTableColumns() {
        colNotes.setCellFactory(tc -> {
            TableCell<Transaction, String> cell = new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setTooltip(null);
                    } else {
                        setText(item);
                        setTooltip(new Tooltip(item));
                    }
                }
            };
            return cell;
        });

        colLogDesc.setCellFactory(tc -> {
            TableCell<LogEntry, String> cell = new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setTooltip(null);
                    } else {
                        setText(item);
                        setTooltip(new Tooltip(item));
                    }
                }
            };
            return cell;
        });

        colNotes.setResizable(true);
        colLogDesc.setResizable(true);
        colReceiver.setResizable(true);
        colItemName.setResizable(true);
    }

    @FXML
    private void onRefreshClicked() {
        loadData();
    }
    @FXML
    private void onSearchClicked() {
        String keyword = searchField.getText().trim();

        if (keyword.isEmpty()) {
            transactionsTable.setItems(transactionList);
            searchResultLabel.setText("");
            return;
        }

        // 🟢 فلترة القائمة
        ObservableList<Transaction> filtered = FXCollections.observableArrayList();
        double totalIn = 0, totalOut = 0;

        for (Transaction t : transactionList) {
            if (t.getItemName().toLowerCase().contains(keyword.toLowerCase())) {
                filtered.add(t);
                if (t.getType().equalsIgnoreCase("IN")) {
                    totalIn += t.getQuantity();
                } else if (t.getType().equalsIgnoreCase("OUT")) {
                    totalOut += t.getQuantity();
                }
            }
        }

        transactionsTable.setItems(filtered);

        // 🟢 عرض النتيجة
        if (filtered.isEmpty()) {
            searchResultLabel.setText("❌ لا توجد نتائج للصنف: " + keyword);
        } else {
            searchResultLabel.setText("✅ " + keyword + " | الداخل: " + totalIn + " | الخارج: " + totalOut);
        }
    }


    private void loadData() {
        transactionList.clear();
        shortageList.clear();
        logList.clear();

        // ✅ 1. تحميل المعاملات مع بيانات الموظف والمستلم
        loadTransactions();

        // ✅ 2. تحميل النواقص مع تحديث تلقائي
        loadShortages();

        // ✅ 3. تحميل اللوجز
        loadLogs();

        statusLabel.setText("✅ تم تحميل البيانات بنجاح");
        autoResizeColumns();
    }

    // ✅ 1. تحميل المعاملات مع إصلاح بيانات الموظف والمستلم
    private void loadTransactions() {
        transactionList.clear();

        String query = """
        SELECT 
            t.TransactionID, 
            i.ItemName, 
            t.TransactionType, 
            t.Quantity,
            t.TransactionDate, 
            CASE 
                WHEN t.EmployeeID IS NOT NULL AND t.EmployeeID > 0 THEN 
                    COALESCE(e.name, 'موظف #' + CAST(t.EmployeeID AS VARCHAR))
                ELSE 'نظام'
            END AS EmployeeName,
            ISNULL(t.ReceiverName, '-') AS Receiver, 
            ISNULL(t.Notes, '') AS Notes
        FROM StockTransactions t
        JOIN Items i ON t.ItemID = i.ItemID
        LEFT JOIN Chemtech_management.dbo.Employees e ON t.EmployeeID = e.employee_id
        WHERE 1=1
    """;

        LocalDate from = fromDatePicker.getValue();
        LocalDate to = toDatePicker.getValue();
        String type = typeFilter.getValue();
        String selectedItem = itemSearchBox.getValue();

        if (from != null) query += " AND t.TransactionDate >= '" + from + " 00:00:00'";
        if (to != null) query += " AND t.TransactionDate <= '" + to + " 23:59:59'";
        if (!type.equals("الكل")) query += " AND t.TransactionType='" + type + "'";
        if (selectedItem != null && !selectedItem.trim().isEmpty())
            query += " AND i.ItemName LIKE '%" + selectedItem + "%'";

        query += " ORDER BY t.TransactionDate DESC";

        double totalIn = 0, totalOut = 0;

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                String typeValue = rs.getString("TransactionType");
                double qty = rs.getDouble("Quantity");

                if ("IN".equalsIgnoreCase(typeValue)) totalIn += qty;
                else if ("OUT".equalsIgnoreCase(typeValue)) totalOut += qty;

                transactionList.add(new Transaction(
                        rs.getInt("TransactionID"),
                        rs.getString("ItemName"),
                        typeValue,
                        qty,
                        rs.getString("TransactionDate"),
                        rs.getString("EmployeeName"),
                        rs.getString("Receiver"),
                        rs.getString("Notes")
                ));
            }

            transactionsTable.setItems(transactionList);

            // ✅ عرض الإجماليات
            totalInLabel.setText("إجمالي الداخل: " + totalIn);
            totalOutLabel.setText("إجمالي الخارج: " + totalOut);
            netLabel.setText("الصافي: " + (totalIn - totalOut));

        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("❌ خطأ في تحميل المعاملات");
        }
    }

    // ✅ 2. تحميل النواقص مع حذف التلقائي للنواقص المعالجة
    private void loadShortages() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            // ✅ أولاً: تحديث النواقص - حذف الأصناف التي لم تعد ناقصة
            String cleanupQuery = """
                DELETE FROM ShortageItems 
                WHERE ItemID IN (
                    SELECT s.ItemID 
                    FROM ShortageItems s
                    JOIN StockBalances sb ON s.ItemID = sb.ItemID
                    WHERE sb.Quantity >= s.MinQuantity
                )
            """;

            try (PreparedStatement cleanupStmt = conn.prepareStatement(cleanupQuery)) {
                int deletedCount = cleanupStmt.executeUpdate();
                if (deletedCount > 0) {
                    System.out.println("✅ تم حذف " + deletedCount + " صنف لم يعد ناقصاً");
                }
            }

            // ✅ ثانياً: إضافة النواقص الجديدة
            String detectNewQuery = """
                INSERT INTO ShortageItems (ItemID, CurrentQuantity, MinQuantity, DetectedAt)
                SELECT 
                    i.ItemID, 
                    sb.Quantity, 
                    i.MinQuantity,
                    GETDATE()
                FROM Items i
                JOIN StockBalances sb ON i.ItemID = sb.ItemID
                WHERE sb.Quantity < i.MinQuantity
                AND i.ItemID NOT IN (SELECT ItemID FROM ShortageItems)
            """;

            try (PreparedStatement detectStmt = conn.prepareStatement(detectNewQuery)) {
                int addedCount = detectStmt.executeUpdate();
                if (addedCount > 0) {
                    System.out.println("✅ تم اكتشاف " + addedCount + " صنف ناقص جديد");
                }
            }

            conn.commit();

            // ✅ ثالثاً: تحميل النواقص الحالية
            String selectQuery = """
                SELECT i.ItemName, s.CurrentQuantity, s.MinQuantity, s.DetectedAt
                FROM ShortageItems s
                JOIN Items i ON s.ItemID = i.ItemID
                ORDER BY s.DetectedAt DESC
            """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(selectQuery)) {

                while (rs.next()) {
                    shortageList.add(new Shortage(
                            rs.getString("ItemName"),
                            rs.getDouble("CurrentQuantity"),
                            rs.getDouble("MinQuantity"),
                            rs.getString("DetectedAt")
                    ));
                }
                shortagesTable.setItems(shortageList);
            }

        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("❌ خطأ في تحميل النواقص");
        }
    }


    // ✅ 3. تحميل اللوجز مع إصلاح النصوص
    private void loadLogs() {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("""
        SELECT 
            l.ActionType, 
            CASE 
                WHEN l.Description LIKE '%?%' OR l.Description LIKE '%??%' THEN 
                    REPLACE(REPLACE(REPLACE(l.Description, '??', 'تم'), '?', ' '), '  ', ' ')
                ELSE l.Description 
            END AS Description,
            CASE 
                WHEN l.EmployeeID IS NOT NULL AND l.EmployeeID > 0 THEN 
                    COALESCE(e.name, 'موظف #' + CAST(l.EmployeeID AS VARCHAR))
                ELSE 'نظام'
            END AS EmployeeName,
            l.LogDate
        FROM Logs l
        LEFT JOIN Chemtech_management.dbo.Employees e ON l.EmployeeID = e.employee_id
        ORDER BY l.LogDate DESC
     """)) {

            while (rs.next()) {
                logList.add(new LogEntry(
                        rs.getString("ActionType"),
                        rs.getString("Description"),
                        rs.getString("EmployeeName"),
                        rs.getString("LogDate")
                ));
            }
            logsTable.setItems(logList);
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("❌ خطأ في تحميل السجلات");
        }
    }
    // ✅ دالة لضبط حجم الأعمدة تلقائياً
    private void autoResizeColumns() {
        transactionsTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        shortagesTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        logsTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        Platform.runLater(() -> {
            transactionsTable.refresh();
            shortagesTable.refresh();
            logsTable.refresh();
        });
    }

    @FXML
    private void onExportClicked() {
        try (Workbook workbook = new XSSFWorkbook()) {

            // ✅ تصدير المعاملات
            Sheet transactionsSheet = workbook.createSheet("المعاملات");
            createTransactionsSheet(transactionsSheet);

            // ✅ تصدير النواقص
            Sheet shortagesSheet = workbook.createSheet("الأصناف الناقصة");
            createShortagesSheet(shortagesSheet);

            // ✅ تصدير السجلات
            Sheet logsSheet = workbook.createSheet("سجلات النظام");
            createLogsSheet(logsSheet);

            // ✅ فتح FileChooser
            javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
            fileChooser.setTitle("اختر مكان حفظ التقرير");
            fileChooser.getExtensionFilters().add(
                    new javafx.stage.FileChooser.ExtensionFilter("Excel Files (*.xlsx)", "*.xlsx")
            );
            fileChooser.setInitialFileName("Reports_Export.xlsx");

            java.io.File file = fileChooser.showSaveDialog(statusLabel.getScene().getWindow());
            if (file == null) {
                statusLabel.setText("❌ تم إلغاء عملية الحفظ");
                return;
            }

            try (FileOutputStream fileOut = new FileOutputStream(file)) {
                workbook.write(fileOut);
            }

            statusLabel.setText("📁 تم تصدير التقرير بنجاح إلى: " + file.getAbsolutePath());
        } catch (Exception e) {
            statusLabel.setText("❌ فشل في التصدير");
            e.printStackTrace();
        }
    }

    // ✅ إنشاء شيت المعاملات
    private void createTransactionsSheet(Sheet sheet) {
        Row header = sheet.createRow(0);
        String[] columns = {"الكود", "الصنف", "النوع", "الكمية", "التاريخ", "الموظف", "المستلم", "ملاحظات"};
        for (int i = 0; i < columns.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(columns[i]);
            CellStyle style = sheet.getWorkbook().createCellStyle();
            Font font = sheet.getWorkbook().createFont();
            font.setBold(true);
            style.setFont(font);
            cell.setCellStyle(style);
        }

        int rowNum = 1;
        for (Transaction t : transactionList) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(t.getTransactionID());
            row.createCell(1).setCellValue(t.getItemName());
            row.createCell(2).setCellValue(t.getType());
            row.createCell(3).setCellValue(t.getQuantity());
            row.createCell(4).setCellValue(t.getDate());
            row.createCell(5).setCellValue(t.getEmployee());
            row.createCell(6).setCellValue(t.getReceiver());
            row.createCell(7).setCellValue(t.getNotes());
        }

        for (int i = 0; i < columns.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    // ✅ إنشاء شيت النواقص
    private void createShortagesSheet(Sheet sheet) {
        Row header = sheet.createRow(0);
        String[] columns = {"الصنف", "الكمية الحالية", "الحد الأدنى", "تاريخ الكشف"};
        for (int i = 0; i < columns.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(columns[i]);
            CellStyle style = sheet.getWorkbook().createCellStyle();
            Font font = sheet.getWorkbook().createFont();
            font.setBold(true);
            style.setFont(font);
            cell.setCellStyle(style);
        }

        int rowNum = 1;
        for (Shortage s : shortageList) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(s.getItemName());
            row.createCell(1).setCellValue(s.getCurrentQty());
            row.createCell(2).setCellValue(s.getMinQty());
            row.createCell(3).setCellValue(s.getDetectedAt());
        }

        for (int i = 0; i < columns.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    // ✅ إنشاء شيت السجلات
    private void createLogsSheet(Sheet sheet) {
        Row header = sheet.createRow(0);
        String[] columns = {"الحدث", "الوصف", "الموظف", "التاريخ"};
        for (int i = 0; i < columns.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(columns[i]);
            CellStyle style = sheet.getWorkbook().createCellStyle();
            Font font = sheet.getWorkbook().createFont();
            font.setBold(true);
            style.setFont(font);
            cell.setCellStyle(style);
        }

        int rowNum = 1;
        for (LogEntry l : logList) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(l.getAction());
            row.createCell(1).setCellValue(l.getDescription());
            row.createCell(2).setCellValue(l.getEmployee());
            row.createCell(3).setCellValue(l.getLogDate());
        }

        for (int i = 0; i < columns.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    // --- Models ---
    public static class Transaction {
        private final int transactionID;
        private final String itemName, type, date, employee, receiver, notes;
        private final double quantity;

        public Transaction(int id, String itemName, String type, double qty, String date,
                           String emp, String receiver, String notes) {
            this.transactionID = id;
            this.itemName = itemName;
            this.type = type;
            this.quantity = qty;
            this.date = date;
            this.employee = emp;
            this.receiver = receiver;
            this.notes = notes;
        }

        public int getTransactionID() { return transactionID; }
        public String getItemName() { return itemName; }
        public String getType() { return type; }
        public double getQuantity() { return quantity; }
        public String getDate() { return date; }
        public String getEmployee() { return employee; }
        public String getReceiver() { return receiver; }
        public String getNotes() { return notes; }
    }

    public static class Shortage {
        private final String itemName, detectedAt;
        private final double currentQty, minQty;

        public Shortage(String name, double current, double min, String detectedAt) {
            this.itemName = name;
            this.currentQty = current;
            this.minQty = min;
            this.detectedAt = detectedAt;
        }

        public String getItemName() { return itemName; }
        public double getCurrentQty() { return currentQty; }
        public double getMinQty() { return minQty; }
        public String getDetectedAt() { return detectedAt; }
    }

    public static class LogEntry {
        private final String action, description, employee, logDate;

        public LogEntry(String action, String desc, String emp, String date) {
            this.action = action;
            this.description = desc;
            this.employee = emp;
            this.logDate = date;
        }

        public String getAction() { return action; }
        public String getDescription() { return description; }
        public String getEmployee() { return employee; }
        public String getLogDate() { return logDate; }
    }
}