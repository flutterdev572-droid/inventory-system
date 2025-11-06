package app.models;

import javafx.beans.property.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Device {
    private IntegerProperty deviceID;
    private StringProperty deviceName;
    private StringProperty createdAt;

    public Device(int deviceID, String deviceName, LocalDateTime createdAt) {
        this.deviceID = new SimpleIntegerProperty(deviceID);
        this.deviceName = new SimpleStringProperty(deviceName);
        this.createdAt = new SimpleStringProperty(createdAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
    }

    public int getDeviceID() { return deviceID.get(); }
    public String getDeviceName() { return deviceName.get(); }
    public String getCreatedAt() { return createdAt.get(); }

    public IntegerProperty deviceIDProperty() { return deviceID; }
    public StringProperty deviceNameProperty() { return deviceName; }
    public StringProperty createdAtProperty() { return createdAt; }
}
