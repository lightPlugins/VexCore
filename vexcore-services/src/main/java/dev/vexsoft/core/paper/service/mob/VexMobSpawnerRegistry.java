package dev.vexsoft.core.paper.service.mob;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.mob.spawner.MobSpawnerDefinition;
import dev.vexsoft.core.paper.mob.spawner.MobSpawnerKey;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/** Default owner-scoped mob spawner registry facade. */
@Dependencies(MobSpawnerRegistryCoordinatorService.class)
public final class VexMobSpawnerRegistry implements MobSpawnerRegistry, AutoCloseable {

  private final ServiceOwner owner;
  private final MobSpawnerRegistryCoordinatorService coordinator;

  public VexMobSpawnerRegistry(final VexServiceRegistry services) {
    VexServiceRegistry checked = Objects.requireNonNull(services, "services");
    owner = checked.getOwner();
    coordinator = checked.require(MobSpawnerRegistryCoordinatorService.class);
  }

  @Override
  public MobSpawnerDefinition register(final MobSpawnerDefinition definition) {
    return coordinator.register(owner, definition);
  }

  @Override
  public Collection<MobSpawnerDefinition> synchronize(
      final Collection<MobSpawnerDefinition> definitions
  ) {
    return coordinator.synchronize(owner, definitions);
  }

  @Override
  public Optional<MobSpawnerDefinition> find(final MobSpawnerKey key) {
    return coordinator.find(key);
  }

  @Override
  public boolean unregister(final MobSpawnerKey key) {
    return coordinator.unregister(owner, key);
  }

  @Override
  public Collection<MobSpawnerDefinition> getDefinitions() {
    return coordinator.getDefinitions();
  }

  @Override
  public void close() {
    coordinator.unregisterOwner(owner);
  }
}
