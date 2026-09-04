package dev.vexsoft.core.paper.service.mob;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.mob.spawner.MobSpawnerDefinition;
import dev.vexsoft.core.paper.mob.spawner.MobSpawnerKey;
import org.bukkit.entity.Player;

/** Coordinates activation and live populations for registered mob spawner points. */
public interface MobSpawnerRuntimeCoordinatorService extends VexService {

  void register(ServiceOwner owner, MobSpawnerDefinition definition);

  void unregister(ServiceOwner owner, MobSpawnerKey key, boolean reload);

  void unregisterOwner(ServiceOwner owner);

  void start();

  void shutdown();

  void refresh(Player player);
}
