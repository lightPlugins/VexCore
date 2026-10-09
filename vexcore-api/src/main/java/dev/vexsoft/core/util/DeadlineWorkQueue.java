package dev.vexsoft.core.util;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.function.Consumer;

/** Single-owner deadline queue with replacement scheduling and bounded due-time selection. */
public final class DeadlineWorkQueue<T> {

    private final PriorityQueue<Entry<T>> queue = new PriorityQueue<>(Comparator.comparingLong(Entry::deadline));
    private final Map<T, Entry<T>> scheduled = new HashMap<>();

    /** Replaces the item's previous deadline without mutating heap entries. */
    public void schedule(final T item, final long deadline) {
        Entry<T> entry = new Entry<>(Objects.requireNonNull(item, "item"), deadline);
        scheduled.put(item, entry);
        queue.add(entry);
    }

    /** Returns the number of distinct scheduled items. */
    public int size() {
        return scheduled.size();
    }

    /** Removes all scheduled and stale heap entries. */
    public void clear() {
        scheduled.clear();
        queue.clear();
    }

    /** Selects at most the inspection limit, counting stale entries towards the work budget. */
    public int drainDue(final long now, final int inspectionLimit, final Consumer<T> operation) {
        if (inspectionLimit < 1) {
            throw new IllegalArgumentException("Deadline inspection limit must be positive");
        }
        int delivered = 0;
        for (int inspected = 0; inspected < inspectionLimit && !queue.isEmpty()
            && queue.peek().deadline() <= now; inspected++) {
            Entry<T> entry = queue.remove();
            if (scheduled.remove(entry.item(), entry)) {
                operation.accept(entry.item());
                delivered++;
            }
        }
        return delivered;
    }

    private record Entry<T>(T item, long deadline) {
    }
}
