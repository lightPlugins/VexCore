package dev.vexsoft.core.paper.service.world;

import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.api.world.ServerPosition;
import dev.vexsoft.core.api.world.WorldKey;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.World;

/** Resolves Paper worlds exclusively through persistent namespaced world IDs. */
public interface WorldService extends VexService {

  /** Finds a currently loaded world by its namespaced ID. */
  Optional<World> find(WorldKey key);

  /** Returns the persistent namespaced ID of a loaded world. */
  WorldKey getKey(World world);

  /** Creates a Paper location when the position's world is currently loaded. */
  Optional<Location> createLocation(ServerPosition position);
}
