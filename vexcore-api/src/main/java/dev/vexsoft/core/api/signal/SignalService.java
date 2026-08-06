package dev.vexsoft.core.api.signal;

import dev.vexsoft.core.api.service.VexService;
import net.kyori.adventure.key.Key;

/**
 * Publishes local signals and manages listeners owned by the current service scope.
 */
public interface SignalService extends VexService {

  /** Registers a listener for a concrete Java signal type. */
  <S extends VexSignal> SignalSubscription subscribe(
      Class<S> signalType,
      SignalListener<? super S> listener
  );

  /** Registers a listener for a stable namespaced signal key. */
  SignalSubscription subscribe(Key signalKey, SignalListener<VexSignal> listener);

  /** Validates and synchronously dispatches a signal on the publishing thread. */
  SignalDispatchResult publish(VexSignal signal);

  /** Removes every signal listener owned by this service scope. */
  void unsubscribeAll();
}
