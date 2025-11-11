package app.controllers;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import app.db.DatabaseConnection;

public class AddDeviceComponentController {

    @FXML private ComboBox<String> itemComboBox;
    @FXML private TextField quantityField;
    private ObservableList<String> originalItems = FXCollections.observableArrayList();
    private Map<String, String> itemCodeMap = new HashMap<>(); // خريطة لتخزين الأسماء والأكواد

    private int deviceId; // Will be set from the device screen

    public void setDeviceId(int id) {
        this.deviceId = id;
        loadItems();
    }

    private void loadItems() {
        originalItems.clear();
        itemCodeMap.clear();
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT ItemName, ItemCode FROM Items ORDER BY ItemName";
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String itemName = rs.getString("ItemName");
                String itemCode = rs.getString("ItemCode");
                originalItems.add(itemName);
                itemCodeMap.put(itemName, itemCode != null ? itemCode : "بدون كود");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        itemComboBox.setItems(FXCollections.observableArrayList(originalItems));
        makeComboBoxSearchable(itemComboBox);
    }

    private void makeComboBoxSearchable(ComboBox<String> comboBox) {
        comboBox.setEditable(true);
        TextField editor = comboBox.getEditor();

        // قائمة منفصلة للعرض المؤقت أثناء الكتابة
        ObservableList<String> filteredList = FXCollections.observableArrayList(originalItems);
        comboBox.setItems(filteredList);

        // مستمع التغيير في النص
        editor.textProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue == null) return;

            // فلترة بالاسم والكود
            filteredList.setAll(originalItems.filtered(
                    item -> {
                        String lowerNewValue = newValue.toLowerCase();
                        boolean nameMatch = item.toLowerCase().contains(lowerNewValue);
                        boolean codeMatch = itemCodeMap.get(item) != null &&
                                itemCodeMap.get(item).toLowerCase().contains(lowerNewValue);
                        return nameMatch || codeMatch;
                    }
            ));

            // لو النص فاضي رجّع القائمة الأصلية
            if (newValue.isEmpty()) {
                filteredList.setAll(originalItems);
            }

            // عرض القائمة فقط لو المستخدم بدأ يكتب
            if (!comboBox.isShowing() && !filteredList.isEmpty()) {
                comboBox.show();
            }
        });

        // إعادة القائمة الأصلية بعد الاختيار
        comboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !originalItems.contains(newVal)) {
                comboBox.setValue(null);
            } else {
                filteredList.setAll(originalItems);
            }
        });

        // إضافة placeholder للبحث
        comboBox.setPromptText("ابحث بالاسم أو الكود...");
    }

    @FXML
    private void saveComponent() {
        String itemName = itemComboBox.getValue();
        String qtyText = quantityField.getText();

        if (itemName == null || qtyText.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "من فضلك اختر الصنف وأدخل الكمية").show();
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            // الحصول على ItemID أولاً
            String getItemIdSql = "SELECT ItemID FROM Items WHERE ItemName = ?";
            PreparedStatement getItemStmt = conn.prepareStatement(getItemIdSql);
            getItemStmt.setString(1, itemName);
            ResultSet rs = getItemStmt.executeQuery();

            if (!rs.next()) {
                new Alert(Alert.AlertType.ERROR, "❌ الصنف غير موجود في قاعدة البيانات").show();
                return;
            }

            int itemId = rs.getInt("ItemID");

            // إدخال في جدول DeviceComponents
            String sql = "INSERT INTO DeviceComponents (DeviceID, ItemID, Quantity) VALUES (?, ?, ?)";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, deviceId);
            ps.setInt(2, itemId);
            ps.setDouble(3, Double.parseDouble(qtyText));
            ps.executeUpdate();

            new Alert(Alert.AlertType.INFORMATION, "✔ تمت إضافة المكون بنجاح").show();
            closePopup();
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "❌ خطأ في إضافة المكون: " + e.getMessage()).show();
        }
    }

    @FXML
    private void closePopup() {
        Stage stage = (Stage) itemComboBox.getScene().getWindow();
        stage.close();
    }

    // دالة للحصول على كود العنصر المختار
    public String getSelectedItemCode() {
        String selectedItem = itemComboBox.getValue();
        return selectedItem != null ? itemCodeMap.get(selectedItem) : null;
    }

    // دالة للحصول على اسم العنصر المختار
    public String getSelectedItemName() {
        return itemComboBox.getValue();
    }

    // دالة للحصول على الكمية المدخلة
    public double getQuantity() {
        try {
            return Double.parseDouble(quantityField.getText());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}