package com.example.demo.database;

import com.example.demo.model.Event;
import com.example.demo.model.RecurringEventSeries;
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
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DatabaseManager {

    private static final String DB_FILE_NAME = "DATABASEFORJAVAFX.accdb";
    private static final String DB_TMP_FILE_NAME = "DATABASEFORJAVAFX_tmp_build.accdb";
    private static final String DB_PATH_PROPERTY = "smart.diary.db.path";
    private static final int FOREVER_HORIZON_MONTHS = 12;

    private Connection connection;

    private static Path resolveDatabasePath() {
        String propertyPath = System.getProperty(DB_PATH_PROPERTY);
        if (propertyPath != null && !propertyPath.isBlank()) {
            Path path = Paths.get(propertyPath);
            if (Files.exists(path)) {
                return path;
            }
        }

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
        addCandidatePair(candidates, Paths.get(userHome, "OneDrive", "׳׳¡׳׳›׳™׳", DB_FILE_NAME));

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
        syncRecurringEventOccurrences();
        System.out.println("Connected to Access database: " + databasePath.toAbsolutePath());
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean insertEvent(Event event) {
        try {
            insertEventRow(event);
            return event.getId() > 0;
        } catch (SQLException e) {
            System.err.println("Insert event failed: " + e.getMessage());
            return false;
        }
    }

    public int createRecurringEventSeries(RecurringEventSeries series, List<User> participants) {
        String sql = """
                INSERT INTO recurring_event_series (
                    user_id, title, start_time, end_time, priority, description, location, frequency, until_date, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, 1);
            stmt.setString(2, series.getTitle());
            stmt.setTimestamp(3, Timestamp.valueOf(series.getStartTime()));
            stmt.setTimestamp(4, Timestamp.valueOf(series.getEndTime()));
            stmt.setInt(5, series.getPriority());
            stmt.setString(6, series.getDescription());
            stmt.setString(7, series.getLocation());
            stmt.setString(8, series.getFrequency());
            if (series.getUntilDate() == null) {
                stmt.setNull(9, Types.TIMESTAMP);
            } else {
                stmt.setTimestamp(9, Timestamp.valueOf(series.getUntilDate()));
            }
            stmt.setTimestamp(10, new Timestamp(System.currentTimeMillis()));

            if (stmt.executeUpdate() <= 0) {
                return -1;
            }

            int recurrenceId = readGeneratedKey(stmt);
            if (recurrenceId <= 0) {
                return -1;
            }

            series.setRecurrenceId(recurrenceId);
            for (User participant : participants) {
                addRecurringParticipant(recurrenceId, participant.getUserId(), false);
            }
            return materializeSeriesOccurrences(series);
        } catch (SQLException e) {
            System.err.println("Create recurring series failed: " + e.getMessage());
            return -1;
        }
    }

    public List<Event> getAllEvents() {
        List<Event> events = new ArrayList<>();
        String sql = "SELECT * FROM events ORDER BY start_time";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                events.add(mapEvent(rs));
            }

        } catch (SQLException e) {
            System.err.println("Load events failed: " + e.getMessage());
        }

        return events;
    }

    public List<Event> getEventsByRecurrenceId(int recurrenceId) {
        List<Event> events = new ArrayList<>();
        String sql = "SELECT * FROM events WHERE recurrence_id = ? ORDER BY start_time";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, recurrenceId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    events.add(mapEvent(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Load recurring events failed: " + e.getMessage());
        }

        return events;
    }

    public boolean deleteEvent(int eventId) {
        deleteParticipantsForEvent(eventId);
        String sql = "DELETE FROM events WHERE event_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, eventId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Delete event failed: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteRecurringSeries(int recurrenceId) {
        try {
            for (Event event : getEventsByRecurrenceId(recurrenceId)) {
                deleteParticipantsForEvent(event.getId());
            }

            try (PreparedStatement deleteEvents = connection.prepareStatement("DELETE FROM events WHERE recurrence_id = ?");
                 PreparedStatement deleteRecurringParticipants = connection.prepareStatement(
                         "DELETE FROM recurring_participants WHERE recurrence_id = ?"
                 );
                 PreparedStatement deleteSeries = connection.prepareStatement(
                         "DELETE FROM recurring_event_series WHERE recurrence_id = ?"
                 )) {
                deleteEvents.setInt(1, recurrenceId);
                deleteEvents.executeUpdate();

                deleteRecurringParticipants.setInt(1, recurrenceId);
                deleteRecurringParticipants.executeUpdate();

                deleteSeries.setInt(1, recurrenceId);
                return deleteSeries.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("Delete recurring series failed: " + e.getMessage());
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

    public void ensureDefaultUsersIfMissing() {
        String countSql = "SELECT COUNT(*) FROM users";
        String insertSql = "INSERT INTO users (username, full_name, email, created_at) VALUES (?, ?, ?, ?)";

        try (Statement countStmt = connection.createStatement();
             ResultSet rs = countStmt.executeQuery(countSql)) {

            rs.next();
            if (rs.getInt(1) > 0) {
                return;
            }

            try (PreparedStatement insertStmt = connection.prepareStatement(insertSql)) {
                insertDefaultUser(insertStmt, "owner", "Project Owner", "owner@smartdiary.local");
                insertDefaultUser(insertStmt, "participant1", "Default Participant 1", "p1@smartdiary.local");
                insertDefaultUser(insertStmt, "participant2", "Default Participant 2", "p2@smartdiary.local");
            }
        } catch (SQLException e) {
            System.err.println("Ensure default users failed: " + e.getMessage());
        }
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
                    conflicts.add(mapEvent(rs));
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

    private int insertEventRow(Event event) throws SQLException {
        String sql = """
                INSERT INTO events (user_id, title, start_time, end_time, priority, description, location, recurrence_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, 1);
            stmt.setString(2, event.getTitle());
            stmt.setTimestamp(3, Timestamp.valueOf(event.getStartTime()));
            stmt.setTimestamp(4, Timestamp.valueOf(event.getEndTime()));
            stmt.setInt(5, event.getPriority());
            stmt.setString(6, event.getDescription());
            stmt.setString(7, event.getLocation());
            if (event.getRecurrenceId() == null) {
                stmt.setNull(8, Types.INTEGER);
            } else {
                stmt.setInt(8, event.getRecurrenceId());
            }
            stmt.setTimestamp(9, new Timestamp(System.currentTimeMillis()));

            int rows = stmt.executeUpdate();
            if (rows <= 0) {
                return 0;
            }

            int newId = readGeneratedKey(stmt);
            if (newId > 0) {
                event.setId(newId);
            }
            return newId;
        }
    }

    private void syncRecurringEventOccurrences() throws SQLException {
        for (RecurringEventSeries series : getAllRecurringSeries()) {
            materializeSeriesOccurrences(series);
        }
    }

    private List<RecurringEventSeries> getAllRecurringSeries() throws SQLException {
        List<RecurringEventSeries> seriesList = new ArrayList<>();
        String sql = "SELECT * FROM recurring_event_series ORDER BY start_time";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Timestamp untilTimestamp = rs.getTimestamp("until_date");
                seriesList.add(new RecurringEventSeries(
                        rs.getInt("recurrence_id"),
                        rs.getString("title"),
                        rs.getTimestamp("start_time").toLocalDateTime(),
                        rs.getTimestamp("end_time").toLocalDateTime(),
                        rs.getInt("priority"),
                        defaultString(rs.getString("description")),
                        defaultString(rs.getString("location")),
                        rs.getString("frequency"),
                        untilTimestamp == null ? null : untilTimestamp.toLocalDateTime()
                ));
            }
        }

        return seriesList;
    }

    private int materializeSeriesOccurrences(RecurringEventSeries series) throws SQLException {
        int inserted = 0;
        LocalDateTime endInclusive = resolveMaterializationEnd(series);
        List<RecurringParticipantAssignment> participants = getRecurringParticipantAssignments(series.getRecurrenceId());

        for (Event occurrence : series.buildOccurrences(endInclusive)) {
            if (!occurrenceExists(series.getRecurrenceId(), occurrence.getStartTime())) {
                int eventId = insertEventRow(occurrence);
                if (eventId > 0) {
                    for (RecurringParticipantAssignment participant : participants) {
                        addParticipant(eventId, participant.userId(), participant.isRequired());
                    }
                    inserted++;
                }
            }
        }

        return inserted;
    }

    private LocalDateTime resolveMaterializationEnd(RecurringEventSeries series) {
        if (series.getUntilDate() != null) {
            return series.getUntilDate();
        }

        LocalDateTime base = LocalDateTime.now();
        if (base.isBefore(series.getStartTime())) {
            base = series.getStartTime();
        }
        return base.plusMonths(FOREVER_HORIZON_MONTHS);
    }

    private boolean occurrenceExists(int recurrenceId, LocalDateTime startTime) throws SQLException {
        String sql = "SELECT COUNT(*) FROM events WHERE recurrence_id = ? AND start_time = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, recurrenceId);
            stmt.setTimestamp(2, Timestamp.valueOf(startTime));
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    private boolean addRecurringParticipant(int recurrenceId, int userId, boolean isRequired) {
        String sql = "INSERT INTO recurring_participants (recurrence_id, user_id, is_required, added_at) VALUES (?, ?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, recurrenceId);
            stmt.setInt(2, userId);
            stmt.setInt(3, isRequired ? 1 : 0);
            stmt.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Add recurring participant failed: " + e.getMessage());
            return false;
        }
    }

    private List<RecurringParticipantAssignment> getRecurringParticipantAssignments(int recurrenceId) throws SQLException {
        List<RecurringParticipantAssignment> participants = new ArrayList<>();
        String sql = "SELECT user_id, is_required FROM recurring_participants WHERE recurrence_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, recurrenceId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    participants.add(new RecurringParticipantAssignment(
                            rs.getInt("user_id"),
                            rs.getBoolean("is_required")
                    ));
                }
            }
        }

        return participants;
    }

    private void deleteParticipantsForEvent(int eventId) {
        String sql = "DELETE FROM participants WHERE event_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, eventId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Delete event participants failed: " + e.getMessage());
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

    private void insertDefaultUser(PreparedStatement stmt, String username, String fullName, String email) throws SQLException {
        stmt.setString(1, username);
        stmt.setString(2, fullName);
        stmt.setString(3, email);
        stmt.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
        stmt.executeUpdate();
    }

    private Event mapEvent(ResultSet rs) throws SQLException {
        int recurrenceIdValue = rs.getInt("recurrence_id");
        Integer recurrenceId = rs.wasNull() ? null : recurrenceIdValue;
        return new Event(
                rs.getInt("event_id"),
                recurrenceId,
                rs.getString("title"),
                rs.getTimestamp("start_time").toLocalDateTime(),
                rs.getTimestamp("end_time").toLocalDateTime(),
                rs.getInt("priority"),
                defaultString(rs.getString("description")),
                defaultString(rs.getString("location"))
        );
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private record RecurringParticipantAssignment(int userId, boolean isRequired) {
    }
}
