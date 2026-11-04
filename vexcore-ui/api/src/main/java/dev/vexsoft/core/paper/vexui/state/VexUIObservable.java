package dev.vexsoft.core.paper.vexui.state;

/** Observable service snapshot; callbacks may originate on any thread and must not mutate Bukkit state. */
public interface VexUIObservable<T> {

    /** Returns the current immutable snapshot. */
    T get();

    /** Registers change notification without an initial callback; callers subscribe before reading. */
    VexUISubscription subscribe(Runnable changed);
}
