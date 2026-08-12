package dev.vexsoft.core.common.service.stats.contribution;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.service.stats.contribution.StatContributionRegistry;
import dev.vexsoft.core.stats.contribution.StatContributionProvider;
import dev.vexsoft.core.stats.contribution.StatContributionRefreshResult;
import java.util.List;
import java.util.Objects;

/** Owner-scoped stat contribution provider registry and refresh facade. */
@Dependencies(StatContributionCoordinatorService.class)
public final class VexStatContributionRegistry
    implements StatContributionRegistry, AutoCloseable {

  private final VexServiceRegistry services;
  private final ServiceOwner owner;
  private final StatContributionCoordinatorService coordinator;

  /** Captures the current owner and shared coordinator. */
  public VexStatContributionRegistry(final VexServiceRegistry services) {
    this.services = Objects.requireNonNull(services, "services");
    owner = services.getOwner();
    coordinator = services.require(StatContributionCoordinatorService.class);
  }

  @Override
  public void register(
      final String key,
      final Class<? extends StatContributionProvider> providerType
  ) {
    coordinator.register(owner, services, key, providerType);
  }

  @Override
  public boolean unregister(final String key) {
    return coordinator.unregister(owner, key);
  }

  @Override
  public StatContributionRefreshResult refresh(final VexPlayer player, final String key) {
    return coordinator.refresh(player, owner, key);
  }

  @Override
  public List<StatContributionRefreshResult> refresh(final VexPlayer player) {
    return coordinator.refresh(player, owner);
  }

  @Override
  public List<StatContributionRefreshResult> refreshAll(final String key) {
    return coordinator.refreshAll(owner, key);
  }

  @Override
  public List<StatContributionRefreshResult> refreshAll() {
    return coordinator.refreshAll(owner);
  }

  @Override
  public void close() {
    coordinator.unregisterOwner(owner);
  }
}
