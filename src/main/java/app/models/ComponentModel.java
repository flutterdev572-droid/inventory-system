package app.models;

import javafx.scene.control.Button;

public class ComponentModel {
    private int id;
    private String itemName;
    private String itemCode; // أضفنا خاصية كود الصنف
    private double quantity;
    private Button editButton;
    private Button deleteButton;

    // الكونستركتور المعدل
    public ComponentModel(int id, String itemName, String itemCode, double quantity, Button editButton, Button deleteButton) {
        this.id = id;
        this.itemName = itemName;
        this.itemCode = itemCode; // تهيئة كود الصنف
        this.quantity = quantity;
        this.editButton = editButton;
        this.deleteButton = deleteButton;
    }

    // الكونستركتور القديم (للتوافق مع الكود الحالي)
    public ComponentModel(int id, String itemName, double quantity, Button editButton, Button deleteButton) {
        this(id, itemName, "", quantity, editButton, deleteButton);
    }

    // الجيترات
    public int getId() { return id; }
    public String getItemName() { return itemName; }
    public String getItemCode() { return itemCode; } // جيتر جديد لكود الصنف
    public double getQuantity() { return quantity; }
    public Button getEditButton() { return editButton; }
    public Button getDeleteButton() { return deleteButton; }

    // السيترات (اختياري حسب احتياجك)
    public void setId(int id) { this.id = id; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; } // سيتر جديد
    public void setQuantity(double quantity) { this.quantity = quantity; }
    public void setEditButton(Button editButton) { this.editButton = editButton; }
    public void setDeleteButton(Button deleteButton) { this.deleteButton = deleteButton; }
}