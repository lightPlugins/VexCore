package dev.vexsoft.core.common.data.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.vexsoft.core.common.data.sql.JdbcSharedDatabase;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies the generic durable transaction boundary independently of feature-specific SQL. */
final class JdbcSharedDatabaseTest {

    @TempDir
    Path directory;

    @Test
    void commitsBinaryDataOffThreadAndRollsBackPartialChanges() throws Exception {
        String url = "jdbc:sqlite:" + directory.resolve("shared.db");
        long caller = Thread.currentThread().threadId();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var database = new JdbcSharedDatabase(() -> DriverManager.getConnection(url), executor);
            database.transaction(connection -> {
                assertNotEquals(caller, Thread.currentThread().threadId());
                try (var statement = connection.createStatement()) {
                    statement.executeUpdate("CREATE TABLE payloads (id INT PRIMARY KEY, data BLOB NOT NULL)");
                }
                return null;
            }).join();
            byte[] bytes = {0, -1, 17, -128};
            database.transaction(connection -> {
                try (var insert = connection.prepareStatement("INSERT INTO payloads VALUES (?,?)")) {
                    insert.setInt(1, 1);
                    insert.setBytes(2, bytes);
                    insert.executeUpdate();
                }
                return null;
            }).join();
            assertThrows(CompletionException.class, () -> database.transaction(connection -> {
                try (var statement = connection.createStatement()) {
                    statement.executeUpdate("DELETE FROM payloads");
                }
                throw new IllegalStateException("abort");
            }).join());
            try (var connection = DriverManager.getConnection(url);
                var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT data FROM payloads")) {
                rows.next();
                assertArrayEquals(bytes, rows.getBytes(1));
            }
        }
    }

    @Test
    void aRejectedExecutorDoesNotLeakTheBoundedQueueCapacity() {
        var database = new JdbcSharedDatabase(() -> {
            throw new AssertionError("Rejected work must never open a connection");
        }, command -> {
            throw new IllegalStateException("closed executor");
        });
        for (int index = 0; index < 300; index++) {
            var failure = assertThrows(CompletionException.class, () -> database.transaction(connection -> null).join());
            assertEquals("closed executor", failure.getCause().getMessage());
        }
    }

    @Test
    void cancellationBeforeExecutionStillReleasesItsQueueSlot() {
        var queued = new ArrayList<Runnable>();
        var database = new JdbcSharedDatabase(() -> {
            throw new AssertionError("Cancelled queued work must not open a connection");
        }, queued::add);
        for (int index = 0; index < 300; index++) {
            var future = database.transaction(connection -> null);
            future.cancel(false);
            assertEquals(1, queued.size());
            queued.removeFirst().run();
        }
    }
}
