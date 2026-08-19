package dev.vexsoft.core.paper.service.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vexsoft.core.api.service.configuration.ConfigurationService;
import dev.vexsoft.core.api.service.currency.CurrencyRegistry;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.ServiceReference;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.common.service.configuration.VexConfigurationService;
import dev.vexsoft.core.common.service.currency.VexCurrencyRegistry;
import dev.vexsoft.core.paper.packets.service.BlockDisplayPacketService;
import dev.vexsoft.core.paper.packets.service.InteractionPacketService;
import dev.vexsoft.core.paper.packets.service.PlayerAnimationPacketService;
import dev.vexsoft.core.paper.service.packets.VexBlockDisplayPacketService;
import dev.vexsoft.core.paper.service.packets.VexInteractionPacketService;
import dev.vexsoft.core.paper.service.packets.VexPlayerAnimationPacketService;
import dev.vexsoft.core.paper.service.inventory.VexInventoryListener;
import dev.vexsoft.core.paper.service.listeners.ListenerService;
import dev.vexsoft.core.paper.service.placeholder.PlaceholderApiBridgeService;
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
    assertEquals(VexCurrencyRegistry.class, services.definitions.get(CurrencyRegistry.class));
    assertEquals(VexInventoryListener.class, services.listenerType);
    assertEquals(
        VexBlockDisplayPacketService.class,
        services.definitions.get(BlockDisplayPacketService.class)
    );
    assertEquals(
        VexInteractionPacketService.class,
        services.definitions.get(InteractionPacketService.class)
    );
    assertEquals(
        VexPlayerAnimationPacketService.class,
        services.definitions.get(PlayerAnimationPacketService.class)
    );
  }

  private static final class TestServices implements VexServiceRegistry, ServiceOwner {

    private final Map<Class<? extends VexService>, Class<? extends VexService>> definitions =
        new LinkedHashMap<>();
    private final ListenerService listeners = new ListenerService() {
      @Override
      public <T extends Listener> T register(
          final Class<T> listenerType,
          final VexServiceRegistry services
      ) {
        TestServices.this.listenerType = listenerType;
        return null;
      }

      @Override
      public void unregisterAll() { }
    };
    private final PlaceholderApiBridgeService placeholders = () -> { };
    private Class<? extends Listener> listenerType;

    @Override
    public ServiceOwner getOwner() {
      return this;
    }

    @Override
    public VexServiceRegistry scoped(final ServiceOwner owner) {
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
      if (serviceType == PlaceholderApiBridgeService.class) {
        return Optional.of(serviceType.cast(placeholders));
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
