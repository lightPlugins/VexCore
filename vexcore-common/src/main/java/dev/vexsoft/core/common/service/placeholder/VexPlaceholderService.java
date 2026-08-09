package dev.vexsoft.core.common.service.placeholder;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.placeholder.PlaceholderService;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.placeholder.PlaceholderContext;
import dev.vexsoft.core.placeholder.VexPlaceholder;
import java.util.Objects;
import net.kyori.adventure.text.Component;

/** Default owner-scoped placeholder facade. */
@Dependencies(PlaceholderRegistryCoordinatorService.class)
public final class VexPlaceholderService implements PlaceholderService, AutoCloseable {

  private final VexServiceRegistry services;
  private final ServiceOwner owner;
  private final PlaceholderRegistryCoordinatorService coordinator;

  public VexPlaceholderService(final VexServiceRegistry services) {
    this.services = Objects.requireNonNull(services, "services");
    owner = services.getOwner();
    coordinator = services.require(PlaceholderRegistryCoordinatorService.class);
  }

  @Override
  public <T extends VexPlaceholder> T register(final Class<T> placeholderType) {
    return coordinator.register(owner, services, placeholderType);
  }

  @Override
  public String resolve(final VexPlayer player, final String input) {
    return resolve(PlaceholderContext.of(player), input);
  }

  @Override
  public String resolve(final PlaceholderContext context, final String input) {
    return coordinator.resolve(context, input);
  }

  @Override
  public Component resolve(final VexPlayer player, final Component component) {
    return PlaceholderComponents.resolve(this, player, component);
  }

  @Override
  public void clear() {
    coordinator.unregisterOwner(owner);
  }

  @Override
  public void close() {
    clear();
  }
}
