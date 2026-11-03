package dev.vexsoft.core.paper.vexui;

/** Idempotent cancellation of a UI listener or lifecycle registration. */
@FunctionalInterface
public interface VexUISubscription extends AutoCloseable {

    /** Unregisters this listener without changing the observed value. */
    @Override
    void close();
}
