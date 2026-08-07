package dev.vexsoft.core.paper.module;

import dev.vexsoft.core.api.player.DataService;
import dev.vexsoft.core.api.player.PlayerContainerService;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.data.VexDataService;
import dev.vexsoft.core.data.VexPlayerContainerService;
import dev.vexsoft.core.gameplay.stat.GameplayPlayerData;
import dev.vexsoft.core.gameplay.stat.StatContainer;
import dev.vexsoft.core.gameplay.stat.StatRegistry;
import dev.vexsoft.core.gameplay.stat.StatRegistryCoordinatorService;
import dev.vexsoft.core.gameplay.stat.VexStatContainer;
import dev.vexsoft.core.gameplay.stat.VexStatRegistry;
import dev.vexsoft.core.gameplay.stat.VexStatRegistryCoordinatorService;

/** Installs the shared stat runtime and its player container. */
public final class GameplayModule implements VexModule {

  private VexServiceRegistry services;

  @Override
  public void enable(final VexServiceRegistry registry) {
    services = registry.scoped(this);
    services.register(DataService.class, VexDataService.class);
    services.register(PlayerContainerService.class, VexPlayerContainerService.class);
    services.register(
        StatRegistryCoordinatorService.class,
        VexStatRegistryCoordinatorService.class
    );
    services.register(StatRegistry.class, VexStatRegistry.class);
    services.registerQueuedServices();
    services.require(DataService.class).register(GameplayPlayerData.class);
    StatRegistryCoordinatorService coordinator = services.require(
        StatRegistryCoordinatorService.class
    );
    services.require(PlayerContainerService.class).register(
        StatContainer.class,
        player -> new VexStatContainer(player, coordinator)
    );
  }

  @Override
  public void disable() {
    if (services != null) {
      services.unregisterOwnedServices();
    }
  }

  @Override
  public String getServiceOwnerName() {
    return "vexcore_gameplay";
  }
}
