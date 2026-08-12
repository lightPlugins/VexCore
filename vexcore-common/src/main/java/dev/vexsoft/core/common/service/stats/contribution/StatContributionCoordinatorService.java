package dev.vexsoft.core.common.service.stats.contribution;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.stats.contribution.StatContributionProvider;
import dev.vexsoft.core.stats.contribution.StatContributionRefreshResult;
import java.util.List;

/** Internal coordinator for provider registration and player snapshots. */
public interface StatContributionCoordinatorService extends VexService {

  /** Creates and registers one provider. */
  void register(
      ServiceOwner owner,
      VexServiceRegistry services,
      String key,
      Class<? extends StatContributionProvider> type
  );

  /** Removes one owned provider. */
  boolean unregister(ServiceOwner owner, String key);

  /** Removes every provider owned by one service scope. */
  void unregisterOwner(ServiceOwner owner);

  /** Refreshes one source for one player. */
  StatContributionRefreshResult refresh(VexPlayer player, ServiceOwner owner, String key);

  /** Refreshes all owner sources for one player. */
  List<StatContributionRefreshResult> refresh(VexPlayer player, ServiceOwner owner);

  /** Refreshes one source for every loaded player. */
  List<StatContributionRefreshResult> refreshAll(ServiceOwner owner, String key);

  /** Refreshes all owner sources for every loaded player. */
  List<StatContributionRefreshResult> refreshAll(ServiceOwner owner);

  /** Refreshes every registered provider for a newly loaded player. */
  List<StatContributionRefreshResult> refreshPlayer(VexPlayer player);

  /** Forgets runtime handles belonging to a player that is leaving. */
  void removePlayer(VexPlayer player);
}
