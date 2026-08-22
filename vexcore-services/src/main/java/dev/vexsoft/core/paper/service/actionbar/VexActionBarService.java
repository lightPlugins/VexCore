package dev.vexsoft.core.paper.service.actionbar;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** Default owner-scoped facade for the shared action-bar coordinator. */
@Dependencies(ActionBarCoordinatorService.class)
public final class VexActionBarService implements ActionBarService, AutoCloseable {

  private final ServiceOwner owner;
  private final ActionBarCoordinatorService coordinator;

  /** Resolves this service's owner and the global action-bar coordinator. */
  public VexActionBarService(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    owner = checkedServices.getOwner();
    coordinator = checkedServices.require(ActionBarCoordinatorService.class);
  }

  @Override
  public void setPersistent(
      final Player player,
      final String channel,
      final Component component,
      final int priority
  ) {
    coordinator.setPersistent(owner, player, channel, component, priority);
  }

  @Override
  public void showTemporary(
      final Player player,
      final String channel,
      final Component component,
      final long durationTicks,
      final int priority
  ) {
    coordinator.showTemporary(owner, player, channel, component, durationTicks, priority);
  }

  @Override
  public void clearPersistent(final Player player, final String channel) {
    coordinator.clearPersistent(owner, player, channel);
  }

  @Override
  public void clearTemporary(final Player player, final String channel) {
    coordinator.clearTemporary(owner, player, channel);
  }

  @Override
  public void clear(final Player player) {
    coordinator.clear(owner, player);
  }

  @Override
  public void close() {
    coordinator.clearOwner(owner);
  }
}
