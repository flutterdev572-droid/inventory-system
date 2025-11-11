package app.controllers;

import app.db.DatabaseConnection;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class AddDeviceController {

    @FXML private TextField deviceNameField;
    @FXML private ComboBox<String> itemComboBox;
    @FXML private TextField quantityField;
    @FXML private TableView<ComponentEntry> componentTable;
    @FXML private TableColumn<ComponentEntry, String> colItemName;
    @FXML private TableColumn<ComponentEntry, String> colItemCode; // عمود جديد
    @FXML private TableColumn<ComponentEntry, Double> colQuantity;
    @FXML private Button addItemButton;
    @FXML private Button saveDeviceButton;

    private ObservableList<String> allItems = FXCollections.observableArrayList();
    private Map<String, String> itemCodeMap = new HashMap<>(); // خريطة لتخزين الأسماء والأكواد
    private ObservableList<ComponentEntry> components = FXCollections.observableArrayList();
    private boolean filtering = false; // علامة لمنع التكرار

    private Integer editingDeviceId = null; // null = جهاز جديد

    @FXML
    public void initialize() {
        setupTable();
        loadItems();

        // دعم البحث داخل ComboBox
        setupComboBoxSearch();

        addItemButton.setOnAction(e -> addComponent());
        saveDeviceButton.setOnAction(e -> saveDevice());
    }

    private void setupComboBoxSearch() {
        itemComboBox.setEditable(true);

        // إضافة Placeholder للبحث
        itemComboBox.setPromptText("ابحث بالاسم أو الكود...");

        // إعداد البحث في ComboBox
        itemComboBox.getEditor().textProperty().addListener((obs, old, newVal) -> {
            if (!filtering) {
                filterItems(newVal);
            }
        });

        // إظهار القائمة عند النقر على ComboBox
        itemComboBox.setOnMouseClicked(e -> {
            if (!itemComboBox.isShowing()) {
                filterItems(itemComboBox.getEditor().getText());
                itemComboBox.show();
            }
        });

        // إعادة تعيين التصفية عند فقدان التركيز إذا كان النص فارغاً
        itemComboBox.getEditor().focusedProperty().addListener((obs, old, newVal) -> {
            if (!newVal && (itemComboBox.getEditor().getText() == null || itemComboBox.getEditor().getText().isEmpty())) {
                filterItems("");
            }
        });
    }

    private void setupTable() {
        colItemName.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        colItemCode.setCellValueFactory(new PropertyValueFactory<>("itemCode")); // عمود الكود
        colQuantity.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        componentTable.setItems(components);

        // زر حذف داخل الجدول
        TableColumn<ComponentEntry, Void> deleteCol = new TableColumn<>("حذف");
        deleteCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("🗑");

            {
                btn.setStyle("-fx-background-color:#ef4444;-fx-text-fill:white;-fx-font-size:13;-fx-background-radius:5;");
                btn.setOnAction(e -> {
                    ComponentEntry entry = getTableView().getItems().get(getIndex());
                    components.remove(entry);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setGraphic(null);
                else setGraphic(btn);
            }
        });
        componentTable.getColumns().add(deleteCol);
    }

    private void loadItems() {
        allItems.clear();
        itemCodeMap.clear();
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT ItemName, ItemCode FROM Items ORDER BY ItemName");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String itemName = rs.getString("ItemName");
                String itemCode = rs.getString("ItemCode");
                allItems.add(itemName);
                itemCodeMap.put(itemName, itemCode != null ? itemCode : "بدون كود");
            }
            itemComboBox.setItems(FXCollections.observableArrayList(allItems));
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "خطأ في تحميل الأصناف", e.getMessage());
        }
    }

    private void filterItems(String query) {
        filtering = true;
        try {
            if (query == null || query.isEmpty()) {
                itemComboBox.setItems(FXCollections.observableArrayList(allItems));
            } else {
                String lower = query.toLowerCase();
                List<String> filtered = allItems.stream()
                        .filter(i ->
                                i.toLowerCase().contains(lower) ||
                                        (itemCodeMap.get(i) != null && itemCodeMap.get(i).toLowerCase().contains(lower))
                        )
                        .collect(Collectors.toList());
                itemComboBox.setItems(FXCollections.observableArrayList(filtered));
            }
            itemComboBox.show();
        } finally {
            filtering = false;
        }
    }

    private void addComponent() {
        String itemName = itemComboBox.getValue();
        String qtyText = quantityField.getText();

        if (itemName == null || itemName.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "تنبيه", "اختر صنف أولاً!");
            return;
        }

        double qty;
        try {
            qty = Double.parseDouble(qtyText);
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "خطأ", "أدخل كمية صحيحة!");
            return;
        }

        // الحصول على كود العنصر
        String itemCode = itemCodeMap.get(itemName);

        // تحديث لو المكون مضاف مسبقًا
        for (ComponentEntry entry : components) {
            if (entry.getItemName().equals(itemName)) {
                entry.setQuantity(entry.getQuantity() + qty);
                componentTable.refresh();
                clearComponentFields();
                return;
            }
        }

        components.add(new ComponentEntry(itemName, itemCode, qty));
        componentTable.refresh();
        clearComponentFields();
    }

    private void clearComponentFields() {
        quantityField.clear();
        itemComboBox.getSelectionModel().clearSelection();
        itemComboBox.getEditor().clear();
    }

    private void saveDevice() {
        String name = deviceNameField.getText().trim();
        if (name.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "تنبيه", "أدخل اسم الجهاز!");
            return;
        }

        if (components.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "تنبيه", "أضف مكونات قبل الحفظ!");
            return;
        }

        Connection conn = null;
        try {
            conn = DatabaseConnection.getInventoryConnection();
            conn.setAutoCommit(false);

            // التحقق من عدم تكرار اسم الجهاز
            if (editingDeviceId == null) {
                PreparedStatement checkStmt = conn.prepareStatement(
                        "SELECT DeviceID FROM Devices WHERE DeviceName = ?"
                );
                checkStmt.setString(1, name);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    showAlert(Alert.AlertType.ERROR, "خطأ", "اسم الجهاز موجود مسبقاً!");
                    return;
                }
            } else {
                PreparedStatement checkStmt = conn.prepareStatement(
                        "SELECT DeviceID FROM Devices WHERE DeviceName = ? AND DeviceID != ?"
                );
                checkStmt.setString(1, name);
                checkStmt.setInt(2, editingDeviceId);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    showAlert(Alert.AlertType.ERROR, "خطأ", "اسم الجهاز موجود مسبقاً!");
                    return;
                }
            }

            int deviceId;
            if (editingDeviceId == null) {
                // إنشاء رقم تسلسلي فريد بدلاً من NULL
                String uniqueSerial = "DEV-" + System.currentTimeMillis() + "-" +
                        ThreadLocalRandom.current().nextInt(1000, 9999);

                PreparedStatement insertDevice = conn.prepareStatement(
                        "INSERT INTO Devices (DeviceName, SerialNumber) VALUES (?, ?)",
                        Statement.RETURN_GENERATED_KEYS
                );
                insertDevice.setString(1, name);
                insertDevice.setString(2, uniqueSerial);

                int affectedRows = insertDevice.executeUpdate();

                if (affectedRows == 0) {
                    throw new SQLException("فشل إنشاء الجهاز، لم تتأثر أي صفوف.");
                }

                ResultSet rs = insertDevice.getGeneratedKeys();
                if (rs.next()) {
                    deviceId = rs.getInt(1);
                } else {
                    throw new SQLException("فشل إنشاء الجهاز، لم يتم الحصول على ID.");
                }
            } else {
                deviceId = editingDeviceId;
                PreparedStatement updateDevice = conn.prepareStatement(
                        "UPDATE Devices SET DeviceName = ? WHERE DeviceID = ?"
                );
                updateDevice.setString(1, name);
                updateDevice.setInt(2, deviceId);
                updateDevice.executeUpdate();

                // حذف المكونات القديمة
                PreparedStatement delComps = conn.prepareStatement("DELETE FROM DeviceComponents WHERE DeviceID = ?");
                delComps.setInt(1, deviceId);
                delComps.executeUpdate();
            }

            // إضافة المكونات الجديدة
            for (ComponentEntry entry : components) {
                PreparedStatement getItem = conn.prepareStatement("SELECT ItemID FROM Items WHERE ItemName = ?");
                getItem.setString(1, entry.getItemName());
                ResultSet rs = getItem.executeQuery();
                if (rs.next()) {
                    int itemId = rs.getInt("ItemID");

                    PreparedStatement insComp = conn.prepareStatement(
                            "INSERT INTO DeviceComponents (DeviceID, ItemID, Quantity) VALUES (?, ?, ?)"
                    );
                    insComp.setInt(1, deviceId);
                    insComp.setInt(2, itemId);
                    insComp.setDouble(3, entry.getQuantity());
                    insComp.executeUpdate();
                } else {
                    throw new SQLException("الصنف '" + entry.getItemName() + "' غير موجود في قاعدة البيانات.");
                }
            }

            conn.commit();
            showAlert(Alert.AlertType.INFORMATION, "تم", "تم حفظ الجهاز بنجاح!");
            ((Stage) saveDeviceButton.getScene().getWindow()).close();

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            showAlert(Alert.AlertType.ERROR, "خطأ في الحفظ", e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) {
                try {
                    if (!conn.isClosed()) {
                        conn.setAutoCommit(true);
                        conn.close();
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void loadForEdit(int deviceId, String deviceName) {
        this.editingDeviceId = deviceId;
        this.deviceNameField.setText(deviceName);
        components.clear();

        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                 SELECT i.ItemName, i.ItemCode, dc.Quantity
                 FROM DeviceComponents dc
                 JOIN Items i ON dc.ItemID = i.ItemID
                 WHERE dc.DeviceID = ?
             """)) {
            stmt.setInt(1, deviceId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String itemCode = rs.getString("ItemCode");
                components.add(new ComponentEntry(
                        rs.getString("ItemName"),
                        itemCode != null ? itemCode : "بدون كود",
                        rs.getDouble("Quantity")
                ));
            }
            componentTable.refresh();
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "خطأ في تحميل المكونات", e.getMessage());
        }
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    public static class ComponentEntry {
        private String itemName;
        private String itemCode;
        private double quantity;

        public ComponentEntry(String itemName, String itemCode, double quantity) {
            this.itemName = itemName;
            this.itemCode = itemCode;
            this.quantity = quantity;
        }

        public String getItemName() { return itemName; }
        public String getItemCode() { return itemCode; }
        public double getQuantity() { return quantity; }
        public void setQuantity(double q) { this.quantity = q; }
    }
}