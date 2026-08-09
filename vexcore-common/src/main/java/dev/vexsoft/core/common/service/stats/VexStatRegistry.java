package dev.vexsoft.core.common.service.stats;

import dev.vexsoft.core.gameplay.stat.Stat;
import dev.vexsoft.core.gameplay.stat.StatDefinition;
import dev.vexsoft.core.gameplay.stat.StatKey;

import dev.vexsoft.core.api.service.stats.StatRegistry;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Default owner-scoped stat registry facade. */
@Dependencies(StatRegistryCoordinatorService.class)
public final class VexStatRegistry implements StatRegistry, AutoCloseable {

  private final ServiceOwner owner;
  private final StatRegistryCoordinatorService coordinator;

  public VexStatRegistry(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    owner = checkedServices.getOwner();
    coordinator = checkedServices.require(StatRegistryCoordinatorService.class);
  }

  @Override
  public Stat register(final StatDefinition definition) {
    return coordinator.register(owner, definition);
  }

  @Override
  public List<Stat> synchronize(final Collection<StatDefinition> definitions) {
    return coordinator.synchronize(owner, definitions);
  }

  @Override
  public Optional<Stat> find(final StatKey key) {
    return coordinator.find(key);
  }

  @Override
  public Stat require(final StatKey key) {
    return find(key).orElseThrow(() -> new IllegalStateException("Stat is not registered: " + key));
  }

  @Override
  public boolean unregister(final StatKey key) {
    return coordinator.unregister(owner, key);
  }

  @Override
  public Collection<Stat> getRegisteredStats() {
    return coordinator.getRegisteredStats();
  }

  @Override
  public void close() {
    coordinator.unregisterOwner(owner);
  }
}
