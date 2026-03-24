package com.example.demo.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseManagerPathSelectionTest {

    @TempDir
    Path tempDir;

    @Test
    void selectPreferredExistingDatabasePathReturnsFirstExistingCandidate() throws Exception {
        Path missing = tempDir.resolve("missing.accdb");
        Path preferred = tempDir.resolve("preferred.accdb");
        Path fallback = tempDir.resolve("fallback.accdb");

        Files.createFile(preferred);
        Files.createFile(fallback);

        var selected = DatabaseManager.selectPreferredExistingDatabasePath(
                List.of(missing, preferred, fallback)
        );

        assertTrue(selected.isPresent());
        assertEquals(preferred, selected.get());
    }
}
