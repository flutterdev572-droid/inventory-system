package app.controllers;

import app.db.DatabaseConnection;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class PricingController {

    @FXML private ComboBox<String> itemNameComboBox;
    @FXML private TextField priceField;
    @FXML private TextField tableSearchField;
    @FXML private TableView<ItemPrice> pricingTable;
    @FXML private TableColumn<ItemPrice, String> colItemCode; // العمود الجديد
    @FXML private TableColumn<ItemPrice, String> colItem;
    @FXML private TableColumn<ItemPrice, Double> colPrice;

    private ObservableList<ItemPrice> pricingList = FXCollections.observableArrayList();
    private ObservableList<String> allItemNames = FXCollections.observableArrayList();
    private FilteredList<ItemPrice> filteredList;

    @FXML
    public void initialize() {
        // إعداد أعمدة الجدول
        colItemCode.setCellValueFactory(data -> data.getValue().itemCodeProperty()); // العمود الجديد
        colItem.setCellValueFactory(data -> data.getValue().itemNameProperty());
        colPrice.setCellValueFactory(data -> data.getValue().priceProperty().asObject());

        loadPricingData();
        setupAutoComplete();
        setupTableClick();
        setupTableSearch();
    }

    private void loadPricingData() {
        pricingList.clear();
        allItemNames.clear();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                     SELECT i.ItemID, i.ItemName, i.ItemCode, ISNULL(p.UnitPrice, 0) AS UnitPrice
                     FROM Items i
                     LEFT JOIN ItemPrices p ON i.ItemID = p.ItemID
                     ORDER BY i.ItemName ASC
                     """)) {

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                int itemId = rs.getInt("ItemID");
                String name = rs.getString("ItemName");
                String itemCode = rs.getString("ItemCode"); // جلب كود الصنف
                double price = rs.getDouble("UnitPrice");

                pricingList.add(new ItemPrice(itemId, name, itemCode, price));
                allItemNames.add(name);
            }

            pricingTable.setItems(pricingList);
            itemNameComboBox.setItems(allItemNames);

            // إنشاء FilteredList للبحث
            filteredList = new FilteredList<>(pricingList, p -> true);
            pricingTable.setItems(filteredList);

        } catch (Exception e) {
            showAlert("خطأ", "فشل تحميل الأسعار:\n" + e.getMessage());
        }
    }

    // ✅ AutoComplete ComboBox
    private void setupAutoComplete() {
        itemNameComboBox.setEditable(true);

        itemNameComboBox.getEditor().textProperty().addListener((obs, oldText, newText) -> {
            if (newText == null || newText.isEmpty()) {
                itemNameComboBox.hide();
                return;
            }

            ObservableList<String> filtered = FXCollections.observableArrayList();
            for (String item : allItemNames) {
                if (item.toLowerCase().contains(newText.toLowerCase())) filtered.add(item);
            }

            if (!filtered.isEmpty()) {
                itemNameComboBox.setItems(filtered);
                itemNameComboBox.show();
            } else itemNameComboBox.hide();
        });

        itemNameComboBox.setOnAction(event -> {
            String selected = itemNameComboBox.getSelectionModel().getSelectedItem();
            fillPriceForItem(selected);
        });
    }

    // ✅ عند الضغط على أي صف في الجدول
    private void setupTableClick() {
        pricingTable.setRowFactory(tv -> {
            TableRow<ItemPrice> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty()) {
                    ItemPrice clicked = row.getItem();
                    itemNameComboBox.getEditor().setText(clicked.getItemName());
                    priceField.setText(String.valueOf(clicked.getPrice()));
                }
            });
            return row;
        });
    }

    // ✅ فلترة الجدول - تم التعديل للبحث بالاسم والكود
    private void setupTableSearch() {
        tableSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredList.setPredicate(item -> {
                if (newVal == null || newVal.isEmpty()) return true;

                String searchText = newVal.toLowerCase();
                return item.getItemName().toLowerCase().contains(searchText) ||
                        (item.getItemCode() != null && item.getItemCode().toLowerCase().contains(searchText));
            });
        });
    }

    private void fillPriceForItem(String name) {
        if (name == null) return;
        for (ItemPrice item : pricingList) {
            if (item.getItemName().equalsIgnoreCase(name)) {
                priceField.setText(String.valueOf(item.getPrice()));
                break;
            }
        }
    }

    @FXML
    private void addPrice() {
        String name = itemNameComboBox.getEditor().getText();
        String priceText = priceField.getText();

        if (name.isEmpty() || priceText.isEmpty()) {
            showAlert("تحذير", "من فضلك أدخل اسم الصنف والسعر.");
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            PreparedStatement getItemIdStmt = conn.prepareStatement("SELECT ItemID FROM Items WHERE ItemName = ?");
            getItemIdStmt.setString(1, name);
            ResultSet rs = getItemIdStmt.executeQuery();

            if (!rs.next()) {
                showAlert("خطأ", "الصنف غير موجود.");
                return;
            }

            int itemId = rs.getInt("ItemID");
            double price = Double.parseDouble(priceText);

            PreparedStatement check = conn.prepareStatement("SELECT COUNT(*) FROM ItemPrices WHERE ItemID = ?");
            check.setInt(1, itemId);
            ResultSet rCheck = check.executeQuery();
            rCheck.next();

            if (rCheck.getInt(1) > 0) {
                PreparedStatement update = conn.prepareStatement("UPDATE ItemPrices SET UnitPrice=?, UpdatedAt=GETDATE() WHERE ItemID=?");
                update.setDouble(1, price);
                update.setInt(2, itemId);
                update.executeUpdate();
            } else {
                PreparedStatement insert = conn.prepareStatement("INSERT INTO ItemPrices (ItemID, UnitPrice, CreatedAt) VALUES (?, ?, GETDATE())");
                insert.setInt(1, itemId);
                insert.setDouble(2, price);
                insert.executeUpdate();
            }

            loadPricingData();
            clearFields();

        } catch (Exception e) {
            showAlert("خطأ", "فشل حفظ السعر:\n" + e.getMessage());
        }
    }

    @FXML
    private void updatePrice() {
        ItemPrice selected = pricingTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("تحذير", "اختر صنف لتعديله.");
            return;
        }

        String priceText = priceField.getText();
        if (priceText.isEmpty()) {
            showAlert("تحذير", "من فضلك أدخل السعر الجديد.");
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("UPDATE ItemPrices SET UnitPrice=?, UpdatedAt=GETDATE() WHERE ItemID=?")) {

            stmt.setDouble(1, Double.parseDouble(priceText));
            stmt.setInt(2, selected.getItemId());
            stmt.executeUpdate();
            loadPricingData();

        } catch (Exception e) {
            showAlert("خطأ", "فشل تحديث السعر:\n" + e.getMessage());
        }
    }

    @FXML
    private void deletePrice() {
        ItemPrice selected = pricingTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("تحذير", "اختر صنف لحذفه.");
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM ItemPrices WHERE ItemID = ?")) {

            stmt.setInt(1, selected.getItemId());
            stmt.executeUpdate();
            loadPricingData();

        } catch (Exception e) {
            showAlert("خطأ", "فشل حذف السعر:\n" + e.getMessage());
        }
    }

    @FXML
    private void refreshTable() {
        loadPricingData();
    }

    private void clearFields() {
        itemNameComboBox.getEditor().clear();
        priceField.clear();
        tableSearchField.clear();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // ✅ كلاس مساعد - تم التعديل لإضافة كود الصنف
    public static class ItemPrice {
        private final javafx.beans.property.SimpleIntegerProperty itemId;
        private final javafx.beans.property.SimpleStringProperty itemName;
        private final javafx.beans.property.SimpleStringProperty itemCode; // الخاصية الجديدة
        private final javafx.beans.property.SimpleDoubleProperty price;

        public ItemPrice(int itemId, String itemName, String itemCode, double price) {
            this.itemId = new javafx.beans.property.SimpleIntegerProperty(itemId);
            this.itemName = new javafx.beans.property.SimpleStringProperty(itemName);
            this.itemCode = new javafx.beans.property.SimpleStringProperty(itemCode); // تهيئة كود الصنف
            this.price = new javafx.beans.property.SimpleDoubleProperty(price);
        }

        public int getItemId() { return itemId.get(); }
        public String getItemName() { return itemName.get(); }
        public String getItemCode() { return itemCode.get(); } // جيتر جديد
        public double getPrice() { return price.get(); }

        public javafx.beans.property.IntegerProperty itemIdProperty() { return itemId; }
        public javafx.beans.property.StringProperty itemNameProperty() { return itemName; }
        public javafx.beans.property.StringProperty itemCodeProperty() { return itemCode; } // خاصية جديدة
        public javafx.beans.property.DoubleProperty priceProperty() { return price; }
    }
}