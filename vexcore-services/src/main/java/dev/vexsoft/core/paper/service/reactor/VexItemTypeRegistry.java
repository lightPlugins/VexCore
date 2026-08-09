package dev.vexsoft.core.paper.service.reactor;

import dev.vexsoft.core.paper.reactor.provider.ItemTypeProvider;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import java.util.Objects;
import java.util.function.Predicate;
import net.kyori.adventure.key.Key;
import org.bukkit.inventory.ItemStack;

@Dependencies(ItemTypeCoordinatorService.class)
public final class VexItemTypeRegistry implements ItemTypeRegistry, AutoCloseable {
  private final VexServiceRegistry services;
  private final ServiceOwner owner;
  private final ItemTypeCoordinatorService coordinator;

  public VexItemTypeRegistry(final VexServiceRegistry services) {
    this.services = Objects.requireNonNull(services, "services");
    owner = services.getOwner();
    coordinator = services.require(ItemTypeCoordinatorService.class);
  }

  @Override
  public void register(final Class<? extends ItemTypeProvider> providerType) {
    coordinator.register(owner, services, providerType);
  }

  @Override
  public Predicate<ItemStack> compile(final Key key) {
    return coordinator.compile(key);
  }

  @Override
  public void close() {
    coordinator.unregisterOwner(owner);
  }
}
