package dev.vexsoft.core.signal;

import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.VexService;
import dev.vexsoft.core.api.signal.SignalDispatchResult;
import dev.vexsoft.core.api.signal.SignalListener;
import dev.vexsoft.core.api.signal.SignalSubscription;
import dev.vexsoft.core.api.signal.VexSignal;
import dev.vexsoft.core.api.signal.SignalService;
import net.kyori.adventure.key.Key;

/**
 * Stores signal listeners from every service owner and dispatches signals through cached routes.
 *
 * <p>This infrastructure service is shared globally. Plugins normally interact with their scoped
 * {@link SignalService} instead.</p>
 */
public interface SignalRegistryService extends VexService {

  /** Registers a typed listener belonging to the supplied service owner. */
  <S extends VexSignal> SignalSubscription subscribe(
      ServiceOwner owner,
      Class<S> signalType,
      SignalListener<? super S> listener
  );

  /** Registers a key-based listener belonging to the supplied service owner. */
  SignalSubscription subscribe(
      ServiceOwner owner,
      Key signalKey,
      SignalListener<VexSignal> listener
  );

  /** Validates and synchronously dispatches a signal through its cached route. */
  SignalDispatchResult publish(VexSignal signal);

  /** Removes every listener registered by the supplied service owner. */
  void unsubscribeAll(ServiceOwner owner);
}
