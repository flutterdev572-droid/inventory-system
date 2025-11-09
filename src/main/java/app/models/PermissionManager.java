package app.models;

import app.db.DatabaseConnection;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class PermissionManager {

    private static final Map<Integer, UserPermissions> permissionsCache = new HashMap<>();

    public static class UserPermissions {
        public boolean canAddItems;
        public boolean canManageInventory;
        public boolean canAddDevices;
        public boolean canManageDevices;
        public boolean canTrackSerials;
        public boolean canViewReports;
        public boolean canScrapMaintenance;
        public boolean canManagePricing;
        public boolean canAccessControlPanel;
    }

    public static UserPermissions getUserPermissions(int employeeId) {
        // Check cache first
        if (permissionsCache.containsKey(employeeId)) {
            return permissionsCache.get(employeeId);
        }

        UserPermissions permissions = new UserPermissions();

        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "SELECT * FROM EmployeePermissions WHERE EmployeeID = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, employeeId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                permissions.canAddItems = rs.getBoolean("CanAddItems");
                permissions.canManageInventory = rs.getBoolean("CanManageInventory");
                permissions.canAddDevices = rs.getBoolean("CanAddDevices");
                permissions.canManageDevices = rs.getBoolean("CanManageDevices");
                permissions.canTrackSerials = rs.getBoolean("CanTrackSerials");
                permissions.canViewReports = rs.getBoolean("CanViewReports");
                permissions.canScrapMaintenance = rs.getBoolean("CanScrapMaintenance");
                permissions.canManagePricing = rs.getBoolean("CanManagePricing");
                permissions.canAccessControlPanel = rs.getBoolean("CanAccessControlPanel");
            }

            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Cache the permissions
        permissionsCache.put(employeeId, permissions);
        return permissions;
    }

    public static void refreshUserPermissions(int employeeId) {
        permissionsCache.remove(employeeId);
        getUserPermissions(employeeId); // Reload and cache
    }

    public static void clearCache() {
        permissionsCache.clear();
    }
}