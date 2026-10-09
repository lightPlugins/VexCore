package dev.vexsoft.core.util;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.function.Consumer;

/** Single-owner FIFO work queue bounded by both an item count and elapsed execution time. */
public final class BudgetedWorkQueue<T> {

    private final ArrayDeque<T> entries = new ArrayDeque<>();

    /** Creates an empty queue. */
    public BudgetedWorkQueue() {
    }

    /** Adds one nonnull work item. */
    public void add(final T entry) {
        entries.addLast(Objects.requireNonNull(entry, "entry"));
    }

    /** Returns the queued item count. */
    public int size() {
        return entries.size();
    }

    /** Removes all queued work. */
    public void clear() {
        entries.clear();
    }

    /** Runs at most the supplied count and time budget; unprocessed entries retain their order. */
    public int drain(final int maximum, final long budgetNanos, final Consumer<T> operation) {
        if (maximum < 1 || budgetNanos < 1) {
            throw new IllegalArgumentException("Work budgets must be positive");
        }
        long started = System.nanoTime();
        int completed = 0;
        while (!entries.isEmpty() && completed < maximum && System.nanoTime() - started < budgetNanos) {
            operation.accept(entries.removeFirst());
            completed++;
        }
        return completed;
    }
}
