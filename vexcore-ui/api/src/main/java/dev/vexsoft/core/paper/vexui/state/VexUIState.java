package dev.vexsoft.core.paper.vexui.state;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/** Thread-safe observable UI state; values must be immutable and equal values do not trigger refreshes. */
public final class VexUIState<T> implements VexUIObservable<T> {

    private final AtomicReference<T> value;
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final VexUIObservable<T> observable = new VexUIObservable<>() {
        @Override
        public T get() {
            return VexUIState.this.get();
        }

        @Override
        public VexUISubscription subscribe(final Runnable changed) {
            return VexUIState.this.subscribe(changed);
        }
    };

    /** Creates observable state with a nonnull initial snapshot. */
    public VexUIState(final T initial) {
        value = new AtomicReference<>(Objects.requireNonNull(initial, "initial"));
    }

    @Override
    public T get() {
        return value.get();
    }

    /** Returns a stable observable facade without exposing mutation methods. */
    public VexUIObservable<T> readOnly() {
        return observable;
    }

    /** Replaces this value and notifies subscribers only when equality changes. */
    public void set(final T next) {
        update(ignored -> next);
    }

    /** Atomically replaces an identical expected snapshot and notifies listeners when equality changes. */
    public boolean compareAndSet(final T expected, final T next) {
        Objects.requireNonNull(next, "next");
        if (!value.compareAndSet(expected, next)) {
            return false;
        }
        if (!Objects.equals(expected, next)) {
            notifyListeners();
        }
        return true;
    }

    /** Atomically updates the value; the pure updater may be retried under contention. */
    public void update(final UnaryOperator<T> updater) {
        Objects.requireNonNull(updater, "updater");
        T previous;
        T next;
        do {
            previous = value.get();
            next = Objects.requireNonNull(updater.apply(previous), "updated value");
            if (Objects.equals(previous, next)) {
                return;
            }
        } while (!compareAndSet(previous, next));
    }

    @Override
    public VexUISubscription subscribe(final Runnable changed) {
        Objects.requireNonNull(changed, "changed");
        // A distinct registration lets the same callback be subscribed and retired independently.
        Runnable registration = changed::run;
        listeners.add(registration);
        return () -> listeners.remove(registration);
    }

    private void notifyListeners() {
        RuntimeException failure = null;
        for (var listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
