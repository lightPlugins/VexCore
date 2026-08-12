package dev.vexsoft.core.api.service.stats.contribution;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.stats.contribution.StatContributionProvider;
import dev.vexsoft.core.stats.contribution.StatContributionRefreshResult;
import java.util.List;

/** Registers and refreshes owner-scoped reconstructable stat contribution providers. */
public interface StatContributionRegistry extends VexService {

  /** Registers a provider under an owner-local source key. */
  void register(String key, Class<? extends StatContributionProvider> providerType);

  /** Removes a provider and all of its active player contributions. */
  boolean unregister(String key);

  /** Immediately rebuilds one owned provider for one loaded player. */
  StatContributionRefreshResult refresh(VexPlayer player, String key);

  /** Immediately rebuilds every provider owned by this registry for one player. */
  List<StatContributionRefreshResult> refresh(VexPlayer player);

  /** Immediately rebuilds one owned provider for every loaded player. */
  List<StatContributionRefreshResult> refreshAll(String key);

  /** Immediately rebuilds every provider owned by this registry for all loaded players. */
  List<StatContributionRefreshResult> refreshAll();
}
