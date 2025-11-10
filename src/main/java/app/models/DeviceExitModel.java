package app.models;

import javafx.beans.property.*;

public class DeviceExitModel {
    private final StringProperty deviceName;
    private final StringProperty serial;
    private final DoubleProperty finalPrice;
    private final DoubleProperty exceededPrice;
    private final StringProperty exitDate;
    private final StringProperty deliveredBy;
    private final StringProperty deliveredTo;

    public DeviceExitModel(String deviceName, String serial, double finalPrice, double exceededPrice,
                           String exitDate, String deliveredBy, String deliveredTo) {
        this.deviceName = new SimpleStringProperty(deviceName);
        this.serial = new SimpleStringProperty(serial);
        this.finalPrice = new SimpleDoubleProperty(finalPrice);
        this.exceededPrice = new SimpleDoubleProperty(exceededPrice);
        this.exitDate = new SimpleStringProperty(exitDate);
        this.deliveredBy = new SimpleStringProperty(deliveredBy);
        this.deliveredTo = new SimpleStringProperty(deliveredTo);
    }

    public StringProperty deviceNameProperty() { return deviceName; }
    public StringProperty serialProperty() { return serial; }
    public DoubleProperty finalPriceProperty() { return finalPrice; }
    public DoubleProperty exceededPriceProperty() { return exceededPrice; }
    public StringProperty exitDateProperty() { return exitDate; }
    public StringProperty deliveredByProperty() { return deliveredBy; }
    public StringProperty deliveredToProperty() { return deliveredTo; }

    public String getDeviceName() { return deviceName.get(); }
    public String getSerial() { return serial.get(); }
}
