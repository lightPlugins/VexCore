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

    /** Registers or updates an owner's spawner definition and its runtime state. */
    MobSpawnerDefinition register(ServiceOwner owner, MobSpawnerDefinition definition);

    /** Reconciles the owner's spawner definitions and refreshes their runtime state. */
    Collection<MobSpawnerDefinition> synchronize(ServiceOwner owner, Collection<MobSpawnerDefinition> definitions);

    /** Finds a registered spawner definition by its stable key. */
    Optional<MobSpawnerDefinition> find(MobSpawnerKey key);

    /** Removes an owned spawner definition and deactivates its runtime population. */
    boolean unregister(ServiceOwner owner, MobSpawnerKey key);

    /** Removes the owner's spawner definitions and deactivates their populations. */
    void unregisterOwner(ServiceOwner owner);

    /** Returns a snapshot of all registered spawner definitions. */
    Collection<MobSpawnerDefinition> getDefinitions();

    /** Refreshes the spawners relevant to the player's current location. */
    void refresh(Player player);
}
