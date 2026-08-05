package dev.vexsoft.core.paper.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vexsoft.core.api.configuration.ConfigurationService;
import dev.vexsoft.core.api.service.ServiceOwner;
import dev.vexsoft.core.api.service.ServiceReference;
import dev.vexsoft.core.api.service.VexService;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.configuration.VexConfigurationService;
import dev.vexsoft.core.paper.inventory.VexInventoryListener;
import dev.vexsoft.core.paper.listener.ListenerService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.Test;

public final class VexPluginBootstrapServiceTest {

  @Test
  public void queuesInfrastructureAndStartsEnabledListeners() {
    TestServices services = new TestServices();
    VexPluginBootstrapService bootstrap = new VexPluginBootstrapService(services);

    bootstrap.initialize(services);
    bootstrap.enable(services);

    assertEquals(
        VexConfigurationService.class,
        services.definitions.get(ConfigurationService.class)
    );
    assertEquals(VexInventoryListener.class, services.listenerType);
  }

  private static final class TestServices implements VexServiceRegistry, ServiceOwner {

    private final Map<Class<? extends VexService>, Class<? extends VexService>> definitions =
        new LinkedHashMap<>();
    private final ListenerService listeners = new ListenerService() {
      @Override
      public <T extends Listener> T register(final Class<T> listenerType) {
        TestServices.this.listenerType = listenerType;
        return null;
      }

      @Override
      public void unregisterAll() { }
    };
    private Class<? extends Listener> listenerType;

    @Override
    public ServiceOwner getOwner() {
      return this;
    }

    @Override
    public String getServiceOwnerName() {
      return "VexPluginBootstrapTest";
    }

    @Override
    public <T extends VexService> void register(
        final Class<T> serviceType,
        final Class<? extends T> implementationType
    ) {
      definitions.put(serviceType, implementationType);
    }

    @Override
    public void registerQueuedServices() { }

    @Override
    public <T extends VexService> Optional<T> find(final Class<T> serviceType) {
      if (serviceType == ListenerService.class) {
        return Optional.of(serviceType.cast(listeners));
      }
      return Optional.empty();
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
    public void unregister(final Class<? extends VexService> serviceType) { }

    @Override
    public void unregisterOwnedServices() { }
  }
}
