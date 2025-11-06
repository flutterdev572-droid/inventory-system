package app.controllers;

import app.current_user.CurrentUser;
import app.db.DatabaseConnection;
import app.models.Item;
import app.services.LogService;
import app.utils.RawThermalPrinter;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.sql.*;

public class StockOutController {

    @FXML private ComboBox<Item> itemComboBox;
    @FXML private TextField quantityField;
    @FXML private ComboBox<String> receiverComboBox;
    @FXML private TextArea notesField;
    @FXML private Button saveButton;
    @FXML private TableView<Item> itemsTable;
    @FXML private TableColumn<Item, String> nameColumn;
    @FXML private TableColumn<Item, Number> qtyColumn;

    private ObservableList<Item> itemList = FXCollections.observableArrayList();
    private ObservableList<String> employeeList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        loadItems();
        loadEmployees();

        // إعداد أعمدة الجدول
        nameColumn.setCellValueFactory(cellData -> cellData.getValue().itemNameProperty());
        qtyColumn.setCellValueFactory(cellData -> cellData.getValue().quantityProperty());
        itemsTable.setItems(itemList);

        // ✅ تحسين الـ ComboBox للموظفين - البحث بالاسم أو الـ ID
        setupEmployeeComboBox();
    }

    // ✅ إعداد ComboBox الموظفين مع البحث المتقدم
    private void setupEmployeeComboBox() {
        receiverComboBox.setEditable(true);

        // Auto-completion functionality
        receiverComboBox.getEditor().textProperty().addListener((obs, oldText, newText) -> {
            if (newText == null || newText.isEmpty()) {
                receiverComboBox.setItems(employeeList);
            } else {
                filterEmployees(newText);
            }
        });

        // ✅ التأكد من اختيار عنصر من القائمة
        receiverComboBox.setOnAction(event -> {
            String selected = receiverComboBox.getSelectionModel().getSelectedItem();
            if (selected != null) {
                receiverComboBox.getEditor().setText(selected);
            }
        });
    }

    // ✅ تحميل الأصناف من قاعدة البيانات الأساسية
    private void loadItems() {
        itemList.clear();
        String sql = "SELECT i.ItemID, i.ItemName, u.UnitName, s.Quantity, i.MinQuantity " +
                "FROM Items i " +
                "JOIN Units u ON i.UnitID = u.UnitID " +
                "JOIN StockBalances s ON i.ItemID = s.ItemID";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                itemList.add(new Item(
                        rs.getInt("ItemID"),
                        rs.getString("ItemName"),
                        rs.getString("UnitName"),
                        rs.getDouble("Quantity"),
                        rs.getDouble("MinQuantity")
                ));
            }

            itemComboBox.setItems(itemList);
        } catch (SQLException e) {
            showAlert("خطأ", "فشل في تحميل الأصناف: " + e.getMessage());
        }
    }

    // ✅ تحميل الموظفين من قاعدة البيانات الأخرى (Chemtech_management)
    private void loadEmployees() {
        employeeList.clear();
        String url = "jdbc:sqlserver://localhost;databaseName=Chemtech_management;encrypt=false;";
        String user = "Chem_Tech";
        String password = "AD@chem@25!!";

        String sql = "SELECT employee_id, name FROM Employees ORDER BY name";
        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                int id = rs.getInt("employee_id");
                String name = rs.getString("name");
                employeeList.add(id + " - " + name);
            }

            receiverComboBox.setItems(employeeList);
        } catch (SQLException e) {
            showAlert("خطأ", "فشل في تحميل الموظفين: " + e.getMessage());
            // ✅ إضافة خيارات افتراضية في حالة الخطأ
            employeeList.addAll("1 - مدير النظام", "2 - أمين المخزن");
            receiverComboBox.setItems(employeeList);
        }
    }

    // ✅ البحث داخل ComboBox الموظفين
    private void filterEmployees(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            receiverComboBox.setItems(employeeList);
            return;
        }

        ObservableList<String> filtered = FXCollections.observableArrayList();
        for (String emp : employeeList) {
            if (emp.toLowerCase().contains(keyword.toLowerCase())) {
                filtered.add(emp);
            }
        }
        receiverComboBox.setItems(filtered);
        receiverComboBox.show();
    }

    // ✅ حفظ عملية الصرف
    @FXML
    private void handleSave() {
        Item selectedItem = itemComboBox.getSelectionModel().getSelectedItem();
        String qtyText = quantityField.getText();
        String receiver = receiverComboBox.getValue();
        String notes = notesField.getText();

        // ✅ التحقق من البيانات
        if (selectedItem == null || qtyText.isEmpty() || receiver == null || receiver.isEmpty()) {
            showAlert("خطأ", "الرجاء ملء جميع الحقول المطلوبة (الصنف، الكمية، المستلم).");
            return;
        }

        double qty;
        try {
            qty = Double.parseDouble(qtyText);
        } catch (NumberFormatException e) {
            showAlert("خطأ", "الكمية يجب أن تكون رقمًا صحيحًا.");
            return;
        }

        if (qty <= 0) {
            showAlert("خطأ", "الكمية يجب أن تكون أكبر من صفر.");
            return;
        }

        // ✅ التحقق من المخزون المتاح
        if (qty > selectedItem.getQuantity()) {
            showAlert("خطأ", "لا توجد كمية كافية في المخزون.");
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            // ✅ فقط تسجيل المعاملة - التريجر سيتولى الباقي
            String insertSql = "INSERT INTO StockTransactions (ItemID, TransactionType, Quantity, ReceiverName, EmployeeID, Notes) VALUES (?, 'OUT', ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setInt(1, selectedItem.getItemID());
                ps.setDouble(2, qty);
                ps.setString(3, receiver);
                ps.setInt(4, CurrentUser.getId());
                ps.setString(5, notes);
                ps.executeUpdate();
            }

            // ✅ تسجيل العملية في اللوج
            String description = String.format("صرف كمية: تم صرف %.2f وحدة من الصنف '%s' - المستلم: %s - الملاحظات: %s",
                    qty, selectedItem.getItemName(), receiver, notes.isEmpty() ? "لا توجد" : notes);
            LogService.addLog("STOCK_OUT", description);

            showAlert("تم بنجاح", "تم تسجيل عملية الصرف بنجاح ✅");
            // ✅ طباعة ريسيت بعد الصرف




            // تحديث البيانات
            loadItems();
            clearForm();

        } catch (SQLException e) {
            showAlert("خطأ", "حدث خطأ أثناء حفظ العملية: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ✅ دالة لاستخراج الـ Employee ID من النص
    private int extractEmployeeId(String receiverText) {
        try {
            // النص بيكون بالشكل "123 - اسم الموظف"
            String[] parts = receiverText.split(" - ");
            if (parts.length > 0) {
                return Integer.parseInt(parts[0].trim());
            }
        } catch (Exception e) {
            e.printStackTrace();
            // ✅ في حالة الخطأ، نستخدم المستخدم الحالي بدلاً من 1
            return CurrentUser.getId();
        }
        // ✅ في حالة الخطأ، نستخدم المستخدم الحالي بدلاً من 1
        return CurrentUser.getId();
    }

    // ✅ دالة لاستخراج الـ Employee ID من النص

    // ✅ مسح النموذج
    private void clearForm() {
        quantityField.clear();
        receiverComboBox.getSelectionModel().clearSelection();
        receiverComboBox.getEditor().clear();
        notesField.clear();
    }

    // ✅ تنبيه بسيط
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}