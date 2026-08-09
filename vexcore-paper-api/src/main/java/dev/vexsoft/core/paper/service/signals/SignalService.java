package dev.vexsoft.core.paper.service.signals;

import dev.vexsoft.core.paper.signals.SignalDispatchResult;
import dev.vexsoft.core.paper.signals.SignalListener;
import dev.vexsoft.core.paper.signals.SignalSubscription;
import dev.vexsoft.core.paper.signals.VexSignal;

import dev.vexsoft.core.api.service.registry.VexService;
import net.kyori.adventure.key.Key;

/**
 * Publishes local signals and manages listeners owned by the current service scope.
 */
public interface SignalService extends VexService {

  /**
   * Registers a listener for one exact concrete Java signal type.
   *
   * <p>Interfaces and abstract classes are rejected to prevent broad wildcard subscriptions.</p>
   */
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
