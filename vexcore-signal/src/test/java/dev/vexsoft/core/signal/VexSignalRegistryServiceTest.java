package dev.vexsoft.core.signal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.ServiceReference;
import dev.vexsoft.core.api.service.VexService;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.api.signal.SignalAttributes;
import dev.vexsoft.core.api.signal.SignalDispatchResult;
import dev.vexsoft.core.api.signal.SignalService;
import dev.vexsoft.core.api.signal.SignalSubscription;
import dev.vexsoft.core.api.signal.VexSignal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

class VexSignalRegistryServiceTest {

  private static final Key TEST_KEY = Key.key("test", "progress");

  @Test
  void dispatchesCachedTypeAndKeyRoutesInRegistrationOrder() {
    TestOwner owner = new TestOwner("TestPlugin");
    VexSignalRegistryService registry = new VexSignalRegistryService(new TestServices(owner, null));
    SignalService signals = new VexSignalService(new TestServices(owner, registry));
    List<String> calls = new ArrayList<>();
    signals.subscribe(TestSignal.class, signal -> calls.add("type:" + signal.getAmount()));

    assertEquals(1, signals.publish(signal(2L)).delivered());
    signals.subscribe(TEST_KEY, signal -> calls.add("key:" + signal.getAmount()));
    SignalDispatchResult result = signals.publish(signal(3L));

    assertEquals(List.of("type:2", "type:3", "key:3"), calls);
    assertEquals(2, result.delivered());
    assertEquals(0, result.failed());
  }

  @Test
  void validatesPositiveAmountsAndRequiredFields() {
    TestOwner owner = new TestOwner("TestPlugin");
    VexSignalRegistryService registry = new VexSignalRegistryService(new TestServices(owner, null));

    assertThrows(IllegalArgumentException.class, () -> registry.publish(signal(0L)));
    assertThrows(IllegalArgumentException.class, () -> registry.publish(signal(-1L)));
    assertThrows(
        IllegalArgumentException.class,
        () -> registry.subscribe(owner, VexSignal.class, ignored -> {
        })
    );
  }

  @Test
  void closesIndividualAndOwnerSubscriptions() {
    TestOwner owner = new TestOwner("TestPlugin");
    VexSignalRegistryService registry = new VexSignalRegistryService(new TestServices(owner, null));
    VexSignalService signals = new VexSignalService(new TestServices(owner, registry));
    SignalSubscription subscription = signals.subscribe(TestSignal.class, ignored -> {
    });

    assertEquals(1, signals.publish(signal(1L)).delivered());
    subscription.close();
    assertFalse(subscription.isActive());
    assertEquals(0, signals.publish(signal(1L)).delivered());

    SignalSubscription second = signals.subscribe(TEST_KEY, ignored -> {
    });
    signals.unsubscribeAll();
    assertFalse(second.isActive());
    assertEquals(0, signals.publish(signal(1L)).delivered());
  }

  @Test
  void isolatesFailingListeners() {
    TestOwner owner = new TestOwner("TestPlugin");
    VexSignalRegistryService registry = new VexSignalRegistryService(new TestServices(owner, null));
    SignalService signals = new VexSignalService(new TestServices(owner, registry));
    signals.subscribe(TEST_KEY, ignored -> {
      throw new IllegalStateException("expected test failure");
    });
    signals.subscribe(TEST_KEY, ignored -> {
    });

    SignalDispatchResult result = signals.publish(signal(1L));

    assertEquals(1, result.delivered());
    assertEquals(1, result.failed());
    assertFalse(result.isSuccessful());
    assertEquals(2, result.getListenerCount());
  }

  private TestSignal signal(final long amount) {
    return new TestSignal(amount);
  }

  private record TestSignal(long amount) implements VexSignal {

    @Override
    public Key getKey() {
      return TEST_KEY;
    }

    @Override
    public Optional<UUID> getSubject() {
      return Optional.empty();
    }

    @Override
    public long getAmount() {
      return amount;
    }

    @Override
    public SignalAttributes getAttributes() {
      return SignalAttributes.empty();
    }
  }

  private record TestOwner(String serviceOwnerName) implements ServiceOwner {

    @Override
    public String getServiceOwnerName() {
      return serviceOwnerName;
    }
  }

  private static final class TestServices implements VexServiceRegistry {

    private final ServiceOwner owner;
    private final SignalRegistryService registry;

    private TestServices(final ServiceOwner owner, final SignalRegistryService registry) {
      this.owner = owner;
      this.registry = registry;
    }

    @Override
    public ServiceOwner getOwner() {
      return owner;
    }

    @Override
    public VexServiceRegistry scoped(final ServiceOwner childOwner) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T extends VexService> void register(
        final Class<T> serviceType,
        final Class<? extends T> implementationType
    ) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void registerQueuedServices() {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T extends VexService> Optional<T> find(final Class<T> serviceType) {
      return serviceType == SignalRegistryService.class && registry != null
          ? Optional.of(serviceType.cast(registry))
          : Optional.empty();
    }

    @Override
    public <T extends VexService> T require(final Class<T> serviceType) {
      return find(serviceType).orElseThrow();
    }

    @Override
    public <T extends VexService> ServiceReference<T> reference(final Class<T> serviceType) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isAvailable(final Class<? extends VexService> serviceType) {
      return find(serviceType).isPresent();
    }

    @Override
    public void unregister(final Class<? extends VexService> serviceType) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void unregisterOwnedServices() {
      throw new UnsupportedOperationException();
    }
  }
}
