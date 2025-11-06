package app.models;

import javafx.beans.property.*;

public class Item {
    private final IntegerProperty id;
    private final StringProperty itemName;
    private final StringProperty unitName;
    private final DoubleProperty quantity;
    private final DoubleProperty minQuantity;

    public Item(int id, String name, String unit, double qty, double minQty) {
        this.id = new SimpleIntegerProperty(id);
        this.itemName = new SimpleStringProperty(name);
        this.unitName = new SimpleStringProperty(unit);
        this.quantity = new SimpleDoubleProperty(qty);
        this.minQuantity = new SimpleDoubleProperty(minQty);
    }

    // ---- Properties (for TableView) ----
    public IntegerProperty idProperty() { return id; }
    public StringProperty itemNameProperty() { return itemName; }
    public StringProperty unitNameProperty() { return unitName; }
    public DoubleProperty quantityProperty() { return quantity; }
    public DoubleProperty minQuantityProperty() { return minQuantity; }

    // ---- Getters ----
    public int getItemID() { return id.get(); }
    public String getItemName() { return itemName.get(); }
    public String getUnitName() { return unitName.get(); }
    public double getQuantity() { return quantity.get(); }
    public double getMinQuantity() { return minQuantity.get(); }
    public int getId() {
        return id.get();
    }

    // ---- Setters (optional for updating data) ----
    public void setQuantity(double newQty) { this.quantity.set(newQty); }
}
