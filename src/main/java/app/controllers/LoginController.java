package app.controllers;

import app.current_user.CurrentUser;
import app.db.DatabaseConnection;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.prefs.Preferences;

public class LoginController {

    @FXML private TextField txtEmployeeId;
    @FXML private PasswordField txtPassword;
    @FXML private TextField txtPasswordVisible;
    @FXML private Button btnTogglePassword;
    @FXML private ImageView eyeIcon;
    @FXML private Label lblError;
    @FXML private ProgressIndicator loginLoading;

    private boolean passwordVisible = false;
    private final Image eyeOpenImage = new Image(getClass().getResourceAsStream("/images/show.png"));
    private final Image eyeClosedImage = new Image(getClass().getResourceAsStream("/images/hide.png"));
    private static final String PREFS_NODE = "app_login";
    private static final String LAST_ID_KEY = "last_employee_id";

    @FXML
    private void initialize() {
        // Bind password text between both fields
        txtPasswordVisible.textProperty().bindBidirectional(txtPassword.textProperty());
        txtPasswordVisible.setVisible(false);

        // Set up eye icon button
        setupPasswordToggle();

        // Check DB config at first launch
        Platform.runLater(this::checkDbConnection);

        int lastId = getLastEmployeeId();
        if (lastId != -1) {
            txtEmployeeId.setText(String.valueOf(lastId));
        }
    }

    private void saveLastEmployeeId(int employeeId) {
        Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
        prefs.putInt(LAST_ID_KEY, employeeId);
    }

    private int getLastEmployeeId() {
        Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
        return prefs.getInt(LAST_ID_KEY, -1); // -1 if not found
    }

    private void setupPasswordToggle() {
        updateEyeIcon();
        btnTogglePassword.setOnAction(e -> togglePasswordVisibility());
    }

    private void togglePasswordVisibility() {
        passwordVisible = !passwordVisible;
        if (passwordVisible) {
            txtPassword.setVisible(false);
            txtPasswordVisible.setVisible(true);
            txtPasswordVisible.requestFocus();
            txtPasswordVisible.positionCaret(txtPasswordVisible.getText().length());
        } else {
            txtPasswordVisible.setVisible(false);
            txtPassword.setVisible(true);
            txtPassword.requestFocus();
            txtPassword.positionCaret(txtPassword.getText().length());
        }
        updateEyeIcon();
    }

    private void updateEyeIcon() {
        if (passwordVisible) {
            eyeIcon.setImage(eyeOpenImage);
            btnTogglePassword.setTooltip(new Tooltip("Hide password"));
        } else {
            eyeIcon.setImage(eyeClosedImage);
            btnTogglePassword.setTooltip(new Tooltip("Show password"));
        }
    }

    private void checkDbConnection() {
        boolean connected = false;
        try {
            // نختبر الاتصال بداتابيز المخزن علشان الشاشة الرئيسية
            connected = DatabaseConnection.getInventoryConnection() != null;
        } catch (Exception ignored) {}

        if (!connected) {
            // Show DB config dialog
            boolean success = DBConfigDialog.showConfigDialog();
            if (!success) {
                showError("Cannot connect to database. Please check your connection settings.");
            } else {
                lblError.setVisible(false);
            }
        }
    }

    @FXML
    private void handleLogin(ActionEvent event) {
        lblError.setVisible(false);
        loginLoading.setVisible(true);

        new Thread(() -> {
            String idStr = txtEmployeeId.getText().trim();
            String password = txtPassword.getText();

            boolean success = false;
            String name = "";

            if (!idStr.matches("\\d+")) {
                Platform.runLater(() -> {
                    showError("Invalid ID format");
                    loginLoading.setVisible(false);
                });
                return;
            }

            int employeeId = Integer.parseInt(idStr);

            try {
                // هنا بنستخدم داتابيز الإدارة للوجين فقط
                Connection conn = DatabaseConnection.getManagementConnection();
                String sql = "SELECT name, password_hash FROM Employees WHERE employee_id = ?";
                PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.setInt(1, employeeId);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    byte[] storedHash = rs.getBytes("password_hash");
                    if (storedHash != null && verifyPassword(password, storedHash)) {
                        success = true;
                        name = rs.getString("name");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();

                Platform.runLater(() -> {
                    showError("Database connection error. Please check your connection.");
                    loginLoading.setVisible(false);
                });
                return;
            }

            final boolean loginSuccess = success;
            final String employeeName = name;

            Platform.runLater(() -> {
                loginLoading.setVisible(false);
                if (loginSuccess) {
                    saveLastEmployeeId(employeeId);
                    CurrentUser.setId(employeeId);
                    CurrentUser.setName(employeeName);
                    try {
                        loadDashboard(employeeName);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    showError("Wrong ID or Password");
                }
            });
        }).start();
    }

    private boolean verifyPassword(String password, byte[] storedHash) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] inputHash = digest.digest(password.getBytes("UTF-8"));
        if (inputHash.length != storedHash.length) return false;
        for (int i = 0; i < inputHash.length; i++) {
            if (inputHash[i] != storedHash[i]) return false;
        }
        return true;
    }

    private void showError(String msg){
        lblError.setText(msg);
        lblError.setVisible(true);
    }

    private void loadDashboard(String employeeName) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/dashboard.fxml"));
        Parent root = loader.load();

        DashboardController controller = loader.getController();
        controller.setLoggedEmployeeName(employeeName);

        Stage stage = (Stage) txtEmployeeId.getScene().getWindow();
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();

        Scene newScene = new Scene(root);
        stage.setScene(newScene);

        stage.setX(screenBounds.getMinX());
        stage.setY(screenBounds.getMinY());
        stage.setWidth(screenBounds.getWidth());
        stage.setHeight(screenBounds.getHeight());

        stage.setMaximized(true);
        stage.show();
    }
}