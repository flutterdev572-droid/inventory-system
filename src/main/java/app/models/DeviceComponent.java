// DeviceComponent.java
package app.models;

import javafx.beans.property.*;

public class DeviceComponent {
    private IntegerProperty componentID;
    private StringProperty itemName;
    private StringProperty itemCode; // أضفنا خاصية كود الصنف
    private DoubleProperty quantityPerDevice;

    public DeviceComponent(int componentID, String itemName, String itemCode, double quantityPerDevice) {
        this.componentID = new SimpleIntegerProperty(componentID);
        this.itemName = new SimpleStringProperty(itemName);
        this.itemCode = new SimpleStringProperty(itemCode); // تهيئة كود الصنف
        this.quantityPerDevice = new SimpleDoubleProperty(quantityPerDevice);
    }

    public int getComponentID() { return componentID.get(); }
    public String getItemName() { return itemName.get(); }
    public String getItemCode() { return itemCode.get(); } // دالة الحصول على الكود
    public double getQuantityPerDevice() { return quantityPerDevice.get(); }

    public IntegerProperty componentIDProperty() { return componentID; }
    public StringProperty itemNameProperty() { return itemName; }
    public StringProperty itemCodeProperty() { return itemCode; } // خاصية الكود
    public DoubleProperty quantityPerDeviceProperty() { return quantityPerDevice; }
}