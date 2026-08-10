package dev.vexsoft.core.paper.service.world;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.world.ServerPosition;
import dev.vexsoft.core.api.world.WorldKey;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;

/** Paper world resolver using the 26.1+ namespaced world identity API. */
@Dependencies
public final class VexWorldService implements WorldService {

  public VexWorldService(final VexServiceRegistry services) {
    Objects.requireNonNull(services, "services");
  }

  @Override
  public Optional<World> find(final WorldKey key) {
    WorldKey checkedKey = Objects.requireNonNull(key, "key");
    return Optional.ofNullable(Bukkit.getWorld(new NamespacedKey(
        checkedKey.namespace(),
        checkedKey.value()
    )));
  }

  @Override
  public WorldKey getKey(final World world) {
    NamespacedKey key = Objects.requireNonNull(world, "world").getKey();
    return new WorldKey(key.getNamespace(), key.getKey());
  }

  @Override
  public Optional<Location> createLocation(final ServerPosition position) {
    ServerPosition checkedPosition = Objects.requireNonNull(position, "position");
    return find(checkedPosition.world()).map(world -> new Location(
        world,
        checkedPosition.x(),
        checkedPosition.y(),
        checkedPosition.z(),
        checkedPosition.yaw(),
        checkedPosition.pitch()
    ));
  }
}
