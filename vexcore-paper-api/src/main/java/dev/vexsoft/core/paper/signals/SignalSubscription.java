package dev.vexsoft.core.paper.signals;

/**
 * Represents an active signal-listener registration.
 */
public interface SignalSubscription extends AutoCloseable {

  /** Returns whether this subscription is still active. */
  boolean isActive();

  /** Removes the listener from the signal service. */
  @Override
  void close();
}
