package dev.vexsoft.core.paper.service.mob;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.mob.spawner.MobSpawnerDefinition;
import dev.vexsoft.core.paper.mob.spawner.MobSpawnerKey;
import org.bukkit.entity.Player;

/** Coordinates activation and live populations for registered mob spawner points. */
public interface MobSpawnerRuntimeCoordinatorService extends VexService {

    /** Installs a spawner runtime, retiring the previous population when replacing its definition. */
    void register(ServiceOwner owner, MobSpawnerDefinition definition);

    /** Deactivates an owned spawner and records whether removal was caused by a reload. */
    void unregister(ServiceOwner owner, MobSpawnerKey key, boolean reload);

    /** Deactivates every spawner runtime belonging to the owner. */
    void unregisterOwner(ServiceOwner owner);

    /** Starts spawner listeners and player-bound activation tasks. */
    void start();

    /** Stops activation tasks and retires all managed spawner populations. */
    void shutdown();

    /** Refreshes the player's activation task and nearby spawner interest. */
    void refresh(Player player);
}
