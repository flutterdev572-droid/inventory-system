package app.services;

public class ItemImportDTO {
    private String itemCode;
    private String itemName;
    private String unitName;
    private double minQuantity;
    private Double initialQuantity;
    private Double price;

    public ItemImportDTO() {}

    public ItemImportDTO(String itemCode, String itemName, String unitName,
                         double minQuantity, Double initialQuantity, Double price) {
        this.itemCode = itemCode;
        this.itemName = itemName;
        this.unitName = unitName;
        this.minQuantity = minQuantity;
        this.initialQuantity = initialQuantity;
        this.price = price;
    }

    // Getters and Setters
    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getUnitName() { return unitName; }
    public void setUnitName(String unitName) { this.unitName = unitName; }

    public double getMinQuantity() { return minQuantity; }
    public void setMinQuantity(double minQuantity) { this.minQuantity = minQuantity; }

    public Double getInitialQuantity() { return initialQuantity; }
    public void setInitialQuantity(Double initialQuantity) { this.initialQuantity = initialQuantity; }

    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
}