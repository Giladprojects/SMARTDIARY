package com.example.demo.database;

import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseMigrationTool {

    private DatabaseMigrationTool() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("Usage: DatabaseMigrationTool <absolute-path-to-accdb>");
        }

        String url = "jdbc:ucanaccess://" + args[0];
        try (Connection connection = DriverManager.getConnection(url)) {
            migrate(connection);
            System.out.println("Migration completed for: " + args[0]);
        }
    }

    public static void migrate(Connection connection) throws SQLException {
        ensureUsersTable(connection);
        ensureEventsTable(connection);
        ensureParticipantsTable(connection);
        ensureConflictsTable(connection);
        ensureRecurringEventSeriesTable(connection);
        ensureRecurringParticipantsTable(connection);
        ensureSchemaMigrationsTable(connection);
        seedUsers(connection);
        markSchemaVersion(connection, "2026-03-12-smartdiary-recurring-events");
    }

    private static void ensureIndexesBestEffort(Connection connection) {
        try {
            ensureIndexes(connection);
        } catch (Exception ignored) {
            // Access driver can report duplicate index as a hard failure on some files.
            // Indexes are performance-only, so schema migration continues.
        }
    }

    private static void ensureUsersTable(Connection connection) throws SQLException {
        if (!tableExists(connection, "users")) {
            execute(connection, """
                    CREATE TABLE users (
                        user_id COUNTER PRIMARY KEY,
                        username TEXT(100) NOT NULL,
                        full_name TEXT(150) NOT NULL,
                        email TEXT(150),
                        created_at DATETIME
                    )
                    """);
            return;
        }

        ensureColumn(connection, "users", "username", "TEXT(100)");
        ensureColumn(connection, "users", "full_name", "TEXT(150)");
        ensureColumn(connection, "users", "email", "TEXT(150)");
        ensureColumn(connection, "users", "created_at", "DATETIME");
    }

    private static void ensureEventsTable(Connection connection) throws SQLException {
        if (!tableExists(connection, "events")) {
            execute(connection, """
                    CREATE TABLE events (
                        event_id COUNTER PRIMARY KEY,
                        user_id LONG,
                        title TEXT(255) NOT NULL,
                        start_time DATETIME NOT NULL,
                        end_time DATETIME NOT NULL,
                        priority INTEGER,
                        description MEMO,
                        location TEXT(255),
                        recurrence_id LONG,
                        created_at DATETIME
                    )
                    """);
            return;
        }

        ensureColumn(connection, "events", "user_id", "LONG");
        ensureColumn(connection, "events", "title", "TEXT(255)");
        ensureColumn(connection, "events", "start_time", "DATETIME");
        ensureColumn(connection, "events", "end_time", "DATETIME");
        ensureColumn(connection, "events", "priority", "INTEGER");
        ensureColumn(connection, "events", "description", "MEMO");
        ensureColumn(connection, "events", "location", "TEXT(255)");
        ensureColumn(connection, "events", "recurrence_id", "LONG");
        ensureColumn(connection, "events", "created_at", "DATETIME");
    }

    private static void ensureParticipantsTable(Connection connection) throws SQLException {
        if (!tableExists(connection, "participants")) {
            execute(connection, """
                    CREATE TABLE participants (
                        participant_id COUNTER PRIMARY KEY,
                        event_id LONG NOT NULL,
                        user_id LONG NOT NULL,
                        status TEXT(20),
                        is_required YESNO,
                        invited_at DATETIME
                    )
                    """);
            return;
        }

        ensureColumn(connection, "participants", "event_id", "LONG");
        ensureColumn(connection, "participants", "user_id", "LONG");
        ensureColumn(connection, "participants", "status", "TEXT(20)");
        ensureColumn(connection, "participants", "is_required", "YESNO");
        ensureColumn(connection, "participants", "invited_at", "DATETIME");
    }

    private static void ensureConflictsTable(Connection connection) throws SQLException {
        if (!tableExists(connection, "conflicts")) {
            execute(connection, """
                    CREATE TABLE conflicts (
                        conflict_id COUNTER PRIMARY KEY,
                        event1_id LONG,
                        event2_id LONG,
                        conflict_type TEXT(50),
                        detected_at DATETIME,
                        resolved YESNO,
                        resolution MEMO
                    )
                    """);
            return;
        }

        ensureColumn(connection, "conflicts", "event1_id", "LONG");
        ensureColumn(connection, "conflicts", "event2_id", "LONG");
        ensureColumn(connection, "conflicts", "conflict_type", "TEXT(50)");
        ensureColumn(connection, "conflicts", "detected_at", "DATETIME");
        ensureColumn(connection, "conflicts", "resolved", "YESNO");
        ensureColumn(connection, "conflicts", "resolution", "MEMO");
    }

    private static void ensureRecurringEventSeriesTable(Connection connection) throws SQLException {
        if (!tableExists(connection, "recurring_event_series")) {
            execute(connection, """
                    CREATE TABLE recurring_event_series (
                        recurrence_id COUNTER PRIMARY KEY,
                        user_id LONG,
                        title TEXT(255) NOT NULL,
                        start_time DATETIME NOT NULL,
                        end_time DATETIME NOT NULL,
                        priority INTEGER,
                        description MEMO,
                        location TEXT(255),
                        frequency TEXT(20) NOT NULL,
                        until_date DATETIME,
                        created_at DATETIME
                    )
                    """);
            return;
        }

        ensureColumn(connection, "recurring_event_series", "user_id", "LONG");
        ensureColumn(connection, "recurring_event_series", "title", "TEXT(255)");
        ensureColumn(connection, "recurring_event_series", "start_time", "DATETIME");
        ensureColumn(connection, "recurring_event_series", "end_time", "DATETIME");
        ensureColumn(connection, "recurring_event_series", "priority", "INTEGER");
        ensureColumn(connection, "recurring_event_series", "description", "MEMO");
        ensureColumn(connection, "recurring_event_series", "location", "TEXT(255)");
        ensureColumn(connection, "recurring_event_series", "frequency", "TEXT(20)");
        ensureColumn(connection, "recurring_event_series", "until_date", "DATETIME");
        ensureColumn(connection, "recurring_event_series", "created_at", "DATETIME");
    }

    private static void ensureRecurringParticipantsTable(Connection connection) throws SQLException {
        if (!tableExists(connection, "recurring_participants")) {
            execute(connection, """
                    CREATE TABLE recurring_participants (
                        recurring_participant_id COUNTER PRIMARY KEY,
                        recurrence_id LONG NOT NULL,
                        user_id LONG NOT NULL,
                        is_required YESNO,
                        added_at DATETIME
                    )
                    """);
            return;
        }

        ensureColumn(connection, "recurring_participants", "recurrence_id", "LONG");
        ensureColumn(connection, "recurring_participants", "user_id", "LONG");
        ensureColumn(connection, "recurring_participants", "is_required", "YESNO");
        ensureColumn(connection, "recurring_participants", "added_at", "DATETIME");
    }

    private static void ensureSchemaMigrationsTable(Connection connection) throws SQLException {
        if (!tableExists(connection, "schema_migrations")) {
            execute(connection, """
                    CREATE TABLE schema_migrations (
                        id COUNTER PRIMARY KEY,
                        version_tag TEXT(100) NOT NULL,
                        applied_at DATETIME
                    )
                    """);
            return;
        }

        ensureColumn(connection, "schema_migrations", "version_tag", "TEXT(100)");
        ensureColumn(connection, "schema_migrations", "applied_at", "DATETIME");
    }

    private static void ensureIndexes(Connection connection) throws SQLException {
        ensureIndex(connection, "events", "idx_events_start_time", "CREATE INDEX idx_events_start_time ON events (start_time)");
        ensureIndex(connection, "events", "idx_events_end_time", "CREATE INDEX idx_events_end_time ON events (end_time)");
        ensureIndex(connection, "events", "idx_events_recurrence_id", "CREATE INDEX idx_events_recurrence_id ON events (recurrence_id)");
        ensureIndex(
                connection,
                "participants",
                "idx_participants_event_user",
                "CREATE UNIQUE INDEX idx_participants_event_user ON participants (event_id, user_id)"
        );
        ensureIndex(
                connection,
                "recurring_participants",
                "idx_recurring_participants_series_user",
                "CREATE UNIQUE INDEX idx_recurring_participants_series_user ON recurring_participants (recurrence_id, user_id)"
        );
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet rs = metaData.getTables(null, null, tableName, null)) {
            if (rs.next()) {
                return true;
            }
        }
        try (ResultSet rs = metaData.getTables(null, null, tableName.toUpperCase(), null)) {
            return rs.next();
        }
    }

    private static void ensureColumn(Connection connection, String table, String column, String type) throws SQLException {
        if (!columnExists(connection, table, column)) {
            execute(connection, "ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        }
    }

    private static boolean columnExists(Connection connection, String table, String column) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet rs = metaData.getColumns(null, null, table, column)) {
            if (rs.next()) {
                return true;
            }
        }
        try (ResultSet rs = metaData.getColumns(null, null, table.toUpperCase(), column.toUpperCase())) {
            return rs.next();
        }
    }

    private static void ensureIndex(Connection connection, String tableName, String indexName, String createSql) throws SQLException {
        if (!indexExists(connection, tableName, indexName)) {
            try {
                execute(connection, createSql);
            } catch (Exception ex) {
                String msg = ex.getMessage();
                if (msg != null && msg.toLowerCase().contains("duplicate index name")) {
                    return;
                }
                throw ex;
            }
        }
    }

    private static boolean indexExists(Connection connection, String tableName, String indexName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet rs = metaData.getIndexInfo(null, null, tableName, false, false)) {
            while (rs.next()) {
                String existingName = rs.getString("INDEX_NAME");
                if (existingName != null && existingName.equalsIgnoreCase(indexName)) {
                    return true;
                }
            }
        }
        try (ResultSet rs = metaData.getIndexInfo(null, null, tableName.toUpperCase(), false, false)) {
            while (rs.next()) {
                String existingName = rs.getString("INDEX_NAME");
                if (existingName != null && existingName.equalsIgnoreCase(indexName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void seedUsers(Connection connection) throws SQLException {
        if (!tableExists(connection, "users")) {
            return;
        }

        int count;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
            rs.next();
            count = rs.getInt(1);
        }

        if (count > 0) {
            return;
        }

        String sql = "INSERT INTO users (username, full_name, email, created_at) VALUES (?, ?, ?, NOW())";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            insertSeedUser(stmt, "owner", "Project Owner", "owner@smartdiary.local");
            insertSeedUser(stmt, "participant1", "Default Participant 1", "p1@smartdiary.local");
            insertSeedUser(stmt, "participant2", "Default Participant 2", "p2@smartdiary.local");
        }
    }

    private static void markSchemaVersion(Connection connection, String versionTag) throws SQLException {
        String checkSql = "SELECT COUNT(*) FROM schema_migrations WHERE version_tag = ?";
        try (PreparedStatement check = connection.prepareStatement(checkSql)) {
            check.setString(1, versionTag);
            try (ResultSet rs = check.executeQuery()) {
                rs.next();
                if (rs.getInt(1) > 0) {
                    return;
                }
            }
        }

        String insertSql = "INSERT INTO schema_migrations (version_tag, applied_at) VALUES (?, NOW())";
        try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
            insert.setString(1, versionTag);
            insert.executeUpdate();
        }
    }

    private static void insertSeedUser(
            PreparedStatement stmt,
            String username,
            String fullName,
            String email
    ) throws SQLException {
        stmt.setString(1, username);
        stmt.setString(2, fullName);
        stmt.setString(3, email);
        stmt.executeUpdate();
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }
}
