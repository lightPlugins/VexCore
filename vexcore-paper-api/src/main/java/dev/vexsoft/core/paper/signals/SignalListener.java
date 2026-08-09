package dev.vexsoft.core.paper.signals;

/**
 * Receives synchronously published local signals.
 *
 * @param <S> signal type accepted by this listener
 */
@FunctionalInterface
public interface SignalListener<S extends VexSignal> {

  /** Handles one published signal on the publishing thread. */
  void handle(S signal);
}
