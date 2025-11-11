package app.controllers;

import app.current_user.CurrentUser;
import app.db.DatabaseConnection;
import app.services.LogService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import java.sql.*;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class AdminRequestsController {

    @FXML private TableView<StockRequest> requestsTable;
    @FXML private TableColumn<StockRequest, Integer> requestIdColumn;
    @FXML private TableColumn<StockRequest, String> itemNameColumn;
    @FXML private TableColumn<StockRequest, String> deviceNameColumn;
    @FXML private TableColumn<StockRequest, String> serialNumberColumn;
    @FXML private TableColumn<StockRequest, Double> quantityColumn;
    @FXML private TableColumn<StockRequest, String> requesterColumn;
    @FXML private TableColumn<StockRequest, String> requestDateColumn;
    @FXML private TableColumn<StockRequest, String> statusColumn;
    @FXML private TableColumn<StockRequest, String> reasonColumn;

    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilterCombo;
    @FXML private Button approveButton;
    @FXML private Button rejectButton;
    @FXML private Button refreshButton;

    private final ObservableList<StockRequest> allRequests = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTableColumns();
        loadRequestsData();
        setupFilters();
    }

    private void setupTableColumns() {
        requestIdColumn.setCellValueFactory(new PropertyValueFactory<>("requestId"));
        itemNameColumn.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        deviceNameColumn.setCellValueFactory(new PropertyValueFactory<>("deviceName"));
        serialNumberColumn.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));
        quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        requesterColumn.setCellValueFactory(new PropertyValueFactory<>("requesterName"));
        requestDateColumn.setCellValueFactory(new PropertyValueFactory<>("requestDate"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        reasonColumn.setCellValueFactory(new PropertyValueFactory<>("reason"));

        requestsTable.setRowFactory(tv -> {
            TableRow<StockRequest> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showRequestDetails(row.getItem());
                }
            });
            return row;
        });
    }

    private void loadRequestsData() {
        allRequests.clear();

        String query = """
            SELECT 
                sr.RequestID,
                i.ItemName,
                d.DeviceName,
                ds.SerialNumber,
                sr.RequestedQuantity,
                sr.RequestedByName,
                sr.RequestedDate,
                sr.Status,
                sr.Reason,
                sr.DefectiveNumber,
                sr.AssignedToEmployee,
                sr.ApprovedBy,
                sr.ApprovedDate,
                sr.ItemID,
                sr.SerialID
            FROM StockRequests sr
            INNER JOIN Items i ON sr.ItemID = i.ItemID
            INNER JOIN DeviceSerials ds ON sr.SerialID = ds.SerialID
            INNER JOIN Devices d ON ds.DeviceID = d.DeviceID
            ORDER BY sr.RequestedDate DESC
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                StockRequest request = new StockRequest(
                        rs.getInt("RequestID"),
                        rs.getString("ItemName"),
                        rs.getString("DeviceName"),
                        rs.getString("SerialNumber"),
                        rs.getDouble("RequestedQuantity"),
                        rs.getString("RequestedByName"),
                        rs.getTimestamp("RequestedDate"),
                        rs.getString("Status"),
                        rs.getString("Reason"),
                        rs.getString("DefectiveNumber"),
                        rs.getString("AssignedToEmployee"),
                        rs.getInt("ApprovedBy"),
                        rs.getTimestamp("ApprovedDate"),
                        rs.getInt("ItemID"),
                        rs.getInt("SerialID")
                );
                allRequests.add(request);
            }

            requestsTable.setItems(allRequests);

        } catch (SQLException e) {
            e.printStackTrace();
            showError("خطأ في تحميل طلبات الصرف: " + e.getMessage());
        }
    }

    private void setupFilters() {
        statusFilterCombo.setItems(FXCollections.observableArrayList(
                "الكل", "Pending", "Approved", "Rejected"
        ));
        statusFilterCombo.setValue("الكل");

        statusFilterCombo.setOnAction(e -> filterRequests());

        searchField.textProperty().addListener((obs, oldValue, newValue) -> {
            filterRequests();
        });
    }

    private void filterRequests() {
        String statusFilter = statusFilterCombo.getValue();
        String searchTerm = searchField.getText().toLowerCase().trim();

        ObservableList<StockRequest> filtered = FXCollections.observableArrayList();

        for (StockRequest request : allRequests) {
            boolean statusMatch = statusFilter.equals("الكل") ||
                    request.getStatus().equals(statusFilter);

            boolean searchMatch = searchTerm.isEmpty() ||
                    request.getItemName().toLowerCase().contains(searchTerm) ||
                    request.getDeviceName().toLowerCase().contains(searchTerm) ||
                    request.getSerialNumber().toLowerCase().contains(searchTerm) ||
                    request.getRequesterName().toLowerCase().contains(searchTerm);

            if (statusMatch && searchMatch) {
                filtered.add(request);
            }
        }

        requestsTable.setItems(filtered);
    }

    @FXML
    private void onApproveClicked() {
        StockRequest selectedRequest = requestsTable.getSelectionModel().getSelectedItem();

        if (selectedRequest == null) {
            showError("يرجى اختيار طلب أولاً!");
            return;
        }

        if (!selectedRequest.getStatus().equals("Pending")) {
            showError("يمكن الموافقة على الطلبات المعلقة فقط!");
            return;
        }

        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("تأكيد الموافقة");
        confirmAlert.setHeaderText("هل تريد الموافقة على هذا الطلب؟");
        confirmAlert.setContentText("العنصر: " + selectedRequest.getItemName() +
                "\nالكمية: " + selectedRequest.getQuantity() +
                "\nالجهاز: " + selectedRequest.getDeviceName() +
                "\nالسيريال: " + selectedRequest.getSerialNumber());

        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            approveRequest(selectedRequest);
        }
    }

    @FXML
    private void onRejectClicked() {
        StockRequest selectedRequest = requestsTable.getSelectionModel().getSelectedItem();

        if (selectedRequest == null) {
            showError("يرجى اختيار طلب أولاً!");
            return;
        }

        if (!selectedRequest.getStatus().equals("Pending")) {
            showError("يمكن رفض الطلبات المعلقة فقط!");
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("رفض الطلب");
        dialog.setHeaderText("سبب الرفض");
        dialog.setContentText("الرجاء إدخال سبب الرفض:");

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent() && !result.get().trim().isEmpty()) {
            rejectRequest(selectedRequest, result.get());
        } else if (result.isPresent()) {
            showError("يجب إدخال سبب الرفض!");
        }
    }

    private void approveRequest(StockRequest request) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            // 1. تحديث حالة الطلب
            String updateQuery = """
                UPDATE StockRequests 
                SET Status = 'Approved', ApprovedBy = ?, ApprovedDate = GETDATE()
                WHERE RequestID = ?
            """;
            try (PreparedStatement ps = conn.prepareStatement(updateQuery)) {
                ps.setInt(1, CurrentUser.getId());
                ps.setInt(2, request.getRequestId());
                ps.executeUpdate();
            }

            // 2. تنفيذ صرف الكمية من المخزون
            executeStockOut(request, conn);

            // 3. تسجيل في SerialComponentUsage
            recordSerialUsage(request, conn);

            conn.commit();

            // 4. تسجيل في اللوج
            LogService.addLog("REQUEST_APPROVED",
                    "تم الموافقة على طلب الصرف #" + request.getRequestId() +
                            " - العنصر: " + request.getItemName() +
                            " - الكمية: " + request.getQuantity());

            showInfo("تمت الموافقة على الطلب بنجاح!");
            loadRequestsData();

        } catch (SQLException e) {
            e.printStackTrace();
            showError("خطأ في الموافقة على الطلب: " + e.getMessage());
        }
    }

    private void executeStockOut(StockRequest request, Connection conn) throws SQLException {
        // تسجيل في StockTransactions
        String transactionQuery = """
            INSERT INTO StockTransactions (ItemID, TransactionType, Quantity, ReceiverName, Notes, EmployeeID)
            VALUES (?, 'OUT', ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(transactionQuery)) {
            ps.setInt(1, request.getItemId());
            ps.setDouble(2, request.getQuantity());
            ps.setString(3, request.getRequesterName());
            ps.setString(4, "طلب معتمد - " + request.getReason());
            ps.setInt(5, CurrentUser.getId());
            ps.executeUpdate();
        }
    }

    private void recordSerialUsage(StockRequest request, Connection conn) throws SQLException {
        String usageQuery = """
            INSERT INTO SerialComponentUsage (SerialID, ItemID, Quantity, UsedBy)
            VALUES (?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(usageQuery)) {
            ps.setInt(1, request.getSerialId());
            ps.setInt(2, request.getItemId());
            ps.setDouble(3, request.getQuantity());
            ps.setInt(4, CurrentUser.getId());
            ps.executeUpdate();
        }
    }

    private void rejectRequest(StockRequest request, String rejectionReason) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String query = """
                UPDATE StockRequests 
                SET Status = 'Rejected', Reason = CONCAT(Reason, ' - سبب الرفض: ', ?), ApprovedBy = ?, ApprovedDate = GETDATE()
                WHERE RequestID = ?
            """;
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, rejectionReason);
                ps.setInt(2, CurrentUser.getId());
                ps.setInt(3, request.getRequestId());
                ps.executeUpdate();
            }

            LogService.addLog("REQUEST_REJECTED",
                    "تم رفض طلب الصرف #" + request.getRequestId() +
                            " - السبب: " + rejectionReason);

            showInfo("تم رفض الطلب بنجاح!");
            loadRequestsData();

        } catch (SQLException e) {
            e.printStackTrace();
            showError("خطأ في رفض الطلب: " + e.getMessage());
        }
    }

    private void showRequestDetails(StockRequest request) {
        Alert detailsAlert = new Alert(Alert.AlertType.INFORMATION);
        detailsAlert.setTitle("تفاصيل الطلب #" + request.getRequestId());
        detailsAlert.setHeaderText("معلومات كاملة عن الطلب");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        String requestDate = request.getRequestDate().toLocalDateTime().format(formatter);
        String approvedDate = request.getApprovedDate() != null ?
                request.getApprovedDate().toLocalDateTime().format(formatter) : "لم يتم";

        String content = """
            رقم الطلب: %d
            العنصر: %s
            الجهاز: %s
            السيريال: %s
            الكمية: %.2f
            مقدم الطلب: %s
            وقت الطلب: %s
            الحالة: %s
            السبب: %s
            رقم القطعة المعيبة: %s
            مسؤول المعالجة: %s
            تم الاعتماد بواسطة: %s
            وقت الاعتماد: %s
            """.formatted(
                request.getRequestId(),
                request.getItemName(),
                request.getDeviceName(),
                request.getSerialNumber(),
                request.getQuantity(),
                request.getRequesterName(),
                requestDate,
                request.getStatus(),
                request.getReason(),
                request.getDefectiveNumber() != null ? request.getDefectiveNumber() : "لا يوجد",
                request.getAssignedToEmployee(),
                request.getApprovedBy() > 0 ? String.valueOf(request.getApprovedBy()) : "لم يتم",
                approvedDate
        );

        detailsAlert.setContentText(content);
        detailsAlert.showAndWait();
    }

    @FXML
    private void onRefreshClicked() {
        loadRequestsData();
        showInfo("تم تحديث البيانات");
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("خطأ");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("تم بنجاح");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    public static class StockRequest {
        private final int requestId;
        private final String itemName;
        private final String deviceName;
        private final String serialNumber;
        private final double quantity;
        private final String requesterName;
        private final Timestamp requestDate;
        private final String status;
        private final String reason;
        private final String defectiveNumber;
        private final String assignedToEmployee;
        private final int approvedBy;
        private final Timestamp approvedDate;
        private final int itemId;
        private final int serialId;

        public StockRequest(int requestId, String itemName, String deviceName, String serialNumber,
                            double quantity, String requesterName, Timestamp requestDate, String status,
                            String reason, String defectiveNumber, String assignedToEmployee,
                            int approvedBy, Timestamp approvedDate, int itemId, int serialId) {
            this.requestId = requestId;
            this.itemName = itemName;
            this.deviceName = deviceName;
            this.serialNumber = serialNumber;
            this.quantity = quantity;
            this.requesterName = requesterName;
            this.requestDate = requestDate;
            this.status = status;
            this.reason = reason;
            this.defectiveNumber = defectiveNumber;
            this.assignedToEmployee = assignedToEmployee;
            this.approvedBy = approvedBy;
            this.approvedDate = approvedDate;
            this.itemId = itemId;
            this.serialId = serialId;
        }

        public int getRequestId() { return requestId; }
        public String getItemName() { return itemName; }
        public String getDeviceName() { return deviceName; }
        public String getSerialNumber() { return serialNumber; }
        public double getQuantity() { return quantity; }
        public String getRequesterName() { return requesterName; }
        public Timestamp getRequestDate() { return requestDate; }
        public String getStatus() { return status; }
        public String getReason() { return reason; }
        public String getDefectiveNumber() { return defectiveNumber; }
        public String getAssignedToEmployee() { return assignedToEmployee; }
        public int getApprovedBy() { return approvedBy; }
        public Timestamp getApprovedDate() { return approvedDate; }
        public int getItemId() { return itemId; }
        public int getSerialId() { return serialId; }
    }
}