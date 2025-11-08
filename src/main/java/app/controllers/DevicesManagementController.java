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

        // ✅ إعداد الفلترة أولاً قبل تحميل البيانات
        filteredList = new FilteredList<>(devicesList, p -> true);
        devicesTable.setItems(filteredList);

        // ✅ إعداد البحث
        setupSearch();

        // ✅ تحميل الأجهزة بعد إعداد الفلترة
        loadDevices();
    }

    // ✅ إعداد وظيفة البحث
    private void setupSearch() {
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredList.setPredicate(device -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }
                String lowerCaseFilter = newValue.toLowerCase();
                return device.getDeviceName().toLowerCase().contains(lowerCaseFilter) ||
                        (device.getSerial() != null && device.getSerial().toLowerCase().contains(lowerCaseFilter));
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
                edit.setStyle("-fx-background-color: #f59e0b; -fx-text-fill: white; -fx-font-weight: bold;");
                edit.setOnAction(e -> editDevice(id, name));

                Button comp = new Button("📦 مكونات");
                comp.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold;");
                comp.setOnAction(e -> openComponents(id));

                Button del = new Button("🗑 حذف");
                del.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold;");
                del.setOnAction(e -> deleteDevice(id));

                devicesList.add(new DeviceModel(id, name, serial, edit, comp, del));
            }
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("حدث خطأ أثناء تحميل الأجهزة: " + e.getMessage());
        }
    }

    private void editDevice(int id, String oldName) {
        TextInputDialog dialog = new TextInputDialog(oldName);
        dialog.setTitle("تعديل اسم الجهاز");
        dialog.setHeaderText("أدخل اسم جديد للجهاز:");
        dialog.setContentText("اسم الجهاز:");
        dialog.showAndWait().ifPresent(newName -> {
            if (newName != null && !newName.trim().isEmpty()) {
                try(Connection conn = DatabaseConnection.getInventoryConnection()) {
                    PreparedStatement stmt = conn.prepareStatement("UPDATE Devices SET DeviceName=? WHERE DeviceID=?");
                    stmt.setString(1, newName.trim());
                    stmt.setInt(2, id);
                    stmt.executeUpdate();
                    loadDevices(); // إعادة تحميل البيانات بعد التعديل
                    showAlert("تم تعديل اسم الجهاز بنجاح");
                } catch (Exception ex) {
                    ex.printStackTrace();
                    showAlert("حدث خطأ أثناء تعديل الجهاز: " + ex.getMessage());
                }
            }
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
            stage.setScene(new Scene(root, 800, 600));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("حدث خطأ أثناء فتح صفحة المكونات: " + e.getMessage());
        }
    }

    private void deleteDevice(int id) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("تأكيد الحذف");
        alert.setHeaderText("هل تريد حذف الجهاز نهائياً؟");
        alert.setContentText("هذا الإجراء لا يمكن التراجع عنه");

        if(alert.showAndWait().get() == ButtonType.YES) {
            try(Connection conn = DatabaseConnection.getInventoryConnection()) {
                PreparedStatement stmt = conn.prepareStatement("DELETE FROM Devices WHERE DeviceID=?");
                stmt.setInt(1, id);
                int affectedRows = stmt.executeUpdate();
                if (affectedRows > 0) {
                    showAlert("تم حذف الجهاز بنجاح");
                    loadDevices(); // إعادة تحميل البيانات بعد الحذف
                }
            } catch (Exception e) {
                e.printStackTrace();
                showAlert("حدث خطأ أثناء حذف الجهاز: " + e.getMessage());
            }
        }
    }

    @FXML
    private void addDevice() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/AddDevice.fxml"));
            Parent root = loader.load();

            // الحصول على controller إذا كنت تريد تمرير المرجع
            Object controller = loader.getController();

            Stage stage = new Stage();
            stage.setTitle("تسجيل جهاز جديد");
            stage.setScene(new Scene(root));
            stage.setOnHidden(e -> loadDevices()); // إعادة تحميل البيانات عند إغلاق النافذة
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("حدث خطأ أثناء فتح صفحة تسجيل جهاز جديد: " + e.getMessage());
        }
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("معلومات");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}