package com.example.demo;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainViewSmokeTest {

    private static final String DB_PROPERTY = "smart.diary.db.path";
    private static final Path PROJECT_DB = Path.of(
            "C:\\Users\\Lenovo\\OneDrive\\Desktop\\JAVAPROJECTCOMPLETE SAGE\\DATABASEFORJAVAFX_tmp_build.accdb"
    );

    @TempDir
    Path tempDir;

    @BeforeAll
    static void startFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.startup(latch::countDown);
        assertTrue(latch.await(10, TimeUnit.SECONDS), "JavaFX platform failed to start");
    }

    @Test
    void mainViewLoadsAndInjectsRequiredControls() throws Exception {
        assertTrue(Files.exists(PROJECT_DB), "Expected test database file to exist");

        Path testDb = tempDir.resolve("DATABASEFORJAVAFX_tmp_build.accdb");
        Files.copy(PROJECT_DB, testDb, StandardCopyOption.REPLACE_EXISTING);

        String previous = System.getProperty(DB_PROPERTY);
        System.setProperty(DB_PROPERTY, testDb.toString());

        try {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Throwable> errorRef = new AtomicReference<>();
            AtomicReference<FXMLLoader> loaderRef = new AtomicReference<>();
            AtomicReference<Parent> rootRef = new AtomicReference<>();

            Platform.runLater(() -> {
                try {
                    FXMLLoader loader = new FXMLLoader(SmartCalenderApp.class.getResource("MainView.fxml"));
                    Parent root = loader.load();
                    loaderRef.set(loader);
                    rootRef.set(root);
                } catch (Throwable t) {
                    errorRef.set(t);
                } finally {
                    latch.countDown();
                }
            });

            assertTrue(latch.await(10, TimeUnit.SECONDS), "FXML load timed out");
            assertTrue(errorRef.get() == null, () -> "FXML load failed: " + errorRef.get());
            assertNotNull(rootRef.get());
            assertNotNull(loaderRef.get().getController());

            Map<String, Object> namespace = loaderRef.get().getNamespace();
            assertNotNull(namespace.get("participantsCombo"));
            assertNotNull(namespace.get("descriptionField"));
            assertNotNull(namespace.get("eventsListView"));
            assertNotNull(namespace.get("calendarGrid"));
            assertNotNull(namespace.get("recurrenceCombo"));
            assertNotNull(namespace.get("repeatUntilCombo"));

            ComboBox<?> participantsCombo = (ComboBox<?>) namespace.get("participantsCombo");
            ComboBox<?> priorityCombo = (ComboBox<?>) namespace.get("priorityCombo");
            ComboBox<?> recurrenceCombo = (ComboBox<?>) namespace.get("recurrenceCombo");
            assertTrue(!participantsCombo.getItems().isEmpty(), "Expected participants to be loaded");
            assertEquals(5, priorityCombo.getItems().size(), "Expected all priority levels to be available");
            assertEquals(4, recurrenceCombo.getItems().size(), "Expected recurring options to be available");
        } finally {
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
