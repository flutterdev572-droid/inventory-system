package app.models;

import javafx.scene.control.Button;

public class ComponentModel {
    private int id;
    private String itemName;
    private double quantity;
    private Button editButton;
    private Button deleteButton;

    public ComponentModel(int id, String itemName, double quantity, Button editButton, Button deleteButton) {
        this.id = id;
        this.itemName = itemName;
        this.quantity = quantity;
        this.editButton = editButton;
        this.deleteButton = deleteButton;
    }

    public int getId() { return id; }
    public String getItemName() { return itemName; }
    public double getQuantity() { return quantity; }
    public Button getEditButton() { return editButton; }
    public Button getDeleteButton() { return deleteButton; }
}
