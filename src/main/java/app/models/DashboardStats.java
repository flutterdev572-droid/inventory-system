package app.models;

public class DashboardStats {
    private int totalItems;
    private int lowStockCount;
    private int totalTransactions;

    public DashboardStats(int totalItems, int lowStockCount, int totalTransactions) {
        this.totalItems = totalItems;
        this.lowStockCount = lowStockCount;
        this.totalTransactions = totalTransactions;
    }

    public int getTotalItems() { return totalItems; }
    public int getLowStockCount() { return lowStockCount; }
    public int getTotalTransactions() { return totalTransactions; }
}
