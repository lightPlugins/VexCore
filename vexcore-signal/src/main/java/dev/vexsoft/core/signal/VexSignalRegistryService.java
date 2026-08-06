package dev.vexsoft.core.signal;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.api.signal.SignalDispatchResult;
import dev.vexsoft.core.api.signal.SignalListener;
import dev.vexsoft.core.api.signal.SignalSubscription;
import dev.vexsoft.core.api.signal.VexSignal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.lang.reflect.Modifier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.key.Key;

/**
 * Dispatches local signals using immutable listener arrays cached by signal type and key.
 *
 * <p>Registration changes invalidate the route cache. Dispatches with an existing route use one
 * concurrent-map lookup followed by direct array iteration.</p>
 */
@Dependencies
public final class VexSignalRegistryService implements SignalRegistryService {

  private static final RegisteredListener[] EMPTY_ROUTE = new RegisteredListener[0];

  private final Object registrationLock = new Object();
  private final Map<Class<? extends VexSignal>, List<RegisteredListener>> typeListeners =
      new HashMap<>();
  private final Map<Key, List<RegisteredListener>> keyListeners = new HashMap<>();
  private final ConcurrentHashMap<RouteKey, RegisteredListener[]> routes = new ConcurrentHashMap<>();
  private final AtomicLong registrationIds = new AtomicLong();
  private final Logger logger = Logger.getLogger(VexSignalRegistryService.class.getName());

  /**
   * Creates the global local signal registry.
   *
   * @param services registry constructing this service
   */
  public VexSignalRegistryService(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
  }

  @Override
  public <S extends VexSignal> SignalSubscription subscribe(
      final ServiceOwner owner,
      final Class<S> signalType,
      final SignalListener<? super S> listener
  ) {
    Objects.requireNonNull(signalType, "signalType");
    Objects.requireNonNull(listener, "listener");
    if (signalType.isInterface() || Modifier.isAbstract(signalType.getModifiers())) {
      throw new IllegalArgumentException("Signal listener type must be concrete: " + signalType.getName());
    }
    return register(
        Objects.requireNonNull(owner, "owner"),
        signal -> listener.handle(signalType.cast(signal)),
        signalType,
        null
    );
  }

  @Override
  public SignalSubscription subscribe(
      final ServiceOwner owner,
      final Key signalKey,
      final SignalListener<VexSignal> listener
  ) {
    return register(
        Objects.requireNonNull(owner, "owner"),
        Objects.requireNonNull(listener, "listener")::handle,
        null,
        Objects.requireNonNull(signalKey, "signalKey")
    );
  }

  @Override
  public SignalDispatchResult publish(final VexSignal signal) {
    VexSignal checkedSignal = validate(signal);
    RouteKey routeKey = new RouteKey(checkedSignal.getClass(), checkedSignal.getKey());
    RegisteredListener[] listeners = findRoute(routeKey);
    int delivered = 0;
    int failed = 0;
    for (RegisteredListener listener : listeners) {
      if (!listener.isActive()) {
        continue;
      }
      try {
        listener.invoke(checkedSignal);
        delivered++;
      } catch (RuntimeException exception) {
        failed++;
        logger.log(
            Level.SEVERE,
            "Signal listener owned by " + listener.owner().getServiceOwnerName()
                + " failed for " + checkedSignal.getKey().asString(),
            exception
        );
      }
    }
    return new SignalDispatchResult(delivered, failed);
  }

  @Override
  public void unsubscribeAll(final ServiceOwner owner) {
    Objects.requireNonNull(owner, "owner");
    synchronized (registrationLock) {
      removeOwned(typeListeners, owner);
      removeOwned(keyListeners, owner);
      routes.clear();
    }
  }

  private SignalSubscription register(
      final ServiceOwner owner,
      final SignalInvoker invoker,
      final Class<? extends VexSignal> signalType,
      final Key signalKey
  ) {
    RegisteredListener registration = new RegisteredListener(
        registrationIds.incrementAndGet(),
        owner,
        invoker,
        signalType,
        signalKey,
        this
    );
    synchronized (registrationLock) {
      if (signalType != null) {
        typeListeners.computeIfAbsent(signalType, ignored -> new ArrayList<>()).add(registration);
      } else {
        keyListeners.computeIfAbsent(signalKey, ignored -> new ArrayList<>()).add(registration);
      }
      routes.clear();
    }
    return registration;
  }

