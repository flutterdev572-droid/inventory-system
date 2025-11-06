package app.services;

import app.db.DatabaseConnection;
import app.models.Item;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ItemDAO {

    // ===================================
    // 1️⃣ إضافة صنف جديد
    // ===================================
    public boolean addItem(String name, String unitName, double minQty, double initialQty) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            int unitId = getUnitIdByName(unitName, conn);
            if (unitId == -1) return false;

            PreparedStatement check = conn.prepareStatement("SELECT COUNT(*) FROM Items WHERE ItemName=?");
            check.setString(1, name);
            ResultSet rs = check.executeQuery();
            rs.next();
            if (rs.getInt(1) > 0) return false;

            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO Items (ItemName, UnitID, MinQuantity) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setInt(2, unitId);
            ps.setDouble(3, minQty);
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                int itemId = keys.getInt(1);
                PreparedStatement bal = conn.prepareStatement(
                        "INSERT INTO StockBalances (ItemID, Quantity) VALUES (?, ?)");
                bal.setInt(1, itemId);
                bal.setDouble(2, initialQty);
                bal.executeUpdate();
            }

            return true;
        }
    }

    // ===================================
    // 2️⃣ جلب كل الأصناف
    // ===================================
    public ObservableList<Item> getAllItems() {
        ObservableList<Item> list = FXCollections.observableArrayList();
        try (Connection conn = DatabaseConnection.getConnection()) {
            String query = """
                SELECT i.ItemID, i.ItemName, u.UnitName, s.Quantity, i.MinQuantity
                FROM Items i
                JOIN Units u ON i.UnitID = u.UnitID
                JOIN StockBalances s ON i.ItemID = s.ItemID
                ORDER BY i.ItemID DESC
            """;
            PreparedStatement ps = conn.prepareStatement(query);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                list.add(new Item(
                        rs.getInt("ItemID"),
                        rs.getString("ItemName"),
                        rs.getString("UnitName"),
                        rs.getDouble("Quantity"),
                        rs.getDouble("MinQuantity")
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    // ===================================
    // 3️⃣ بحث بالأسماء
    // ===================================
    public ObservableList<Item> searchItems(String keyword) {
        if (keyword == null || keyword.isEmpty()) return getAllItems();
        ObservableList<Item> list = FXCollections.observableArrayList();
        try (Connection conn = DatabaseConnection.getConnection()) {
            String query = """
                SELECT i.ItemID, i.ItemName, u.UnitName, s.Quantity, i.MinQuantity
                FROM Items i
                JOIN Units u ON i.UnitID = u.UnitID
                JOIN StockBalances s ON i.ItemID = s.ItemID
                WHERE i.ItemName LIKE ?
            """;
            PreparedStatement ps = conn.prepareStatement(query);
            ps.setString(1, "%" + keyword + "%");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new Item(
                        rs.getInt("ItemID"),
                        rs.getString("ItemName"),
                        rs.getString("UnitName"),
                        rs.getDouble("Quantity"),
                        rs.getDouble("MinQuantity")
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    // ===================================
    // 4️⃣ جلب الوحدات
    // ===================================
    public List<String> getAllUnits() throws SQLException {
        List<String> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT UnitName FROM Units ORDER BY UnitName")) {
            while (rs.next()) list.add(rs.getString("UnitName"));
        }
        return list;
    }

    // ===================================
    // 5️⃣ إضافة كمية جديدة (IN)
    // ===================================
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

    // ===================================
    // 6️⃣ صرف كمية (OUT)
    // ===================================
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

    // ===================================
    // 7️⃣ جلب المعاملات في يوم معين
    // ===================================
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

    // ===================================
    // 🔍 تحديث حالة النواقص
    // ===================================
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

    // ===================================
    // 8️⃣ حذف الصنف بالكامل
    // ===================================
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

    // ===================================
    // 🔹 مساعد: جلب ID الوحدة بالاسم
    // ===================================
    private int getUnitIdByName(String name, Connection conn) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("SELECT UnitID FROM Units WHERE UnitName=?");
        ps.setString(1, name);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) return rs.getInt("UnitID");
        return -1;
    }

    //================================
    // Add unit
    //===============================
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

}
