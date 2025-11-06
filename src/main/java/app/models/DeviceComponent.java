package app.models;

import javafx.beans.property.*;

public class DeviceComponent {
    private IntegerProperty componentID;
    private StringProperty itemName;
    private DoubleProperty quantityPerDevice;

    public DeviceComponent(int componentID, String itemName, double quantityPerDevice) {
        this.componentID = new SimpleIntegerProperty(componentID);
        this.itemName = new SimpleStringProperty(itemName);
        this.quantityPerDevice = new SimpleDoubleProperty(quantityPerDevice);
    }

    public int getComponentID() { return componentID.get(); }
    public String getItemName() { return itemName.get(); }
    public double getQuantityPerDevice() { return quantityPerDevice.get(); }

    public IntegerProperty componentIDProperty() { return componentID; }
    public StringProperty itemNameProperty() { return itemName; }
    public DoubleProperty quantityPerDeviceProperty() { return quantityPerDevice; }
}
