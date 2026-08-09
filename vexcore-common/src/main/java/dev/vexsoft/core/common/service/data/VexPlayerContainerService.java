package dev.vexsoft.core.common.service.data;

import dev.vexsoft.core.api.player.PlayerContainer;
import dev.vexsoft.core.api.player.PlayerContainerFactory;
import dev.vexsoft.core.api.service.player.PlayerContainerService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import java.util.Objects;

/** Owner-scoped facade for registering player feature containers. */
@Dependencies(PlayerDataCoordinatorService.class)
public final class VexPlayerContainerService implements PlayerContainerService, AutoCloseable {

  private final ServiceOwner owner;
  private final PlayerDataCoordinatorService coordinator;

  public VexPlayerContainerService(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    owner = checkedServices.getOwner();
    coordinator = checkedServices.require(PlayerDataCoordinatorService.class);
  }

  @Override
  public <T extends PlayerContainer> void register(
      final Class<T> type,
      final PlayerContainerFactory<? extends T> factory
  ) {
    coordinator.registerContainer(owner, type, factory);
  }

  @Override
  public void close() {
    coordinator.unregisterContainers(owner);
  }
}
