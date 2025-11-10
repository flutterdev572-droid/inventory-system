package app.controllers;

import app.db.DatabaseConnection;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import java.sql.*;
import java.time.format.DateTimeFormatter;

public class AllSerialsController {

    @FXML private TableView<SerialItem> serialsTable;
    @FXML private TableColumn<SerialItem, String> colSerial;
    @FXML private TableColumn<SerialItem, String> colCreatedAt;
    @FXML private TableColumn<SerialItem, String> colStatus;
    @FXML private TableColumn<SerialItem, Void> colAction;
    @FXML private TextField searchField;
    @FXML private Button selectBtn;
    @FXML private Button closeBtn;

    private SerialTrackingController parentController;
    private SerialTrackingController.Device device;
    private ObservableList<SerialItem> serialsList = FXCollections.observableArrayList();
    private FilteredList<SerialItem> filteredData;

    public void setParentController(SerialTrackingController parentController) {
        this.parentController = parentController;
    }

    public void setDevice(SerialTrackingController.Device device) {
        this.device = device;
    }

    public void setSerials(ObservableList<String> serials) {
        serialsList.clear();
        for (String serial : serials) {
            // جلب تاريخ الإنشاء وحالة الجهاز من قاعدة البيانات
            String createdAt = getCreatedAtForSerial(serial);
            String status = getDeviceStatus(serial);
            serialsList.add(new SerialItem(serial, createdAt, status));
        }
        setupSearch();
    }

    @FXML
    public void initialize() {
        colSerial.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));
        colCreatedAt.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        // إعداد عمود الإجراءات
        setupActionColumn();

        // إعداد البحث
        setupSearch();

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

    // إعداد عمود الإجراءات
    private void setupActionColumn() {
        Callback<TableColumn<SerialItem, Void>, TableCell<SerialItem, Void>> cellFactory =
                new Callback<TableColumn<SerialItem, Void>, TableCell<SerialItem, Void>>() {
                    @Override
                    public TableCell<SerialItem, Void> call(final TableColumn<SerialItem, Void> param) {
                        return new TableCell<SerialItem, Void>() {
                            private final Button viewBtn = new Button("عرض");

                            {
                                viewBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 5 10;");
                                viewBtn.setOnAction(event -> {
                                    SerialItem data = getTableView().getItems().get(getIndex());
                                    if (data != null && "خرج من المصنع".equals(data.getStatus())) {
                                        openDeviceExitDetails(data.getSerialNumber());
                                    }
                                });
                            }

                            @Override
                            public void updateItem(Void item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty) {
                                    setGraphic(null);
                                } else {
                                    SerialItem data = getTableView().getItems().get(getIndex());
                                    if (data != null && "خرج من المصنع".equals(data.getStatus())) {
                                        setGraphic(viewBtn);
                                    } else {
                                        setGraphic(null);
                                    }
                                }
                            }
                        };
                    }
                };

        colAction.setCellFactory(cellFactory);
    }

    // إعداد وظيفة البحث
    private void setupSearch() {
        filteredData = new FilteredList<>(serialsList, p -> true);

        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredData.setPredicate(serial -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }

                String lowerCaseFilter = newValue.toLowerCase();
                return serial.getSerialNumber().toLowerCase().contains(lowerCaseFilter) ||
                        serial.getStatus().toLowerCase().contains(lowerCaseFilter);
            });
        });

        serialsTable.setItems(filteredData);
    }

    public void loadSerials() {
        if (device == null) return;

        serialsList.clear();

        String sql = """
            SELECT DS.SerialNumber, DS.CreatedAt 
            FROM DeviceSerials DS 
            WHERE DS.DeviceID = ? 
            ORDER BY DS.CreatedAt DESC
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, device.getId());
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                String serial = rs.getString("SerialNumber");
                Timestamp createdAt = rs.getTimestamp("CreatedAt");
                String formattedDate = createdAt != null ?
                        createdAt.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) :
                        "غير محدد";

                String status = getDeviceStatus(serial);
                serialsList.add(new SerialItem(serial, formattedDate, status));
            }

            setupSearch();

        } catch (Exception e) {
            e.printStackTrace();
            showAlert("حدث خطأ أثناء تحميل السيريالات.");
        }
    }

    // دالة مساعدة لجلب تاريخ الإنشاء
    private String getCreatedAtForSerial(String serialNumber) {
        String sql = "SELECT CreatedAt FROM DeviceSerials WHERE SerialNumber = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, serialNumber);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                Timestamp createdAt = rs.getTimestamp("CreatedAt");
                return createdAt != null ?
                        createdAt.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) :
                        "غير محدد";
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "غير معروف";
    }

    // دالة مساعدة لتحديد حالة الجهاز
    private String getDeviceStatus(String serialNumber) {
        String sql = "SELECT COUNT(*) as count FROM DeviceExit WHERE SerialNumber = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, serialNumber);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                int count = rs.getInt("count");
                return count > 0 ? "خرج من المصنع" : "قيد التصنيع";
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "قيد التصنيع";
    }

    // فتح تفاصيل خروج الجهاز
    private void openDeviceExitDetails(String serialNumber) {
        try {
            // تحميل واجهة DeviceExit
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/DeviceExitView.fxml"));
            Parent root = loader.load();

            DeviceExitController controller = loader.getController();

            // يمكنك إضافة دالة في DeviceExitController للبحث عن سيريال محدد
            // controller.filterBySerial(serialNumber);

            Stage stage = new Stage();
            stage.setTitle("تفاصيل خروج الجهاز - " + serialNumber);
            stage.setScene(new Scene(root, 1000, 700));
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(serialsTable.getScene().getWindow());
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
            showAlert("حدث خطأ أثناء فتح تفاصيل الجهاز: " + e.getMessage());
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
        private final String status;

        public SerialItem(String serialNumber, String createdAt, String status) {
            this.serialNumber = serialNumber;
            this.createdAt = createdAt;
            this.status = status;
        }

        public String getSerialNumber() { return serialNumber; }
        public String getCreatedAt() { return createdAt; }
        public String getStatus() { return status; }
    }
}