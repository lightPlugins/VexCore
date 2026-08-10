package dev.vexsoft.core.paper.service.directory;

import dev.vexsoft.core.api.network.NetworkPlayer;
import dev.vexsoft.core.api.service.network.PlayerDirectoryService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import java.util.Objects;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Owner-scoped facade for the shared Velocity player directory. */
@Dependencies(PlayerDirectoryCoordinatorService.class)
public final class VexPlayerDirectoryService implements PlayerDirectoryService {

  private final PlayerDirectoryCoordinatorService coordinator;

  public VexPlayerDirectoryService(final VexServiceRegistry services) {
    coordinator = Objects.requireNonNull(services, "services")
        .require(PlayerDirectoryCoordinatorService.class);
  }

  @Override
  public CompletableFuture<Optional<NetworkPlayer>> find(final UUID uniqueId) {
    return coordinator.find(uniqueId);
  }

  @Override
  public List<NetworkPlayer> getOnlinePlayers() {
    return coordinator.getOnlinePlayers();
  }
}
