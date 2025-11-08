package app.controllers;

import app.db.DatabaseConnection;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import java.io.FileOutputStream;
import java.nio.file.Paths;
import java.util.Properties;

public class DBConfigDialog {

    public static boolean showConfigDialog() {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Database Configuration");
        dialog.setHeaderText("Configure Database Connection");

        // Buttons
        ButtonType connectButtonType = new ButtonType("Connect", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(connectButtonType, ButtonType.CANCEL);

        // Form
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

        // status / progress area
        ProgressIndicator progress = new ProgressIndicator();
        progress.setVisible(false);
        Label statusLabel = new Label();
        statusLabel.setWrapText(true);
        HBox statusBox = new HBox(10, progress, statusLabel);
        grid.add(statusBox, 0, 6, 2, 1);

        dialog.getDialogPane().setContent(grid);

        // make "Connect" button behave by running background task
        Button connectButton = (Button) dialog.getDialogPane().lookupButton(connectButtonType);

        connectButton.setOnAction(evt -> {
            // disable UI while testing
            connectButton.setDisable(true);
            dialog.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(true);
            progress.setVisible(true);
            statusLabel.setText("Testing connection...");

            String host = hostField.getText().trim();
            String port = portField.getText().trim();
            String inventoryDb = inventoryDbField.getText().trim();
            String managementDb = managementDbField.getText().trim();
            String user = userField.getText().trim();
            String password = passwordField.getText();

            Task<Boolean> task = new Task<>() {
                @Override
                protected Boolean call() {
                    try {
                        // small validation
                        if (host.isEmpty()) {
                            updateMessage("Host is empty");
                            return false;
                        }
                        if (port.isEmpty()) {
                            updateMessage("Port is empty");
                            return false;
                        }

                        updateMessage("Attempting to connect to inventory DB...");
                        // Use a short login timeout to fail fast if unreachable
                        DriverManagerLoginTimeout.set(5);

                        String url = buildUrl(host, port, inventoryDb);
                        try (java.sql.Connection testConn =
                                     java.sql.DriverManager.getConnection(url, user, password)) {
                            if (!testConn.isValid(3)) {
                                updateMessage("Connection established but not valid.");
                                return false;
                            }
                        }

                        updateMessage("Connection successful. Saving configuration...");

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

                        // Tell DatabaseConnection to reload config and close old connections
                        DatabaseConnection.reloadConfig();

                        updateMessage("Configuration saved and applied.");
                        return true;
                    } catch (Exception ex) {
                        updateMessage("Failed: " + ex.getMessage());
                        ex.printStackTrace();
                        return false;
                    }
                }
            };

            // bind message
            statusLabel.textProperty().bind(task.messageProperty());

            task.setOnSucceeded(ev -> {
                boolean ok = task.getValue();
                progress.setVisible(false);
                statusLabel.textProperty().unbind();
                if (ok) {
                    statusLabel.setText("Connected and configuration saved.");
                    dialog.setResult(Boolean.TRUE);
                    dialog.close();
                } else {
                    connectButton.setDisable(false);
                    dialog.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(false);
                    showAlert("Connection Failed", statusLabel.getText());
                }
            });

            task.setOnFailed(ev -> {
                Throwable ex = task.getException();
                progress.setVisible(false);
                statusLabel.textProperty().unbind();
                connectButton.setDisable(false);
                dialog.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(false);
                String msg = ex != null ? ex.getMessage() : "Unknown error";
                showAlert("Connection Error", msg);
            });

            // run task in background
            Thread t = new Thread(task, "DBConfig-Test-Thread");
            t.setDaemon(true);
            t.start();
        });

        // When user cancels, return false
        dialog.setResultConverter(button -> {
            if (button == ButtonType.CANCEL) return false;
            // If the connect workflow succeeded it already closed the dialog
            // otherwise we return false here (no-op)
            return false;
        });

        // Show and wait — caller receives true only when we explicitly closed dialog with success
        Boolean result = dialog.showAndWait().orElse(false);
        return result;
    }

    private static String buildUrl(String host, String port, String dbName) {
        return "jdbc:sqlserver://" + host + ":" + port +
                ";databaseName=" + dbName + ";encrypt=false;loginTimeout=5;";
    }

    private static void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // Helper to set DriverManager login timeout in a thread-safe way
    private static class DriverManagerLoginTimeout {
        static void set(int seconds) {
            try {
                java.sql.DriverManager.setLoginTimeout(seconds);
            } catch (Exception ignored) { }
        }
    }
}
