package app.models;

import javafx.scene.control.Button;

public class DeviceModel {
    private int deviceID;
    private String deviceName;
    private String serial;
    private Button editButton;
    private Button componentsButton;
    private Button deleteButton;

    public DeviceModel(int deviceID, String deviceName, String serial,
                       Button editButton, Button componentsButton, Button deleteButton) {
        this.deviceID = deviceID;
        this.deviceName = deviceName;
        this.serial = serial;
        this.editButton = editButton;
        this.componentsButton = componentsButton;
        this.deleteButton = deleteButton;
    }

    public int getDeviceID() { return deviceID; }
    public String getDeviceName() { return deviceName; }
    public String getSerial() { return serial; }
    public Button getEditButton() { return editButton; }
    public Button getComponentsButton() { return componentsButton; }
    public Button getDeleteButton() { return deleteButton; }
}
