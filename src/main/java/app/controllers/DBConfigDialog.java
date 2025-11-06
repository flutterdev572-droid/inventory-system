package app.controllers;

import app.db.DatabaseConnection;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.io.FileOutputStream;
import java.util.Properties;
import java.nio.file.Paths;

public class DBConfigDialog {

    public static boolean showConfigDialog() {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Database Configuration");
        dialog.setHeaderText("Configure Database Connection");

        // Set the button types
        ButtonType connectButtonType = new ButtonType("Connect", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(connectButtonType, ButtonType.CANCEL);

        // Create the configuration form
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField hostField = new TextField();
        hostField.setPromptText("localhost");
        TextField portField = new TextField();
        portField.setPromptText("1433");
        TextField inventoryDbField = new TextField();
        inventoryDbField.setText("Inventory_DB");
        TextField managementDbField = new TextField();
        managementDbField.setText("Chemtech_management");
        TextField userField = new TextField();
        userField.setPromptText("sa");
        PasswordField passwordField = new PasswordField();

        grid.add(new Label("Host:"), 0, 0);
        grid.add(hostField, 1, 0);
        grid.add(new Label("Port:"), 0, 1);
        grid.add(portField, 1, 1);
        grid.add(new Label("Inventory DB:"), 0, 2);
        grid.add(inventoryDbField, 1, 2);
        grid.add(new Label("Management DB:"), 0, 3);
        grid.add(managementDbField, 1, 3);
        grid.add(new Label("Username:"), 0, 4);
        grid.add(userField, 1, 4);
        grid.add(new Label("Password:"), 0, 5);
        grid.add(passwordField, 1, 5);

        dialog.getDialogPane().setContent(grid);

        // Convert the result to a boolean when the connect button is clicked
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == connectButtonType) {
                return testAndSaveConnection(
                        hostField.getText(),
                        portField.getText(),
                        inventoryDbField.getText(),
                        managementDbField.getText(),
                        userField.getText(),
                        passwordField.getText()
                );
            }
            return false;
        });

        // Show dialog and wait for result
        return dialog.showAndWait().orElse(false);
    }

    private static boolean testAndSaveConnection(String host, String port, String inventoryDb,
                                                 String managementDb, String user, String password) {
        try {
            // Test connection
            String url = "jdbc:sqlserver://" + host + ":" + port +
                    ";databaseName=" + inventoryDb + ";encrypt=false;";

            java.sql.Connection testConn = java.sql.DriverManager.getConnection(url, user, password);
            testConn.close();

            // Save configuration
            Properties props = new Properties();
            props.setProperty("host", host);
            props.setProperty("port", port);
            props.setProperty("inventory_db_name", inventoryDb);
            props.setProperty("management_db_name", managementDb);
            props.setProperty("user", user);
            props.setProperty("password", password);

            String configFile = Paths.get(System.getProperty("user.home"), "warehouse_db_config.properties").toString();
            try (FileOutputStream fos = new FileOutputStream(configFile)) {
                props.store(fos, "Database Configuration");
            }

            // Reload configuration in DatabaseConnection
            java.lang.reflect.Method loadConfig = DatabaseConnection.class.getDeclaredMethod("loadConfig");
            loadConfig.setAccessible(true);
            loadConfig.invoke(null);

            return true;
        } catch (Exception e) {
            showAlert("Connection Failed", "Cannot connect to database: " + e.getMessage());
            return false;
        }
    }

    private static void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}