package com.example.demo.database;

import com.example.demo.model.Event;
import com.example.demo.model.User;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DatabaseManager {

    private static final String DB_FILE_NAME = "DATABASEFORJAVAFX.accdb";
    private static final String DB_TMP_FILE_NAME = "DATABASEFORJAVAFX_tmp_build.accdb";

    private Connection connection;

    private static String buildUrl() {
        Path resolved = resolveDatabasePath();
        return "jdbc:ucanaccess://" + resolved;
    }

    private static Path resolveDatabasePath() {
        String envPath = System.getenv("SMART_DIARY_DB_PATH");
        if (envPath != null && !envPath.isBlank()) {
            Path path = Paths.get(envPath);
            if (Files.exists(path)) {
                return path;
            }
        }

        String userHome = System.getProperty("user.home");
        List<Path> candidates = new ArrayList<>();
        addCandidatePair(candidates, Paths.get(System.getProperty("user.dir")).resolveSibling(DB_FILE_NAME));
        addCandidatePair(candidates, Paths.get(System.getProperty("user.dir"), "data", DB_FILE_NAME));
        addCandidatePair(candidates, Paths.get(System.getProperty("user.dir"), DB_FILE_NAME));
        addCandidatePair(candidates, Paths.get(userHome, "Desktop", "JAVAPROJECTCOMPLETE SAGE", DB_FILE_NAME));
        addCandidatePair(candidates, Paths.get(userHome, "OneDrive", "Desktop", "JAVAPROJECTCOMPLETE SAGE", DB_FILE_NAME));
        addCandidatePair(candidates, Paths.get(userHome, "Documents", DB_FILE_NAME));
        addCandidatePair(candidates, Paths.get(userHome, "OneDrive", "Documents", DB_FILE_NAME));
        addCandidatePair(candidates, Paths.get(userHome, "OneDrive", "מסמכים", DB_FILE_NAME));

        return candidates.stream()
                .filter(Files::exists)
                .max(Comparator
                        .comparingInt(DatabaseManager::countUsersBestEffort)
                        .thenComparing(Path::toString))
                .orElseThrow(() -> new IllegalStateException(
                        "Access database file not found. Set SMART_DIARY_DB_PATH or place " + DB_FILE_NAME + " in /data"
                ));
    }

    private static void addCandidatePair(List<Path> candidates, Path primaryPath) {
        candidates.add(primaryPath);
        String fileName = primaryPath.getFileName().toString();
        if (DB_FILE_NAME.equalsIgnoreCase(fileName)) {
            candidates.add(primaryPath.resolveSibling(DB_TMP_FILE_NAME));
        }
    }

    private static int countUsersBestEffort(Path path) {
        try (Connection testConnection = DriverManager.getConnection("jdbc:ucanaccess://" + path);
             Statement stmt = testConnection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
            rs.next();
            return rs.getInt(1);
        } catch (Exception ignored) {
            return -1;
        }
    }

    public void connect() throws SQLException {
        Path databasePath = resolveDatabasePath();
        connection = DriverManager.getConnection("jdbc:ucanaccess://" + databasePath);
        DatabaseMigrationTool.migrate(connection);
        System.out.println("Connected to Access database: " + databasePath.toAbsolutePath());
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean insertEvent(Event event) {
        String sql = "INSERT INTO events (user_id, title, start_time, end_time, priority, description, location, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, 1);
            stmt.setString(2, event.getTitle());
            stmt.setTimestamp(3, Timestamp.valueOf(event.getStartTime()));
            stmt.setTimestamp(4, Timestamp.valueOf(event.getEndTime()));
            stmt.setInt(5, event.getPriority());
            stmt.setString(6, event.getDescription());
            stmt.setString(7, event.getLocation());
            stmt.setTimestamp(8, new Timestamp(System.currentTimeMillis()));

            int rows = stmt.executeUpdate();
            if (rows <= 0) {
                return false;
            }

            int newId = readGeneratedKey(stmt);
            if (newId > 0) {
                event.setId(newId);
            }
            return true;

        } catch (SQLException e) {
            System.err.println("Insert event failed: " + e.getMessage());
            return false;
        }
    }

    private int readGeneratedKey(PreparedStatement stmt) {
        try (ResultSet rs = stmt.getGeneratedKeys()) {
            if (rs != null && rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException ignored) {
        }

        try (Statement identityStmt = connection.createStatement();
             ResultSet idRs = identityStmt.executeQuery("SELECT @@IDENTITY")) {
            if (idRs.next()) {
                return idRs.getInt(1);
            }
        } catch (SQLException ignored) {
        }

        return 0;
    }

    public List<Event> getAllEvents() {
        List<Event> events = new ArrayList<>();
        String sql = "SELECT * FROM events ORDER BY start_time";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Event event = new Event(
                        rs.getInt("event_id"),
                        rs.getString("title"),
                        rs.getTimestamp("start_time").toLocalDateTime(),
                        rs.getTimestamp("end_time").toLocalDateTime(),
                        rs.getInt("priority"),
                        rs.getString("description") != null ? rs.getString("description") : "",
                        rs.getString("location") != null ? rs.getString("location") : ""
                );
                events.add(event);
            }

        } catch (SQLException e) {
            System.err.println("Load events failed: " + e.getMessage());
        }

        return events;
    }

    public boolean deleteEvent(int eventId) {
        String sql = "DELETE FROM events WHERE event_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, eventId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Delete event failed: " + e.getMessage());
            return false;
        }
    }

    public boolean updateEvent(Event event) {
        String sql = "UPDATE events SET title = ?, start_time = ?, end_time = ?, " +
                "priority = ?, description = ?, location = ? WHERE event_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, event.getTitle());
            stmt.setTimestamp(2, Timestamp.valueOf(event.getStartTime()));
            stmt.setTimestamp(3, Timestamp.valueOf(event.getEndTime()));
            stmt.setInt(4, event.getPriority());
            stmt.setString(5, event.getDescription());
            stmt.setString(6, event.getLocation());
            stmt.setInt(7, event.getId());

            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("Update event failed: " + e.getMessage());
            return false;
        }
    }

    public List<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users ORDER BY full_name";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                User user = new User(
                        rs.getInt("user_id"),
                        rs.getString("username"),
                        rs.getString("full_name"),
                        rs.getString("email")
                );
                users.add(user);
            }

        } catch (SQLException e) {
            System.err.println("Load users failed: " + e.getMessage());
        }

        return users;
    }

    public boolean addParticipant(int eventId, int userId, boolean isRequired) {
        String sql = "INSERT INTO participants (event_id, user_id, status, is_required, invited_at) " +
                "VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, eventId);
            stmt.setInt(2, userId);
            stmt.setString(3, "pending");
            stmt.setInt(4, isRequired ? 1 : 0);
            stmt.setTimestamp(5, new Timestamp(System.currentTimeMillis()));

            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("Add participant failed: " + e.getMessage());
            return false;
        }
    }

    public List<User> getEventParticipants(int eventId) {
        List<User> participants = new ArrayList<>();
        String sql = "SELECT u.* FROM users u " +
                "INNER JOIN participants p ON u.user_id = p.user_id " +
                "WHERE p.event_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, eventId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                User user = new User(
                        rs.getInt("user_id"),
                        rs.getString("username"),
                        rs.getString("full_name"),
                        rs.getString("email")
                );
                participants.add(user);
            }
            }

        } catch (SQLException e) {
            System.err.println("Load event participants failed: " + e.getMessage());
        }

        return participants;
    }

    public List<Event> findTimeConflicts(LocalDateTime startTime, LocalDateTime endTime, int excludeEventId) {
        List<Event> conflicts = new ArrayList<>();
        String sql = "SELECT * FROM events WHERE event_id <> ? " +
                "AND ((start_time < ? AND end_time > ?) OR " +
                "(start_time < ? AND end_time > ?) OR " +
                "(start_time >= ? AND end_time <= ?))";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, excludeEventId);
            stmt.setTimestamp(2, Timestamp.valueOf(endTime));
            stmt.setTimestamp(3, Timestamp.valueOf(startTime));
            stmt.setTimestamp(4, Timestamp.valueOf(endTime));
            stmt.setTimestamp(5, Timestamp.valueOf(startTime));
            stmt.setTimestamp(6, Timestamp.valueOf(startTime));
            stmt.setTimestamp(7, Timestamp.valueOf(endTime));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Event event = new Event(
                            rs.getInt("event_id"),
                            rs.getString("title"),
                            rs.getTimestamp("start_time").toLocalDateTime(),
                            rs.getTimestamp("end_time").toLocalDateTime(),
                            rs.getInt("priority"),
                            rs.getString("description") != null ? rs.getString("description") : "",
                            rs.getString("location") != null ? rs.getString("location") : ""
                    );
                    conflicts.add(event);
                }
            }

        } catch (SQLException e) {
            System.err.println("Find conflicts failed: " + e.getMessage());
        }

        return conflicts;
    }

    public boolean saveConflict(int event1Id, int event2Id, String conflictType) {
        String sql = "INSERT INTO conflicts (event1_id, event2_id, conflict_type, detected_at, resolved) " +
                "VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, event1Id);
            stmt.setInt(2, event2Id);
            stmt.setString(3, conflictType);
            stmt.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            stmt.setInt(5, 0);

            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("Save conflict failed: " + e.getMessage());
            return false;
        }
    }
}
