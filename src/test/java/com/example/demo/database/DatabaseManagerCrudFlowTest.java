package com.example.demo.database;

import com.example.demo.model.Event;
import com.example.demo.model.RecurringEventSeries;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseManagerCrudFlowTest {

    private static final String DB_PROPERTY = "smart.diary.db.path";
    private static final Path PROJECT_DB = Path.of(
            "C:\\Users\\Lenovo\\OneDrive\\Desktop\\JAVAPROJECTCOMPLETE SAGE\\DATABASEFORJAVAFX_tmp_build.accdb"
    );

    @TempDir
    Path tempDir;

    @Test
    void eventCrudRoundTripWorksAgainstCopiedDatabase() throws Exception {
        assertTrue(Files.exists(PROJECT_DB), "Expected source test database file to exist");

        Path testDb = tempDir.resolve("DATABASEFORJAVAFX_tmp_build.accdb");
        Files.copy(PROJECT_DB, testDb, StandardCopyOption.REPLACE_EXISTING);

        String previous = System.getProperty(DB_PROPERTY);
        System.setProperty(DB_PROPERTY, testDb.toString());

        DatabaseManager manager = new DatabaseManager();
        try {
            manager.connect();

            List<User> users = manager.getAllUsers();
            assertFalse(users.isEmpty(), "Expected at least one user in copied database");

            User participant = users.get(0);
            LocalDateTime start = LocalDateTime.of(2026, 3, 15, 10, 0);
            Event event = new Event(
                    0,
                    "Integration Flow Test",
                    start,
                    start.plusHours(1),
                    3,
                    "Initial description",
                    "Room A"
            );

            assertTrue(manager.insertEvent(event), "Expected event insert to succeed");
            assertNotEquals(0, event.getId(), "Expected inserted event to receive an id");

            List<Event> insertedEvents = manager.getAllEvents();
            assertTrue(
                    insertedEvents.stream().anyMatch(saved -> saved.getId() == event.getId()),
                    "Expected inserted event to be returned by getAllEvents"
            );

            assertTrue(
                    manager.addParticipant(event.getId(), participant.getUserId(), false),
                    "Expected participant insert to succeed"
            );

            List<User> participants = manager.getEventParticipants(event.getId());
            assertTrue(
                    participants.stream().anyMatch(user -> user.getUserId() == participant.getUserId()),
                    "Expected added participant to be returned for the event"
            );

            event.setTitle("Integration Flow Test Updated");
            event.setPriority(5);
            event.setDescription("Updated description");
            event.setLocation("Room B");
            assertTrue(manager.updateEvent(event), "Expected event update to succeed");

            Event updated = manager.getAllEvents().stream()
                    .filter(saved -> saved.getId() == event.getId())
                    .findFirst()
                    .orElseThrow();
            assertEquals("Integration Flow Test Updated", updated.getTitle());
            assertEquals(5, updated.getPriority());
            assertEquals("Updated description", updated.getDescription());
            assertEquals("Room B", updated.getLocation());

            assertTrue(manager.deleteEvent(event.getId()), "Expected event delete to succeed");
            assertTrue(
                    manager.getAllEvents().stream().noneMatch(saved -> saved.getId() == event.getId()),
                    "Expected deleted event to be absent from getAllEvents"
            );
        } finally {
            manager.disconnect();
            restoreProperty(previous);
        }
    }

    @Test
    void recurringSeriesCreatesMaterializedOccurrencesAndDeletesAsOneSeries() throws Exception {
        assertTrue(Files.exists(PROJECT_DB), "Expected source test database file to exist");

        Path testDb = tempDir.resolve("DATABASEFORJAVAFX_tmp_build.accdb");
        Files.copy(PROJECT_DB, testDb, StandardCopyOption.REPLACE_EXISTING);

        String previous = System.getProperty(DB_PROPERTY);
        System.setProperty(DB_PROPERTY, testDb.toString());

        DatabaseManager manager = new DatabaseManager();
        try {
            manager.connect();

            List<User> users = manager.getAllUsers();
            assertFalse(users.isEmpty(), "Expected at least one user in copied database");

            LocalDateTime start = LocalDateTime.of(2026, 3, 18, 5, 0);
            RecurringEventSeries series = new RecurringEventSeries(
                    0,
                    "Daily Football",
                    start,
                    start.plusHours(1),
                    2,
                    "Morning routine",
                    "Field",
                    RecurringEventSeries.DAILY,
                    start.plusDays(4)
            );

            int insertedCount = manager.createRecurringEventSeries(series, List.of(users.get(0)));
            assertEquals(5, insertedCount, "Expected five daily occurrences to be materialized");
            assertNotEquals(0, series.getRecurrenceId(), "Expected recurring series to receive an id");

            List<Event> recurringEvents = manager.getEventsByRecurrenceId(series.getRecurrenceId());
            assertEquals(5, recurringEvents.size(), "Expected all series events to be queryable by recurrence id");
            assertTrue(recurringEvents.stream().allMatch(event -> event.getRecurrenceId() != null));

            List<User> firstParticipants = manager.getEventParticipants(recurringEvents.get(0).getId());
            assertTrue(
                    firstParticipants.stream().anyMatch(user -> user.getUserId() == users.get(0).getUserId()),
                    "Expected recurring participants to be copied to materialized events"
            );

            assertTrue(manager.deleteRecurringSeries(series.getRecurrenceId()), "Expected recurring series delete to succeed");
            assertTrue(
                    manager.getEventsByRecurrenceId(series.getRecurrenceId()).isEmpty(),
                    "Expected recurring events to be removed after deleting the series"
            );
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
