package dev.vexsoft.core.common.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Checks crash leftovers and malformed origins without silently generating replacement state. */
final class AtomicJsonFileTest {

    @TempDir
    Path directory;

    @Test
    void publishesOnlyCompleteSnapshotsAndIgnoresAnInterruptedTemporaryWrite() throws Exception {
        Path path = directory.resolve("region/chunk.json");
        AtomicJsonFile<Snapshot> store = new AtomicJsonFile<>(path, Snapshot.class, new ObjectMapper());
        assertFalse(store.read().isPresent());
        Snapshot initial = new Snapshot(List.of("coal", "cocoa-east"));
        store.write(initial);
        Files.writeString(path.resolveSibling("chunk.json.tmp"), "{interrupted");
        assertEquals(initial, store.read().orElseThrow());
        Snapshot updated = new Snapshot(List.of("coal", "cocoa-east", "wheat"));
        store.write(updated);
        assertEquals(updated, store.read().orElseThrow());
        assertFalse(Files.exists(path.resolveSibling("chunk.json.tmp")));
    }

    @Test
    void failsOnCorruptPublishedOrigins() throws Exception {
        Path path = directory.resolve("chunk.json");
        Files.writeString(path, "{broken");
        var store = new AtomicJsonFile<>(path, Snapshot.class, new ObjectMapper());
        assertThrows(IOException.class, store::read);
    }

    public record Snapshot(List<String> origins) {
    }
}
