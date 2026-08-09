package dev.vexsoft.core.paper.service.reactor;

import dev.vexsoft.core.paper.reactor.provider.BlockTypeProvider;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import java.util.Objects;
import java.util.function.Predicate;
import net.kyori.adventure.key.Key;
import org.bukkit.block.Block;

@Dependencies(BlockTypeCoordinatorService.class)
public final class VexBlockTypeRegistry implements BlockTypeRegistry, AutoCloseable {

  private final VexServiceRegistry services;
  private final ServiceOwner owner;
  private final BlockTypeCoordinatorService coordinator;

  public VexBlockTypeRegistry(final VexServiceRegistry services) {
    this.services = Objects.requireNonNull(services, "services");
    owner = services.getOwner();
    coordinator = services.require(BlockTypeCoordinatorService.class);
  }

  @Override
  public void register(final Class<? extends BlockTypeProvider> providerType) {
    coordinator.register(owner, services, providerType);
  }

  @Override
  public Predicate<Block> compile(final Key key) {
    return coordinator.compile(key);
  }

  @Override
  public void close() {
    coordinator.unregisterOwner(owner);
  }
}
