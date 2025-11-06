package app.controllers;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.sql.*;
import app.db.DatabaseConnection;

public class AddDeviceComponentController {

    @FXML private ComboBox<String> itemComboBox;
    @FXML private TextField quantityField;
    private ObservableList<String> originalItems = FXCollections.observableArrayList();

    private int deviceId; // هنوصلها من شاشة الجهاز

    public void setDeviceId(int id) {
        this.deviceId = id;
        loadItems();
    }

    private void loadItems() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT ItemName FROM Items ORDER BY ItemName";
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                originalItems.add(rs.getString("ItemName"));
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

        editor.textProperty().addListener((obs, oldValue, newValue) -> {
            comboBox.show();
            comboBox.getItems().setAll(
                    originalItems.filtered(item ->
                            item.toLowerCase().contains(newValue.toLowerCase())
                    )
            );
        });
    }


    @FXML
    private void saveComponent() {
        String item = itemComboBox.getValue();
        String qtyText = quantityField.getText();

        if (item == null || qtyText.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "من فضلك اختر الصنف وأدخل الكمية").show();
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "INSERT INTO DeviceComponents (DeviceID, ItemName, Quantity) VALUES (?, ?, ?)";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, deviceId);
            ps.setString(2, item);
            ps.setInt(3, Integer.parseInt(qtyText));
            ps.executeUpdate();

            new Alert(Alert.AlertType.INFORMATION, "✔ تمت إضافة المكون بنجاح").show();
            closePopup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void closePopup() {
        Stage stage = (Stage) itemComboBox.getScene().getWindow();
        stage.close();
    }

}
