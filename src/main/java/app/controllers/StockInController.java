package app.controllers;

import app.current_user.CurrentUser;
import app.db.DatabaseConnection;
import app.models.Item;
import app.services.ItemDAO;
import app.services.LogService; // أضف هذا الاستيراد
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.sql.Connection;

public class StockInController {

    @FXML private ComboBox<Item> itemCombo;
    @FXML private TextField quantityField;
    @FXML private TextField notesField;
    @FXML private Label statusLabel;

    private ItemDAO itemDAO;
    private Connection conn;

    @FXML
    public void initialize() {
        try {
            conn = DatabaseConnection.getConnection();
            itemDAO = new ItemDAO();

            // تحميل الأصناف
            ObservableList<Item> items = itemDAO.getAllItems();
            itemCombo.setItems(items);

            // عرض اسم الصنف بدل الكائن
            itemCombo.setCellFactory(param -> new ListCell<>() {
                @Override
                protected void updateItem(Item item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getItemName());
                }
            });
            itemCombo.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(Item item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getItemName());
                }
            });

        } catch (Exception e) {
            showError("Database error: " + e.getMessage());
        }
    }

    @FXML
    private void onAddClicked() {
        Item selected = itemCombo.getValue();
        if (selected == null) {
            showError("من فضلك اختر صنفًا أولًا.");
            return;
        }

        double qty;
        try {
            qty = Double.parseDouble(quantityField.getText());
            if (qty <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            showError("الكمية يجب أن تكون رقمًا أكبر من صفر.");
            return;
        }

        String notes = notesField.getText();

        try {
            // ✅ إصلاح: استخدام CurrentUser.getId() بدلاً من رقم ثابت
            itemDAO.addStock(selected.getItemID(), qty, CurrentUser.getId(), notes);

            // ✅ تسجيل العملية في اللوج مع وصف واضح
            String description = String.format("إدخال كمية: تم إضافة %.2f وحدة من الصنف '%s' - الملاحظات: %s",
                    qty, selected.getItemName(), notes.isEmpty() ? "لا توجد" : notes);
            LogService.addLog("STOCK_IN", description);

            showSuccess("تمت إضافة الكمية بنجاح ✅");
            clearForm();

        } catch (Exception e) {
            showError("خطأ أثناء الإضافة: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private void clearForm() {
        quantityField.clear();
        notesField.clear();
        itemCombo.getSelectionModel().clearSelection();
    }

    private void showError(String msg) {
        statusLabel.setText(msg);
        statusLabel.setStyle("-fx-text-fill: red;");
    }

    private void showSuccess(String msg) {
        statusLabel.setText(msg);
        statusLabel.setStyle("-fx-text-fill: green;");
    }

    @FXML
    private void onCancelClicked() {
        clearForm();
        statusLabel.setText("");
    }
}