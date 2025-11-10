package app.controllers;

import app.db.DatabaseConnection;
import app.models.Item;
import app.services.ItemImportDTO;
import app.services.ItemDAO;
import app.services.LogService; // أضف هذا الاستيراد
import app.utils.ExcelReader;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Callback;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class AddItemsController {

    @FXML private TextField itemNameField;
    @FXML private TextField itemCodeField; // ⬅️ ده الحقل الجديد

    @FXML private ComboBox<String> unitComboBox;
    @FXML private TextField minQuantityField;
    @FXML private TextField initialQuantityField;
    @FXML private TextField searchField;
    @FXML private TableView<Item> itemsTable;
    @FXML private TableColumn<Item, String> colItemCode; // ⬅️ العمود الجديد

    @FXML private TableColumn<Item, String> colItemName;
    @FXML private TableColumn<Item, String> colUnit;
    @FXML private TableColumn<Item, Double> colQuantity;
    @FXML private TableColumn<Item, Double> colMinQuantity;
    @FXML private TableColumn<Item, Void> colActions;
    @FXML private Label statusLabel;
    @FXML private TextField newUnitField;
    @FXML private TextField priceField;

    private ItemDAO itemDAO;

    @FXML
    public void initialize() {
        try {
            Connection conn = DatabaseConnection.getConnection();
            itemDAO = new ItemDAO();
            colItemCode.setCellValueFactory(new PropertyValueFactory<>("itemCode")); // ⬅️ العمود الجديد
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
    private void onImportExcel(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("اختر ملف Excel");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls")
        );

        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            // إنشاء دايلوج اللودنج
            Dialog<Void> loadingDialog = new Dialog<>();
            loadingDialog.setTitle("جاري الاستيراد");
            loadingDialog.setHeaderText("جاري استيراد البيانات من ملف Excel...");
            loadingDialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);

            // إنشاء محتوى اللودنج
            ProgressBar progressBar = new ProgressBar();
            progressBar.setPrefWidth(300);
            progressBar.setProgress(-1); // indeterminate progress

            Label progressLabel = new Label("يتم الآن معالجة البيانات، الرجاء الانتظار...");
            progressLabel.setStyle("-fx-padding: 10;");

            VBox content = new VBox(10, progressBar, progressLabel);
            content.setAlignment(Pos.CENTER);
            content.setPadding(new Insets(20));

            loadingDialog.getDialogPane().setContent(content);

            // تعطيل زر الإغلاق أثناء التحميل
            loadingDialog.setResultConverter(dialogButton -> {
                if (dialogButton == ButtonType.CANCEL) {
                    // يمكنك إضافة منطق للإلغاء إذا أردت
                    return null;
                }
                return null;
            });

            // تشغيل عملية الاستيراد في thread منفصل
            Task<String> importTask = new Task<String>() {
                @Override
                protected String call() throws Exception {
                    try {
                        // قراءة البيانات من Excel
                        List<ItemImportDTO> items = ExcelReader.readItemsFromExcel(file);

                        if (items.isEmpty()) {
                            return "⚠️ لم يتم العثور على بيانات صالحة في الملف";
                        }

                        // تحديث حالة التقدم
                        updateMessage("جاري استيراد " + items.size() + " صنف...");

                        // استيراد البيانات
                        String result = itemDAO.importItemsFromExcel(items);

                        // تسجيل العملية
                        LogService.addLog("IMPORT_ITEMS",
                                "تم استيراد " + items.size() + " صنف من ملف Excel: " + file.getName());

                        return result;
                    } catch (Exception e) {
                        return "❌ خطأ في استيراد الملف: " + e.getMessage();
                    }
                }
            };

            // ربط التحديثات مع الـ UI
            importTask.messageProperty().addListener((obs, oldMsg, newMsg) -> {
                if (newMsg != null) {
                    progressLabel.setText(newMsg);
                }
            });

            // عندما تكتمل المهمة
            importTask.setOnSucceeded(e -> {
                loadingDialog.close();
                String result = importTask.getValue();

                // عرض النتيجة
                showImportResult(result, file.getName());

                // تحديث الجدول
                refreshTable();
            });

            // عندما تفشل المهمة
            importTask.setOnFailed(e -> {
                loadingDialog.close();
                String errorMessage = "❌ فشل في استيراد الملف: " + importTask.getException().getMessage();
                statusLabel.setText(errorMessage);

                Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                errorAlert.setTitle("خطأ في الاستيراد");
                errorAlert.setHeaderText("حدث خطأ أثناء الاستيراد");
                errorAlert.setContentText(errorMessage);
                errorAlert.showAndWait();
            });

            // بدء المهمة في thread منفصل
            Thread importThread = new Thread(importTask);
            importThread.setDaemon(true);
            importThread.start();

            // عرض دايلوج اللودنج
            loadingDialog.show();
        }
    }

    // دالة مساعدة لعرض نتيجة الاستيراد
    private void showImportResult(String result, String fileName) {
        TextArea textArea = new TextArea(result);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 12px;");

        ScrollPane scrollPane = new ScrollPane(textArea);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefSize(600, 400);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("نتيجة الاستيراد");
        alert.setHeaderText("تم الانتهاء من استيراد الملف: " + fileName);
        alert.getDialogPane().setContent(scrollPane);

        // جعل النافذة قابلة للتغيير في الحجم
        alert.getDialogPane().setPrefSize(650, 450);
        alert.setResizable(true);

        alert.showAndWait();
    }    @FXML
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
            String itemCode = itemCodeField.getText().trim(); // ⬅️ الكود الجديد
            String unit = unitComboBox.getValue();
            double minQty = Double.parseDouble(minQuantityField.getText());
            double initialQty = initialQuantityField.getText().isEmpty() ?
                    0 : Double.parseDouble(initialQuantityField.getText());
            Double price = priceField.getText().isEmpty() ?
                    null : Double.parseDouble(priceField.getText());

            if (name.isEmpty() || unit == null) {
                statusLabel.setText("⚠️ يرجى إدخال اسم ووحدة الصنف.");
                return;
            }

            // ⬅️ إضافة الكود كباراميتر
            int itemId = itemDAO.addItem(name, itemCode, unit, minQty, initialQty);
            if (itemId > 0) {
                // لو فيه سعر، نحفظه في جدول ItemPrices
                if (price != null) {
                    itemDAO.addItemPrice(itemId, price);
                }

                statusLabel.setText("✅ تم إضافة الصنف بنجاح!");

                String description = String.format(
                        "تم إضافة صنف جديد: %s - الكود: %s - الوحدة: %s - الكمية الدنيا: %.2f - الكمية الأولية: %.2f - السعر: %s",
                        name, itemCode.isEmpty() ? "بدون كود" : itemCode, unit, minQty, initialQty,
                        price == null ? "بدون سعر" : String.format("%.2f", price)
                );
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
        itemCodeField.clear(); // ⬅️ تنظيف حقل الكود
        unitComboBox.setValue(null);
        minQuantityField.clear();
        initialQuantityField.clear();
        priceField.clear();
    }
}