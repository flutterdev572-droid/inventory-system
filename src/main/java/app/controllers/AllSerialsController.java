package app.controllers;

import app.db.DatabaseConnection;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import java.sql.*;

public class AllSerialsController {

    @FXML private TableView<SerialItem> serialsTable;
    @FXML private TableColumn<SerialItem, String> colSerial;
    @FXML private TableColumn<SerialItem, String> colCreatedAt;
    @FXML private Button selectBtn;
    @FXML private Button closeBtn;

    private SerialTrackingController parentController;
    private SerialTrackingController.Device device;
    private ObservableList<SerialItem> serialsList = FXCollections.observableArrayList();

    public void setParentController(SerialTrackingController parentController) {
        this.parentController = parentController;
    }

    public void setDevice(SerialTrackingController.Device device) {
        this.device = device;
    }

    public void setSerials(ObservableList<String> serials) {
        serialsList.clear();
        for (String serial : serials) {
            serialsList.add(new SerialItem(serial, ""));
        }
        serialsTable.setItems(serialsList);
    }

    @FXML
    public void initialize() {
        colSerial.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));
        colCreatedAt.setCellValueFactory(new PropertyValueFactory<>("createdAt"));

        // اختيار سريال عند النقر المزدوج
        serialsTable.setRowFactory(tv -> {
            TableRow<SerialItem> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    selectSerial();
                }
            });
            return row;
        });

        selectBtn.setOnAction(e -> selectSerial());
        closeBtn.setOnAction(e -> closeWindow());
    }

    public void loadSerials() {
        if (device == null) return;

        serialsList.clear();

        String sql = """
            SELECT SerialNumber, CreatedAt 
            FROM DeviceSerials 
            WHERE DeviceID = ? 
            ORDER BY CreatedAt DESC
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, device.getId());
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                String serial = rs.getString("SerialNumber");
                String createdAt = rs.getTimestamp("CreatedAt").toString();
                serialsList.add(new SerialItem(serial, createdAt));
            }

            serialsTable.setItems(serialsList);

        } catch (Exception e) {
            e.printStackTrace();
            showAlert("حدث خطأ أثناء تحميل السيريالات.");
        }
    }

    private void selectSerial() {
        SerialItem selected = serialsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            parentController.setSelectedSerial(selected.getSerialNumber());
            closeWindow();
        } else {
            showAlert("اختر سيريال أولاً");
        }
    }

    private void closeWindow() {
        Stage stage = (Stage) closeBtn.getScene().getWindow();
        stage.close();
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.showAndWait();
    }

    // كلاس لعرض بيانات السيريال في الجدول
    public static class SerialItem {
        private final String serialNumber;
        private final String createdAt;

        public SerialItem(String serialNumber, String createdAt) {
            this.serialNumber = serialNumber;
            this.createdAt = createdAt;
        }

        public String getSerialNumber() { return serialNumber; }
        public String getCreatedAt() { return createdAt; }
    }
}