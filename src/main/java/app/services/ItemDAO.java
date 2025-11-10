package app.services;

import app.db.DatabaseConnection;
import app.models.Item;
import app.services.ItemImportDTO;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ItemDAO {

    // ===================================
    // 1️⃣ إضافة صنف جديد (النسخة الأصلية - علشان التوافق)
    // ===================================
    public int addItem(String name, String unitName, double minQty, double initialQty) throws SQLException {
        return addItem(name, "", unitName, minQty, initialQty); // كود فارغ علشان التوافق
    }

    // ===================================
    // 1️⃣ إضافة صنف جديد (النسخة الجديدة مع الكود)
    // ===================================
    public int addItem(String name, String itemCode, String unitName, double minQty, double initialQty) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            int unitId = getUnitIdByName(unitName, conn);
            if (unitId == -1) return -1;

            // البحث إذا كان الصنف موجود بالاسم أو بالكود
            PreparedStatement check = conn.prepareStatement(
                    "SELECT ItemID FROM Items WHERE ItemName=? OR ItemCode=?");
            check.setString(1, name);
            check.setString(2, itemCode);
            ResultSet rs = check.executeQuery();
            if (rs.next()) return -1; // موجود بالفعل

            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO Items (ItemName, ItemCode, UnitID, MinQuantity) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setString(2, itemCode.isEmpty() ? null : itemCode); // لو الكود فارغ نخليه NULL
            ps.setInt(3, unitId);
            ps.setDouble(4, minQty);
            ps.executeUpdate();

            rs = ps.getGeneratedKeys();
            if (rs.next()) {
                int itemId = rs.getInt(1);

                // إضافة الكمية المبدئية
                PreparedStatement bal = conn.prepareStatement(
                        "INSERT INTO StockBalances (ItemID, Quantity) VALUES (?, ?)");
                bal.setInt(1, itemId);
                bal.setDouble(2, initialQty);
                bal.executeUpdate();

                return itemId;
            }
        }
        return -1;
    }

    // ===================================
    // 2️⃣ جلب كل الأصناف (محدث علشان يجيب الكود)
    // ===================================
    public ObservableList<Item> getAllItems() {
        ObservableList<Item> list = FXCollections.observableArrayList();
        try (Connection conn = DatabaseConnection.getConnection()) {
            String query = """
                SELECT i.ItemID, i.ItemName, i.ItemCode, u.UnitName, s.Quantity, i.MinQuantity
                FROM Items i
                JOIN Units u ON i.UnitID = u.UnitID
                JOIN StockBalances s ON i.ItemID = s.ItemID
                ORDER BY i.ItemID DESC
            """;
            PreparedStatement ps = conn.prepareStatement(query);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Item item = new Item(
                        rs.getInt("ItemID"),
                        rs.getString("ItemName"),
                        rs.getString("UnitName"),
                        rs.getDouble("Quantity"),
                        rs.getDouble("MinQuantity")
                );
                item.setItemCode(rs.getString("ItemCode")); // ⬅️ إضافة الكود
                list.add(item);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    // ===================================
    // 3️⃣ بحث بالأسماء والكود (محدث)
    // ===================================
    public ObservableList<Item> searchItems(String keyword) {
        if (keyword == null || keyword.isEmpty()) return getAllItems();
        ObservableList<Item> list = FXCollections.observableArrayList();
        try (Connection conn = DatabaseConnection.getConnection()) {
            String query = """
                SELECT i.ItemID, i.ItemName, i.ItemCode, u.UnitName, s.Quantity, i.MinQuantity
                FROM Items i
                JOIN Units u ON i.UnitID = u.UnitID
                JOIN StockBalances s ON i.ItemID = s.ItemID
                WHERE i.ItemName LIKE ? OR i.ItemCode LIKE ?
            """;
            PreparedStatement ps = conn.prepareStatement(query);
            ps.setString(1, "%" + keyword + "%");
            ps.setString(2, "%" + keyword + "%");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Item item = new Item(
                        rs.getInt("ItemID"),
                        rs.getString("ItemName"),
                        rs.getString("UnitName"),
                        rs.getDouble("Quantity"),
                        rs.getDouble("MinQuantity")
                );
                item.setItemCode(rs.getString("ItemCode")); // ⬅️ إضافة الكود
                list.add(item);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    // ===================================
    // باقي الدوال تفضل كما هي بدون تغيير...
    // ===================================

    // 4️⃣ جلب الوحدات
    public List<String> getAllUnits() throws SQLException {
        List<String> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT UnitName FROM Units ORDER BY UnitName")) {
            while (rs.next()) list.add(rs.getString("UnitName"));
        }
        return list;
    }

    // 5️⃣ إضافة كمية جديدة (IN)
    public void addStock(int itemId, double qty, int employeeId, String notes) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                PreparedStatement update = conn.prepareStatement(
                        "UPDATE StockBalances SET Quantity = Quantity + ? WHERE ItemID=?");
                update.setDouble(1, qty);
                update.setInt(2, itemId);
                update.executeUpdate();

                PreparedStatement log = conn.prepareStatement(
                        "INSERT INTO StockTransactions (ItemID, TransactionType, Quantity, EmployeeID, Notes) VALUES (?, 'IN', ?, ?, ?)");
                log.setInt(1, itemId);
                log.setDouble(2, qty);
                log.setInt(3, employeeId);
                log.setString(4, notes);
                log.executeUpdate();

                updateShortageStatus(itemId, conn);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    // 6️⃣ صرف كمية (OUT)
    public void removeStock(int itemId, double qty, int employeeId, String receiver, String notes) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                PreparedStatement check = conn.prepareStatement("SELECT Quantity FROM StockBalances WHERE ItemID=?");
                check.setInt(1, itemId);
                ResultSet rs = check.executeQuery();
                if (!rs.next() || rs.getDouble("Quantity") < qty) {
                    throw new SQLException("Insufficient stock!");
                }

                PreparedStatement update = conn.prepareStatement(
                        "UPDATE StockBalances SET Quantity = Quantity - ? WHERE ItemID=?");
                update.setDouble(1, qty);
                update.setInt(2, itemId);
                update.executeUpdate();

                PreparedStatement log = conn.prepareStatement(
                        "INSERT INTO StockTransactions (ItemID, TransactionType, Quantity, EmployeeID, ReceiverName, Notes) VALUES (?, 'OUT', ?, ?, ?, ?)");
                log.setInt(1, itemId);
                log.setDouble(2, qty);
                log.setInt(3, employeeId);
                log.setString(4, receiver);
                log.setString(5, notes);
                log.executeUpdate();

                updateShortageStatus(itemId, conn);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    // 7️⃣ جلب المعاملات في يوم معين
    public ObservableList<String> getTransactionsByDate(LocalDate date) {
        ObservableList<String> list = FXCollections.observableArrayList();
        try (Connection conn = DatabaseConnection.getConnection()) {
            PreparedStatement ps = conn.prepareStatement("""
                SELECT t.TransactionID, i.ItemName, t.TransactionType, t.Quantity, t.TransactionDate, t.ReceiverName, t.Notes
                FROM StockTransactions t
                JOIN Items i ON t.ItemID = i.ItemID
                WHERE CAST(t.TransactionDate AS DATE) = ?
                ORDER BY t.TransactionDate DESC
            """);
            ps.setDate(1, Date.valueOf(date));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String row = String.format("[%s] %s - %.2f (%s) %s",
                        rs.getString("TransactionType"),
                        rs.getString("ItemName"),
                        rs.getDouble("Quantity"),
                        rs.getString("TransactionDate"),
                        rs.getString("Notes") == null ? "" : rs.getString("Notes"));
                list.add(row);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    // 🔍 تحديث حالة النواقص
    private void updateShortageStatus(int itemId, Connection conn) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("""
            SELECT s.Quantity, i.MinQuantity
            FROM StockBalances s
            JOIN Items i ON s.ItemID = i.ItemID
            WHERE s.ItemID = ?
        """);
        ps.setInt(1, itemId);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            double qty = rs.getDouble("Quantity");
            double minQty = rs.getDouble("MinQuantity");
            if (qty < minQty) {
                PreparedStatement ins = conn.prepareStatement("""
                    MERGE ShortageItems AS target
                    USING (SELECT ? AS ItemID, ? AS CurrentQuantity, ? AS MinQuantity) AS src
                    ON target.ItemID = src.ItemID
                    WHEN MATCHED THEN UPDATE SET CurrentQuantity = src.CurrentQuantity, MinQuantity = src.MinQuantity, DetectedAt = GETDATE()
                    WHEN NOT MATCHED THEN INSERT (ItemID, CurrentQuantity, MinQuantity) VALUES (src.ItemID, src.CurrentQuantity, src.MinQuantity);
                """);
                ins.setInt(1, itemId);
                ins.setDouble(2, qty);
                ins.setDouble(3, minQty);
                ins.executeUpdate();
            } else {
                PreparedStatement del = conn.prepareStatement("DELETE FROM ShortageItems WHERE ItemID=?");
                del.setInt(1, itemId);
                del.executeUpdate();
            }
        }
    }

    // 8️⃣ حذف الصنف بالكامل
    public boolean deleteItemCompletely(int itemId) {
        String[] queries = {
                "DELETE FROM StockTransactions WHERE ItemID = ?",
                "DELETE FROM StockBalances WHERE ItemID = ?",
                "DELETE FROM ShortageItems WHERE ItemID = ?",
                "DELETE FROM Items WHERE ItemID = ?"
        };

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            for (String sql : queries) {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, itemId);
                    ps.executeUpdate();
                }
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // 🔹 مساعد: جلب ID الوحدة بالاسم
    private int getUnitIdByName(String name, Connection conn) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("SELECT UnitID FROM Units WHERE UnitName=?");
        ps.setString(1, name);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) return rs.getInt("UnitID");
        return -1;
    }

    // Add unit
    public boolean addUnit(String unitName) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "IF NOT EXISTS (SELECT 1 FROM Units WHERE UnitName = ?) " +
                    "INSERT INTO Units (UnitName) VALUES (?)";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, unitName);
            stmt.setString(2, unitName);
            int rows = stmt.executeUpdate();
            return rows > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // Add Price
    public void addItemPrice(int itemId, double price) throws SQLException {
        String query = "INSERT INTO ItemPrices (ItemID, UnitPrice, CreatedBy) VALUES (?, ?, NULL)";
        try (Connection conn = DatabaseConnection.getConnection();
             var stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, itemId);
            stmt.setDouble(2, price);
            stmt.executeUpdate();
        }
    }

    // ===================================
