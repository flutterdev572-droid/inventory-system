package app.controllers;

import app.db.DatabaseConnection;
import app.models.ComponentModel;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DeviceComponentsController {

    @FXML private TableView<ComponentModel> componentsTable;
    @FXML private TableColumn<ComponentModel, String> colItemName;
    @FXML private TableColumn<ComponentModel, Double> colQuantity;
    @FXML private TableColumn<ComponentModel, Button> colEdit;
    @FXML private TableColumn<ComponentModel, Button> colDelete;

    private int deviceID;

    public void setDeviceID(int deviceID) {
        this.deviceID = deviceID;
        loadComponents();
    }

    @FXML
    public void initialize() {
        colItemName.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        colQuantity.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colEdit.setCellValueFactory(new PropertyValueFactory<>("editButton"));
        colDelete.setCellValueFactory(new PropertyValueFactory<>("deleteButton"));
    }

    private void loadComponents() {
        componentsTable.getItems().clear();

        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = """
                SELECT DC.ID, I.ItemName, DC.Quantity
                FROM DeviceComponents DC
                JOIN Items I ON DC.ItemID = I.ItemID
                WHERE DC.DeviceID = ?
            """;

            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, deviceID);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {

                int compID = rs.getInt("ID");
                String itemName = rs.getString("ItemName");
                double qty = rs.getDouble("Quantity");

                Button editBtn = new Button("✏ تعديل");
                Button deleteBtn = new Button("🗑 حذف");

                editBtn.setOnAction(e -> editComponent(compID, itemName, qty));
                deleteBtn.setOnAction(e -> deleteComponent(compID));

                componentsTable.getItems().add(
                        new ComponentModel(compID, itemName, qty, editBtn, deleteBtn)
                );
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void editComponent(int id, String oldName, double oldQty) {
        TextInputDialog dialog = new TextInputDialog(String.valueOf(oldQty));
        dialog.setTitle("تعديل الكمية");
        dialog.setHeaderText("اسم المكون: " + oldName);
        dialog.setContentText("ادخل الكمية الجديدة:");

        dialog.showAndWait().ifPresent(newQtyStr -> {
            try {
                double newQty = Double.parseDouble(newQtyStr);

                try (Connection conn = DatabaseConnection.getConnection()) {
                    PreparedStatement ps = conn.prepareStatement(
                            "UPDATE DeviceComponents SET Quantity = ? WHERE ID = ?"
                    );
                    ps.setDouble(1, newQty);
                    ps.setInt(2, id);
                    ps.executeUpdate();
                    loadComponents();
                }

            } catch (Exception ex) {
                showAlert("خطأ", "الكمية يجب أن تكون رقم!", Alert.AlertType.ERROR);
            }
        });
    }

    private void deleteComponent(int id) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "هل تريد حذف هذا المكون؟", ButtonType.YES, ButtonType.NO);

        if (alert.showAndWait().get() == ButtonType.YES) {
            try (Connection conn = DatabaseConnection.getConnection()) {
                PreparedStatement ps = conn.prepareStatement("DELETE FROM DeviceComponents WHERE ID = ?");
                ps.setInt(1, id);
                ps.executeUpdate();
                loadComponents();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @FXML
    private void openAddComponentPopup() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("إضافة مكون جديد");

        // ✅ تحميل الأصناف
        ObservableList<String> itemsList = FXCollections.observableArrayList();
        try (Connection conn = DatabaseConnection.getConnection()) {
            ResultSet rs = conn.prepareStatement("SELECT ItemName FROM Items").executeQuery();
            while (rs.next()) itemsList.add(rs.getString("ItemName"));
        } catch (Exception e) { e.printStackTrace(); }

        // ✅ إنشاء ComboBox قابل للبحث
        ComboBox<String> itemsCombo = new ComboBox<>();
        itemsCombo.setEditable(true);

        FilteredList<String> filteredItems = new FilteredList<>(itemsList, p -> true);
        itemsCombo.setItems(filteredItems);

        itemsCombo.getEditor().textProperty().addListener((obs, oldValue, newValue) -> {
            filteredItems.setPredicate(item -> item.toLowerCase().contains(newValue.toLowerCase()));
        });

        itemsCombo.setPromptText("ابحث عن الصنف واختاره");

        TextField qtyField = new TextField();
        qtyField.setPromptText("الكمية");

        VBox box = new VBox(10, new Label("الصنف:"), itemsCombo,
                new Label("الكمية:"), qtyField);
        box.setStyle("-fx-padding: 10;");
        dialog.getDialogPane().setContent(box);

        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {

                String selectedItem = itemsCombo.getValue();
                if (selectedItem == null || selectedItem.isEmpty()) {
                    showAlert("خطأ", "يجب اختيار صنف!", Alert.AlertType.ERROR);
                    return;
                }

                double qty;
                try {
                    qty = Double.parseDouble(qtyField.getText());
                } catch (Exception e) {
                    showAlert("خطأ", "الكمية يجب أن تكون رقم!", Alert.AlertType.ERROR);
                    return;
                }

                try (Connection conn = DatabaseConnection.getConnection()) {

                    PreparedStatement getItemID = conn.prepareStatement(
                            "SELECT ItemID FROM Items WHERE ItemName=?");
                    getItemID.setString(1, selectedItem);
                    ResultSet rs = getItemID.executeQuery();

                    if (rs.next()) {
                        int itemID = rs.getInt("ItemID");

                        PreparedStatement ps = conn.prepareStatement(
                                "INSERT INTO DeviceComponents (DeviceID, ItemID, Quantity) VALUES (?, ?, ?)"
                        );
                        ps.setInt(1, deviceID);
                        ps.setInt(2, itemID);
                        ps.setDouble(3, qty);
                        ps.executeUpdate();
                        loadComponents();
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    private void showAlert(String title, String msg, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.show();
    }

}
