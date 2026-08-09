package dev.vexsoft.core.common.service.stats;

import dev.vexsoft.core.api.player.DataContainerKey;
import dev.vexsoft.core.api.player.DataContainerRegistry;
import dev.vexsoft.core.api.player.PlayerDataDefinition;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import java.util.Objects;

/** Declares the persistent values backing player stats. */
@Dependencies
public final class GameplayPlayerData implements PlayerDataDefinition {

  static final DataContainerKey<StatData> STATS = DataContainerKey.of(
      "stats",
      StatData.class,
      StatData::new
  );

  public GameplayPlayerData(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
  }

  @Override
  public void register(final DataContainerRegistry registry) {
    registry.register(STATS);
  }
}
