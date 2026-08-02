package dev.vexsoft.core.data;

import dev.vexsoft.core.api.player.DataService;
import dev.vexsoft.core.api.player.PlayerDataDefinition;
import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexClassFactory;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import java.util.Objects;

@Dependencies(PlayerDataCoordinatorService.class)
public final class VexDataService implements DataService {

  private final VexServiceRegistry services;
  private final PlayerDataCoordinatorService coordinator;

  public VexDataService(final VexServiceRegistry services) {
    this.services = Objects.requireNonNull(services, "services");
    this.coordinator = services.require(PlayerDataCoordinatorService.class);
  }

  @Override
  public void register(final Class<? extends PlayerDataDefinition> definitionType) {
    PlayerDataDefinition definition = VexClassFactory.create(
        definitionType,
        services,
        "Player data definition"
    );
    coordinator.register(services.getOwner(), definition);
  }
}
