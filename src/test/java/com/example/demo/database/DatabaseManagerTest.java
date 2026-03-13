package com.example.demo.database;

import com.example.demo.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static void restoreProperty(String previous) {
        if (previous == null) {
            System.clearProperty(DB_PROPERTY);
        } else {
            System.setProperty(DB_PROPERTY, previous);
        }
    }
}
