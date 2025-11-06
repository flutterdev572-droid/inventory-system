package app.services;

import app.current_user.CurrentUser;
import app.db.DatabaseConnection;
import java.sql.*;

public class LogService {

    public static void addLog(String actionType, String description) {
        String query = "INSERT INTO Logs (ActionType, Description, EmployeeID) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, actionType);
            stmt.setString(2, description);

            // ✅ استخدام EmployeeID بدلاً من NULL
            if (CurrentUser.getId() > 0) {
                stmt.setInt(3, CurrentUser.getId());
            } else {
                stmt.setNull(3, Types.INTEGER);
            }

            stmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}