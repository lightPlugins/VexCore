package dev.vexsoft.core.paper.service.reactor;

import dev.vexsoft.core.paper.reactor.provider.EntityTypeProvider;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import java.util.Objects;
import java.util.function.Predicate;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Entity;

@Dependencies(EntityTypeCoordinatorService.class)
public final class VexEntityTypeRegistry implements EntityTypeRegistry, AutoCloseable {
  private final VexServiceRegistry services;
  private final ServiceOwner owner;
  private final EntityTypeCoordinatorService coordinator;

  public VexEntityTypeRegistry(final VexServiceRegistry services) {
    this.services = Objects.requireNonNull(services, "services");
    owner = services.getOwner();
    coordinator = services.require(EntityTypeCoordinatorService.class);
  }

  @Override
  public void register(final Class<? extends EntityTypeProvider> providerType) {
    coordinator.register(owner, services, providerType);
  }

  @Override
  public Predicate<Entity> compile(final Key key) {
    return coordinator.compile(key);
  }

  @Override
  public void close() {
    coordinator.unregisterOwner(owner);
  }
}
