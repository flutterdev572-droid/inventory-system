package app.controllers;

import app.db.DatabaseConnection;
import app.models.Item;
import app.services.ItemDAO;
import app.services.LogService; // أضف هذا الاستيراد
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.Callback;
import java.sql.Connection;
import java.sql.SQLException;

public class AddItemsController {

    // الكود الحالي كما هو...
    @FXML private TextField itemNameField;
    @FXML private ComboBox<String> unitComboBox;
    @FXML private TextField minQuantityField;
    @FXML private TextField initialQuantityField;
    @FXML private TextField searchField;
    @FXML private TableView<Item> itemsTable;
    @FXML private TableColumn<Item, String> colItemName;
    @FXML private TableColumn<Item, String> colUnit;
    @FXML private TableColumn<Item, Double> colQuantity;
    @FXML private TableColumn<Item, Double> colMinQuantity;
    @FXML private TableColumn<Item, Void> colActions;
    @FXML private Label statusLabel;
    @FXML private TextField newUnitField;

    private ItemDAO itemDAO;

    @FXML
    public void initialize() {
        try {
            Connection conn = DatabaseConnection.getConnection();
            itemDAO = new ItemDAO();

            colItemName.setCellValueFactory(new PropertyValueFactory<>("itemName"));
            colUnit.setCellValueFactory(new PropertyValueFactory<>("unitName"));
            colQuantity.setCellValueFactory(new PropertyValueFactory<>("quantity"));
            colMinQuantity.setCellValueFactory(new PropertyValueFactory<>("minQuantity"));

            refreshUnits();
            refreshTable();

            searchField.textProperty().addListener((obs, oldText, newText) -> {
                itemsTable.setItems(itemDAO.searchItems(newText));
            });

            addDeleteButtonToTable();

        } catch (Exception e) {
            statusLabel.setText("❌ Error initializing: " + e.getMessage());
        }
    }

    @FXML
    private void onAddUnit(ActionEvent event) {
        try {
            String newUnit = newUnitField.getText().trim();
            if (newUnit.isEmpty()) {
                statusLabel.setText("⚠️ أدخل اسم الوحدة أولاً.");
                return;
            }

            boolean added = itemDAO.addUnit(newUnit);
            if (added) {
                statusLabel.setText("✅ تم إضافة الوحدة بنجاح!");

                // تسجيل العملية في اللوج
                LogService.addLog("ADD_UNIT", "تم إضافة وحدة جديدة: " + newUnit);

                newUnitField.clear();
                refreshUnits();
                unitComboBox.setValue(newUnit);
            } else {
                statusLabel.setText("⚠️ الوحدة موجودة بالفعل.");
            }

        } catch (Exception e) {
            statusLabel.setText("❌ خطأ أثناء إضافة الوحدة: " + e.getMessage());
        }
    }

    @FXML
    private void onAddItem(ActionEvent event) {
        try {
            String name = itemNameField.getText();
            String unit = unitComboBox.getValue();
            double minQty = Double.parseDouble(minQuantityField.getText());
            double initialQty = initialQuantityField.getText().isEmpty() ?
                    0 : Double.parseDouble(initialQuantityField.getText());

            if (name.isEmpty() || unit == null) {
                statusLabel.setText("⚠️ يرجى إدخال اسم ووحدة الصنف.");
                return;
            }

            boolean added = itemDAO.addItem(name, unit, minQty, initialQty);
            if (added) {
                statusLabel.setText("✅ تم إضافة الصنف بنجاح!");

                // تسجيل العملية في اللوج
                String description = String.format("تم إضافة صنف جديد: %s - الوحدة: %s - الكمية الدنيا: %.2f - الكمية الأولية: %.2f",
                        name, unit, minQty, initialQty);
                LogService.addLog("ADD_ITEM", description);

                refreshTable();
                clearFields();
            } else {
                statusLabel.setText("⚠️ الصنف موجود بالفعل!");
            }

        } catch (Exception e) {
            statusLabel.setText("❌ خطأ: " + e.getMessage());
        }
    }

    private void addDeleteButtonToTable() {
        Callback<TableColumn<Item, Void>, TableCell<Item, Void>> cellFactory = param -> new TableCell<>() {
            private final Button deleteBtn = new Button("🗑️");

            {
                deleteBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5;");
                deleteBtn.setOnAction(event -> {
                    Item item = getTableView().getItems().get(getIndex());
                    deleteItem(item);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(deleteBtn);
                }
            }
        };

        colActions.setCellFactory(cellFactory);
    }

    private void deleteItem(Item item) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("تأكيد الحذف");
        confirm.setHeaderText(null);
        confirm.setContentText("هل أنت متأكد أنك تريد حذف هذا الصنف وجميع البيانات المرتبطة به؟");

        if (confirm.showAndWait().get() == ButtonType.OK) {
            try {
                boolean deleted = itemDAO.deleteItemCompletely(item.getId());
                if (deleted) {
                    statusLabel.setText("✅ تم حذف الصنف وكل البيانات المرتبطة به.");

                    // تسجيل العملية في اللوج
                    String description = String.format("تم حذف الصنف: %s (ID: %d)", item.getItemName(), item.getId());
                    LogService.addLog("DELETE_ITEM", description);

                    refreshTable();
                } else {
                    statusLabel.setText("⚠️ لم يتم العثور على الصنف.");
                }
            } catch (Exception e) {
                statusLabel.setText("❌ خطأ أثناء الحذف: " + e.getMessage());
            }
        }
    }

    private void refreshUnits() {
        try {
            unitComboBox.setItems(FXCollections.observableArrayList(itemDAO.getAllUnits()));
        } catch (SQLException e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("خطأ في تحميل الوحدات");
            alert.setHeaderText(null);
            alert.setContentText("حدث خطأ أثناء تحميل قائمة الوحدات من قاعدة البيانات.");
            alert.showAndWait();
        }
    }

    private void refreshTable() {
        itemsTable.setItems(itemDAO.getAllItems());
    }

    private void clearFields() {
        itemNameField.clear();
        unitComboBox.setValue(null);
        minQuantityField.clear();
        initialQuantityField.clear();
    }
}