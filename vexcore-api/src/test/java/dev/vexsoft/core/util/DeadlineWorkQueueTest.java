package dev.vexsoft.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Protects retry ordering and a bounded global due-time selection. */
final class DeadlineWorkQueueTest {

    @Test
    void replacementDeadlinesDoNotMutateHeapOrderOrDeliverTwice() {
        var queue = new DeadlineWorkQueue<String>();
        List<String> results = new ArrayList<>();
        queue.schedule("coal", 10);
        queue.schedule("wheat", 20);
        queue.schedule("coal", 30);
        queue.drainDue(20, 10, results::add);
        assertEquals(List.of("wheat"), results);
        queue.drainDue(30, 10, results::add);
        assertEquals(List.of("wheat", "coal"), results);
        assertEquals(0, queue.size());
    }

    @Test
    void selectionLimitsAlsoCountObsoleteEntries() {
        var queue = new DeadlineWorkQueue<String>();
        List<String> results = new ArrayList<>();
        for (int deadline = 0; deadline < 100; deadline++) {
            queue.schedule("coal", deadline);
        }
        assertEquals(0, queue.drainDue(100, 5, results::add));
        assertEquals(1, queue.size());
        assertEquals(1, queue.drainDue(100, 100, results::add));
        assertEquals(List.of("coal"), results);
    }
}
