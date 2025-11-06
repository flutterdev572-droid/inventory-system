package app.services;

import app.db.DatabaseConnection;
import java.sql.*;
import java.util.*;

public class DeviceService {

    // 1️⃣ جلب كل الأجهزة
    public List<Map<String, Object>> getAllDevices() {
        List<Map<String, Object>> devices = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT DeviceID, DeviceName, CreatedAt FROM Devices ORDER BY DeviceID DESC");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> d = new HashMap<>();
                d.put("id", rs.getInt("DeviceID"));
                d.put("name", rs.getString("DeviceName"));
                d.put("createdAt", rs.getTimestamp("CreatedAt"));
                devices.add(d);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return devices;
    }

    // 2️⃣ إضافة جهاز جديد
    public boolean addDevice(String deviceName, int employeeId) {
        String sql = "INSERT INTO Devices (DeviceName, CreatedBy) VALUES (?, ?)";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, deviceName);
            ps.setInt(2, employeeId);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (e.getMessage().contains("UQ_Devices_DeviceName")) {
                System.err.println("⚠️ الجهاز موجود مسبقاً!");
            } else e.printStackTrace();
            return false;
        }
    }

    // 3️⃣ حذف جهاز
    public boolean deleteDevice(int deviceId) {
        String sql = "DELETE FROM Devices WHERE DeviceID=?";
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, deviceId);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // 4️⃣ إضافة مكون للجهاز
    public boolean addDeviceComponent(int deviceId, int itemId, double qty) {
        String sql = """
            INSERT INTO DeviceComponents (DeviceID, ItemID, QuantityPerDevice)
            VALUES (?, ?, ?)
        """;
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, deviceId);
            ps.setInt(2, itemId);
            ps.setDouble(3, qty);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (e.getMessage().contains("duplicate")) {
                System.err.println("⚠️ هذا المكون مضاف بالفعل للجهاز!");
            } else e.printStackTrace();
            return false;
        }
    }

    // 5️⃣ جلب مكونات الجهاز
    public List<Map<String, Object>> getDeviceComponents(int deviceId) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = """
            SELECT dc.DeviceComponentID, i.ItemName, u.UnitName, dc.QuantityPerDevice, s.Quantity AS StockQty
            FROM DeviceComponents dc
            JOIN Items i ON dc.ItemID = i.ItemID
            JOIN Units u ON i.UnitID = u.UnitID
            JOIN StockBalances s ON i.ItemID = s.ItemID
            WHERE dc.DeviceID = ?
        """;
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, deviceId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> comp = new HashMap<>();
                comp.put("componentId", rs.getInt("DeviceComponentID"));
                comp.put("itemName", rs.getString("ItemName"));
                comp.put("unitName", rs.getString("UnitName"));
                comp.put("quantityPerDevice", rs.getDouble("QuantityPerDevice"));
                comp.put("stockQty", rs.getDouble("StockQty"));
                list.add(comp);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    // 6️⃣ حذف مكون من جهاز
    public boolean deleteComponent(int componentId) {
        try (Connection conn = DatabaseConnection.getInventoryConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM DeviceComponents WHERE DeviceComponentID=?")) {
            ps.setInt(1, componentId);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // 7️⃣ إخراج جهاز (خصم المكونات)
    public boolean produceDevice(int deviceId, int deviceCount, int employeeId) {
        try (Connection conn = DatabaseConnection.getInventoryConnection()) {
            conn.setAutoCommit(false);

            PreparedStatement comps = conn.prepareStatement("""
                SELECT dc.ItemID, dc.QuantityPerDevice, s.Quantity
                FROM DeviceComponents dc
                JOIN StockBalances s ON dc.ItemID = s.ItemID
                WHERE dc.DeviceID = ?
            """);
            comps.setInt(1, deviceId);
            ResultSet rs = comps.executeQuery();

            List<String> insufficient = new ArrayList<>();
            Map<Integer, Double> deductions = new HashMap<>();

            while (rs.next()) {
                int itemId = rs.getInt("ItemID");
                double perDevice = rs.getDouble("QuantityPerDevice");
                double stock = rs.getDouble("Quantity");
                double totalNeeded = perDevice * deviceCount;

                if (stock < totalNeeded)
                    insufficient.add("العنصر: " + itemId + " الكمية المتاحة: " + stock);
                else
                    deductions.put(itemId, totalNeeded);
            }

            if (!insufficient.isEmpty()) {
                conn.rollback();
                System.err.println("⚠️ لا يوجد مخزون كافٍ:\n" + String.join("\n", insufficient));
                return false;
            }

            for (Map.Entry<Integer, Double> entry : deductions.entrySet()) {
                PreparedStatement upd = conn.prepareStatement(
                        "UPDATE StockBalances SET Quantity = Quantity - ? WHERE ItemID=?");
                upd.setDouble(1, entry.getValue());
                upd.setInt(2, entry.getKey());
                upd.executeUpdate();

                PreparedStatement trans = conn.prepareStatement("""
                    INSERT INTO StockTransactions (ItemID, TransactionType, Quantity, EmployeeID, Notes)
                    VALUES (?, 'OUT', ?, ?, ?)
                """);
                trans.setInt(1, entry.getKey());
                trans.setDouble(2, entry.getValue());
                trans.setInt(3, employeeId);
                trans.setString(4, "خصم مكونات لتصنيع جهاز");
                trans.executeUpdate();
            }

            PreparedStatement log = conn.prepareStatement("""
                INSERT INTO DeviceProductionLog (DeviceID, Quantity, EmployeeID, Notes)
                VALUES (?, ?, ?, ?)
            """);
            log.setInt(1, deviceId);
            log.setInt(2, deviceCount);
            log.setInt(3, employeeId);
            log.setString(4, "إنتاج " + deviceCount + " جهاز");
            log.executeUpdate();

            conn.commit();
            return true;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