// 9️⃣ استيراد الأصناف من Excel
// ===================================
// ===================================
// 9️⃣ استيراد الأصناف من Excel
// ===================================
    public String importItemsFromExcel(List<ItemImportDTO> items) {
        StringBuilder result = new StringBuilder();
        int successCount = 0;
        int errorCount = 0;

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false); // نبدأ transaction

            for (ItemImportDTO item : items) {
                try {
                    // التحقق من البيانات المطلوبة
                    if (item.getItemName() == null || item.getItemName().trim().isEmpty() ||
                            item.getUnitName() == null || item.getUnitName().trim().isEmpty()) {
                        result.append("❌ خطأ: بيانات ناقصة للصنف: ").append(item.getItemName()).append("\n");
                        errorCount++;
                        continue;
                    }

                    // التحقق من وجود الوحدة
                    int unitId = getUnitIdByName(item.getUnitName().trim(), conn);
                    if (unitId == -1) {
                        result.append("❌ خطأ: الوحدة غير موجودة '").append(item.getUnitName())
                                .append("' للصنف: ").append(item.getItemName()).append("\n");
                        errorCount++;
                        continue;
                    }

                    // التحقق من عدم تكرار اسم الصنف أو الكود
                    PreparedStatement check = conn.prepareStatement(
                            "SELECT ItemID FROM Items WHERE ItemName=? OR (ItemCode IS NOT NULL AND ItemCode=?)");
                    check.setString(1, item.getItemName().trim());
                    check.setString(2, item.getItemCode() != null ? item.getItemCode().trim() : "");
                    ResultSet rs = check.executeQuery();
                    if (rs.next()) {
                        result.append("⚠️ تحذير: الصنف موجود مسبقاً '").append(item.getItemName())
                                .append("' أو الكود '").append(item.getItemCode()).append("'\n");
                        errorCount++;
                        continue;
                    }

                    // إضافة الصنف
                    PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO Items (ItemName, ItemCode, UnitID, MinQuantity) VALUES (?, ?, ?, ?)",
                            Statement.RETURN_GENERATED_KEYS);
                    ps.setString(1, item.getItemName().trim());
                    ps.setString(2, (item.getItemCode() != null && !item.getItemCode().trim().isEmpty()) ?
                            item.getItemCode().trim() : null);
                    ps.setInt(3, unitId);
                    ps.setDouble(4, item.getMinQuantity());
                    ps.executeUpdate();

                    // جلب الـ ID المُنشأ
                    rs = ps.getGeneratedKeys();
                    if (rs.next()) {
                        int itemId = rs.getInt(1);

                        // إضافة الكمية الأولية
                        double initialQty = item.getInitialQuantity() != null ? item.getInitialQuantity() : 0;
                        PreparedStatement bal = conn.prepareStatement(
                                "INSERT INTO StockBalances (ItemID, Quantity) VALUES (?, ?)");
                        bal.setInt(1, itemId);
                        bal.setDouble(2, initialQty);
                        bal.executeUpdate();

                        // إضافة السعر إذا كان موجود
                        if (item.getPrice() != null && item.getPrice() > 0) {
                            PreparedStatement priceStmt = conn.prepareStatement(
                                    "INSERT INTO ItemPrices (ItemID, UnitPrice, CreatedBy) VALUES (?, ?, NULL)");
                            priceStmt.setInt(1, itemId);
                            priceStmt.setDouble(2, item.getPrice());
                            priceStmt.executeUpdate();
                        }

                        result.append("✅ تم إضافة: ").append(item.getItemName())
                                .append(item.getItemCode() != null ? " - كود: " + item.getItemCode() : "")
                                .append("\n");
                        successCount++;
                    }

                } catch (SQLException e) {
                    result.append("❌ خطأ في: ").append(item.getItemName())
                            .append(" - ").append(e.getMessage()).append("\n");
                    errorCount++;
                }
            }

            conn.commit(); // نعمل commit للـ transaction
            result.append("\n📊 ملخص: ").append(successCount).append(" نجاح, ")
                    .append(errorCount).append(" فشل\n");

        } catch (SQLException e) {
            result.append("❌ خطأ عام في الاتصال: ").append(e.getMessage());
        }

        return result.toString();
    }
}