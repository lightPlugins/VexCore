package dev.vexsoft.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies that a large recovery backlog can be spread across ticks without losing entries. */
final class BudgetedWorkQueueTest {

    @Test
    void slicesARecoveryBacklogInOrder() {
        var queue = new BudgetedWorkQueue<Integer>();
        List<Integer> restored = new ArrayList<>();
        for (int position = 0; position < 1000; position++) {
            queue.add(position);
        }
        queue.drain(5, Long.MAX_VALUE, restored::add);
        assertEquals(List.of(0, 1, 2, 3, 4), restored);
        assertEquals(995, queue.size());
        while (queue.size() > 0) {
            queue.drain(10, Long.MAX_VALUE, restored::add);
        }
        assertEquals(1000, restored.size());
        assertEquals(999, restored.getLast());
    }
}
