package dev.vexsoft.core.data;

import dev.vexsoft.core.api.player.PlayerService;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Dependencies(PlayerDataCoordinatorService.class)
public final class VexPlayerService implements PlayerService {

  private final PlayerDataCoordinatorService coordinator;

  public VexPlayerService(final VexServiceRegistry services) {
    this.coordinator = Objects.requireNonNull(services, "services")
        .require(PlayerDataCoordinatorService.class);
  }

  @Override
  public Optional<VexPlayer> find(final UUID uniqueId) {
    return coordinator.find(uniqueId);
  }

  @Override
  public VexPlayer require(final UUID uniqueId) {
    return find(uniqueId).orElseThrow(
        () -> new IllegalStateException("VexPlayer is not loaded: " + uniqueId)
    );
  }

  @Override
  public CompletableFuture<Void> save(final VexPlayer player) {
    return coordinator.save(player);
  }
}