  private void unregister(final RegisteredListener registration) {
    synchronized (registrationLock) {
      if (registration.signalType() != null) {
        remove(typeListeners, registration.signalType(), registration);
      } else {
        remove(keyListeners, registration.signalKey(), registration);
      }
      routes.clear();
    }
  }

  private RegisteredListener[] findRoute(final RouteKey route) {
    RegisteredListener[] cached = routes.get(route);
    if (cached != null) {
      return cached;
    }
    synchronized (registrationLock) {
      cached = routes.get(route);
      if (cached != null) {
        return cached;
      }
      RegisteredListener[] built = buildRoute(route);
      routes.put(route, built);
      return built;
    }
  }

  private RegisteredListener[] buildRoute(final RouteKey route) {
    List<RegisteredListener> matching = new ArrayList<>();
    matching.addAll(typeListeners.getOrDefault(route.signalType(), List.of()));
    matching.addAll(keyListeners.getOrDefault(route.signalKey(), List.of()));
    if (matching.isEmpty()) {
      return EMPTY_ROUTE;
    }
    matching.sort(Comparator.comparingLong(RegisteredListener::id));
    return matching.toArray(RegisteredListener[]::new);
  }

  private VexSignal validate(final VexSignal signal) {
    VexSignal checked = Objects.requireNonNull(signal, "signal");
    Objects.requireNonNull(checked.getKey(), "signal key");
    Optional<UUID> subject = Objects.requireNonNull(checked.getSubject(), "signal subject");
    subject.ifPresent(value -> Objects.requireNonNull(value, "signal subject value"));
    if (checked.getAmount() <= 0) {
      throw new IllegalArgumentException("Signal amount must be greater than zero");
    }
    Objects.requireNonNull(
        checked.getAttributes(),
        "signal attributes"
    );
    return checked;
  }

  private <K> void remove(
      final Map<K, List<RegisteredListener>> listeners,
      final K key,
      final RegisteredListener registration
  ) {
    List<RegisteredListener> registrations = listeners.get(key);
    if (registrations == null) {
      return;
    }
    registrations.remove(registration);
    if (registrations.isEmpty()) {
      listeners.remove(key);
    }
  }

  private <K> void removeOwned(
      final Map<K, List<RegisteredListener>> listeners,
      final ServiceOwner owner
  ) {
    listeners.values().forEach(registrations -> registrations.removeIf(registration -> {
      if (registration.owner() == owner) {
        registration.deactivate();
        return true;
      }
      return false;
    }));
    listeners.entrySet().removeIf(entry -> entry.getValue().isEmpty());
  }

  @FunctionalInterface
  private interface SignalInvoker {
    void invoke(VexSignal signal);
  }

  private record RouteKey(Class<? extends VexSignal> signalType, Key signalKey) {
  }

  private static final class RegisteredListener implements SignalSubscription {

    private final long id;
    private final ServiceOwner owner;
    private final SignalInvoker invoker;
    private final Class<? extends VexSignal> signalType;
    private final Key signalKey;
    private final VexSignalRegistryService registry;
    private final AtomicBoolean active = new AtomicBoolean(true);

    private RegisteredListener(
        final long id,
        final ServiceOwner owner,
        final SignalInvoker invoker,
        final Class<? extends VexSignal> signalType,
        final Key signalKey,
        final VexSignalRegistryService registry
    ) {
      this.id = id;
      this.owner = owner;
      this.invoker = invoker;
      this.signalType = signalType;
      this.signalKey = signalKey;
      this.registry = registry;
    }

    @Override
    public boolean isActive() {
      return active.get();
    }

    @Override
    public void close() {
      if (active.compareAndSet(true, false)) {
        registry.unregister(this);
      }
    }

    private void invoke(final VexSignal signal) {
      invoker.invoke(signal);
    }

    private void deactivate() {
      active.set(false);
    }

    private long id() {
      return id;
    }

    private ServiceOwner owner() {
      return owner;
    }

    private Class<? extends VexSignal> signalType() {
      return signalType;
    }

    private Key signalKey() {
      return signalKey;
    }
  }
}
