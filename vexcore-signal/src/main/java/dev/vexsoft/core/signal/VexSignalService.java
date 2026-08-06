package dev.vexsoft.core.signal;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.api.signal.SignalDispatchResult;
import dev.vexsoft.core.api.signal.SignalListener;
import dev.vexsoft.core.api.signal.SignalService;
import dev.vexsoft.core.api.signal.SignalSubscription;
import dev.vexsoft.core.api.signal.VexSignal;
import java.util.Objects;
import net.kyori.adventure.key.Key;

/**
 * Provides an owner-scoped view of the global local signal registry.
 *
 * <p>Subscriptions created through this service belong to its registry owner and are removed when
 * the service closes.</p>
 */
@Dependencies(SignalRegistryService.class)
public final class VexSignalService implements SignalService, AutoCloseable {

  private final ServiceOwner owner;
  private final SignalRegistryService registry;
  private boolean closed;

  /**
   * Creates an owner-scoped signal service.
   *
   * @param services registry used to determine the owner and resolve signal infrastructure
   */
  public VexSignalService(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    owner = checkedServices.getOwner();
    registry = checkedServices.require(SignalRegistryService.class);
  }

  @Override
  public synchronized <S extends VexSignal> SignalSubscription subscribe(
      final Class<S> signalType,
      final SignalListener<? super S> listener
  ) {
    requireOpen();
    return registry.subscribe(owner, signalType, listener);
  }

  @Override
  public synchronized SignalSubscription subscribe(
      final Key signalKey,
      final SignalListener<VexSignal> listener
  ) {
    requireOpen();
    return registry.subscribe(owner, signalKey, listener);
  }

  @Override
  public SignalDispatchResult publish(final VexSignal signal) {
    return registry.publish(signal);
  }

  @Override
  public synchronized void unsubscribeAll() {
    registry.unsubscribeAll(owner);
  }

  @Override
  public synchronized void close() {
    if (!closed) {
      closed = true;
      unsubscribeAll();
    }
  }

  private void requireOpen() {
    if (closed) {
      throw new IllegalStateException("SignalService is already closed");
    }
  }
}
