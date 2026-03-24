package com.example.demo.database;

import com.example.demo.model.Event;
import com.example.demo.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseManagerTest {

    private static final String DB_PROPERTY = "smart.diary.db.path";
    private static final Path PROJECT_DB = Path.of(
            "C:\\Users\\Lenovo\\OneDrive\\Desktop\\JAVAPROJECTCOMPLETE SAGE\\DATABASEFORJAVAFX_tmp_build.accdb"
    );

    @TempDir
    Path tempDir;

    @Test
    void connectLoadsUsersFromConfiguredDatabase() throws Exception {
        assertTrue(Files.exists(PROJECT_DB), "Expected test database file to exist");

        Path testDb = tempDir.resolve("DATABASEFORJAVAFX_tmp_build.accdb");
        Files.copy(PROJECT_DB, testDb, StandardCopyOption.REPLACE_EXISTING);

        String previous = System.getProperty(DB_PROPERTY);
        System.setProperty(DB_PROPERTY, testDb.toString());

        DatabaseManager manager = new DatabaseManager();
        try {
            manager.connect();
            List<User> users = manager.getAllUsers();
            assertFalse(users.isEmpty(), "Expected seeded users to be available");
        } finally {
            manager.disconnect();
            restoreProperty(previous);
        }
    }

    @Test
    void registerAndAuthenticateUserWorks() throws Exception {
        assertTrue(Files.exists(PROJECT_DB), "Expected test database file to exist");

        Path testDb = tempDir.resolve("DATABASEFORJAVAFX_tmp_build.accdb");
        Files.copy(PROJECT_DB, testDb, StandardCopyOption.REPLACE_EXISTING);

        String previous = System.getProperty(DB_PROPERTY);
        System.setProperty(DB_PROPERTY, testDb.toString());

        DatabaseManager manager = new DatabaseManager();
        try {
            manager.connect();
            User created = manager.registerUser("gilad_test", "Gilad Test", "gilad@test.local", "secret1");
            assertNotNull(created);
            assertTrue(created.getUserId() > 0);

            User authenticated = manager.authenticateUser("gilad_test", "secret1");
            assertNotNull(authenticated);
            assertEquals(created.getUsername(), authenticated.getUsername());
        } finally {
            manager.disconnect();
            restoreProperty(previous);
        }
    }

    @Test
    void connectDoesNotRewriteExistingPlainTextPasswords() throws Exception {
        assertTrue(Files.exists(PROJECT_DB), "Expected test database file to exist");

        Path testDb = tempDir.resolve("DATABASEFORJAVAFX_tmp_build.accdb");
        Files.copy(PROJECT_DB, testDb, StandardCopyOption.REPLACE_EXISTING);

        String previous = System.getProperty(DB_PROPERTY);
        System.setProperty(DB_PROPERTY, testDb.toString());

        DatabaseManager manager = new DatabaseManager();
        try {
            manager.connect();
            User created = manager.registerUser("legacy_user", "Legacy User", "legacy@test.local", "secret1");
            assertNotNull(created);
            manager.disconnect();

            try (var connection = java.sql.DriverManager.getConnection("jdbc:ucanaccess://" + testDb);
                 var stmt = connection.prepareStatement("UPDATE users SET password_hash = ? WHERE user_id = ?")) {
                stmt.setString(1, "y");
                stmt.setInt(2, created.getUserId());
                stmt.executeUpdate();
            }

            manager.connect();
            User authenticated = manager.authenticateUser("legacy_user", "y");
            assertNotNull(authenticated, "Expected existing plain-text password value to remain unchanged");
            assertEquals("legacy_user", authenticated.getUsername());
        } finally {
            manager.disconnect();
            restoreProperty(previous);
        }
    }

    @Test
    void authenticateUserAcceptsLegacyMalformedPasswordWithDefaultPassword() throws Exception {
        assertTrue(Files.exists(PROJECT_DB), "Expected test database file to exist");

        Path testDb = tempDir.resolve("DATABASEFORJAVAFX_tmp_build.accdb");
        Files.copy(PROJECT_DB, testDb, StandardCopyOption.REPLACE_EXISTING);

        String previous = System.getProperty(DB_PROPERTY);
        System.setProperty(DB_PROPERTY, testDb.toString());

        DatabaseManager manager = new DatabaseManager();
        try {
            manager.connect();
            User created = manager.registerUser("legacy_fallback", "Legacy Fallback", "legacy2@test.local", "secret1");
            assertNotNull(created);

            try (var connection = java.sql.DriverManager.getConnection("jdbc:ucanaccess://" + testDb);
                 var stmt = connection.prepareStatement("UPDATE users SET password_hash = ? WHERE user_id = ?")) {
                stmt.setString(1, "z");
                stmt.setInt(2, created.getUserId());
                stmt.executeUpdate();
            }

            User authenticated = manager.authenticateUser("legacy_fallback", "password");
            assertNotNull(authenticated, "Expected fallback authentication for malformed legacy password value");
            assertEquals("legacy_fallback", authenticated.getUsername());
        } finally {
            manager.disconnect();
            restoreProperty(previous);
        }
    }

    @Test
    void calendarEventsForUserIncludeOwnedAndParticipantEvents() throws Exception {
        assertTrue(Files.exists(PROJECT_DB), "Expected test database file to exist");

        Path testDb = tempDir.resolve("DATABASEFORJAVAFX_tmp_build.accdb");
        Files.copy(PROJECT_DB, testDb, StandardCopyOption.REPLACE_EXISTING);

        String previous = System.getProperty(DB_PROPERTY);
        System.setProperty(DB_PROPERTY, testDb.toString());

        DatabaseManager manager = new DatabaseManager();
        try {
            manager.connect();
            User owner = manager.registerUser("owner_test", "Owner Test", "", "secret1");
            User participant = manager.registerUser("participant_test", "Participant Test", "", "secret1");

            LocalDateTime start = LocalDateTime.of(2026, 3, 22, 10, 0);
            Event ownedEvent = new Event(
                    0,
                    owner.getUserId(),
                    "Owner Event",
                    start,
                    start.plusHours(1),
                    3,
                    "",
                    ""
            );
            assertTrue(manager.insertEvent(ownedEvent));
            assertTrue(manager.addParticipant(ownedEvent.getId(), participant.getUserId(), false));

            List<Event> ownerCalendar = manager.getCalendarEventsForUser(owner.getUserId());
            List<Event> participantCalendar = manager.getCalendarEventsForUser(participant.getUserId());

            assertTrue(ownerCalendar.stream().anyMatch(event -> event.getId() == ownedEvent.getId()));
            assertTrue(participantCalendar.stream().anyMatch(event -> event.getId() == ownedEvent.getId()));
        } finally {
            manager.disconnect();
            restoreProperty(previous);
        }
    }

    private static void restoreProperty(String previous) {
        if (previous == null) {
            System.clearProperty(DB_PROPERTY);
        } else {
            System.setProperty(DB_PROPERTY, previous);
        }
    }
}
