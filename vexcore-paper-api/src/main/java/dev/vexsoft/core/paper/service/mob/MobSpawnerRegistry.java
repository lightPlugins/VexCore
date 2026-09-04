package dev.vexsoft.core.paper.service.mob;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.mob.spawner.MobSpawnerDefinition;
import dev.vexsoft.core.paper.mob.spawner.MobSpawnerKey;
import java.util.Collection;
import java.util.Optional;
import org.bukkit.entity.Player;

/** Owner-scoped registry of custom mob spawner points. */
public interface MobSpawnerRegistry extends VexService {

  /** Registers or replaces one owned spawner definition. */
  MobSpawnerDefinition register(MobSpawnerDefinition definition);

  /** Reconciles every spawner definition owned by this registry scope. */
  Collection<MobSpawnerDefinition> synchronize(Collection<MobSpawnerDefinition> definitions);

  /** Finds a registered spawner regardless of owner. */
  Optional<MobSpawnerDefinition> find(MobSpawnerKey key);

  /** Removes one owned spawner and all of its runtime mobs. */
  boolean unregister(MobSpawnerKey key);

  /** Returns every registered spawner definition. */
  Collection<MobSpawnerDefinition> getDefinitions();

  /** Requests an immediate range and population refresh for one fully available player. */
  void refresh(Player player);
}
