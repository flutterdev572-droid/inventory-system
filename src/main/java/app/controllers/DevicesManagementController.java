package app.controllers;

import app.db.DatabaseConnection;
import app.models.DeviceModel;
import javafx.collections.*;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import java.sql.*;

public class DevicesManagementController {

    @FXML private TableView<DeviceModel> devicesTable;
    @FXML private TableColumn<DeviceModel, Integer> colID;
    @FXML private TableColumn<DeviceModel, String> colName;
    @FXML private TableColumn<DeviceModel, String> colSerial;
    @FXML private TableColumn<DeviceModel, Button> colEdit;
    @FXML private TableColumn<DeviceModel, Button> colComponents;
    @FXML private TableColumn<DeviceModel, Button> colDelete;
    @FXML private TextField searchField;

    private ObservableList<DeviceModel> devicesList = FXCollections.observableArrayList();
    private FilteredList<DeviceModel> filteredList;

    @FXML
    public void initialize() {
        colID.setCellValueFactory(new PropertyValueFactory<>("deviceID"));
        colName.setCellValueFactory(new PropertyValueFactory<>("deviceName"));
        colSerial.setCellValueFactory(new PropertyValueFactory<>("serial"));
        colEdit.setCellValueFactory(new PropertyValueFactory<>("editButton"));
        colComponents.setCellValueFactory(new PropertyValueFactory<>("componentsButton"));
        colDelete.setCellValueFactory(new PropertyValueFactory<>("deleteButton"));

        loadDevices();

        // ✅ إعداد الفلترة بعد تحميل الأجهزة
        filteredList = new FilteredList<>(devicesList, p -> true);
        devicesTable.setItems(filteredList);

        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredList.setPredicate(device -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }
                String lowerCaseFilter = newValue.toLowerCase();
                return device.getDeviceName().toLowerCase().contains(lowerCaseFilter) ||
                        device.getSerial().toLowerCase().contains(lowerCaseFilter);
            });
        });
    }

    private void loadDevices() {
        devicesList.clear();
        try(Connection conn = DatabaseConnection.getInventoryConnection()) {
            PreparedStatement stmt = conn.prepareStatement("SELECT DeviceID, DeviceName, SerialNumber FROM Devices");
            ResultSet rs = stmt.executeQuery();

            while(rs.next()) {
                int id = rs.getInt("DeviceID");
                String name = rs.getString("DeviceName");
                String serial = rs.getString("SerialNumber");

                Button edit = new Button("✏ تعديل");
                edit.setOnAction(e -> editDevice(id, name));

                Button comp = new Button("📦 مكونات");
                comp.setOnAction(e -> openComponents(id));

                Button del = new Button("🗑 حذف");
                del.setOnAction(e -> deleteDevice(id));

                devicesList.add(new DeviceModel(id, name, serial, edit, comp, del));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void editDevice(int id, String oldName) {
        TextInputDialog dialog = new TextInputDialog(oldName);
        dialog.setTitle("تعديل اسم الجهاز");
        dialog.setHeaderText("أدخل اسم جديد للجهاز:");
        dialog.showAndWait().ifPresent(newName -> {
            try(Connection conn = DatabaseConnection.getInventoryConnection()) {
                PreparedStatement stmt = conn.prepareStatement("UPDATE Devices SET DeviceName=? WHERE DeviceID=?");
                stmt.setString(1, newName);
                stmt.setInt(2, id);
                stmt.executeUpdate();
                loadDevices();
            } catch (Exception ex) { ex.printStackTrace(); }
        });
    }

    private void openComponents(int deviceID) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/DeviceComponentsView.fxml"));
            Parent root = loader.load();

            DeviceComponentsController controller = loader.getController();
            controller.setDeviceID(deviceID);

            Stage stage = new Stage();
            stage.setTitle("مكونات الجهاز");
            stage.setScene(new Scene(root));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("❌ خطأ أثناء فتح صفحة المكونات: " + e.getMessage());
        }
    }

    private void deleteDevice(int id) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "هل تريد حذف الجهاز نهائياً؟", ButtonType.YES, ButtonType.NO);
        if(alert.showAndWait().get() == ButtonType.YES) {
            try(Connection conn = DatabaseConnection.getInventoryConnection()) {
                PreparedStatement stmt = conn.prepareStatement("DELETE FROM Devices WHERE DeviceID=?");
                stmt.setInt(1, id);
                stmt.executeUpdate();
                loadDevices();
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    @FXML
    private void addDevice() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/AddDevice.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("تسجيل جهاز جديد");
            stage.setScene(new Scene(root));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("❌ خطأ أثناء فتح صفحة تسجيل جهاز جديد: " + e.getMessage());
        }
    }
}
