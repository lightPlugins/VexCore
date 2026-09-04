package dev.vexsoft.core.paper.service.mob;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.mob.spawner.MobSpawnerDefinition;
import dev.vexsoft.core.paper.mob.spawner.MobSpawnerKey;
import java.util.Collection;
import java.util.Optional;
import org.bukkit.entity.Player;

/** Coordinates spawner definitions across owner-scoped registry facades. */
public interface MobSpawnerRegistryCoordinatorService extends VexService {

  MobSpawnerDefinition register(ServiceOwner owner, MobSpawnerDefinition definition);

  Collection<MobSpawnerDefinition> synchronize(
      ServiceOwner owner,
      Collection<MobSpawnerDefinition> definitions
  );

  Optional<MobSpawnerDefinition> find(MobSpawnerKey key);

  boolean unregister(ServiceOwner owner, MobSpawnerKey key);

  void unregisterOwner(ServiceOwner owner);

  Collection<MobSpawnerDefinition> getDefinitions();

  void refresh(Player player);
}
