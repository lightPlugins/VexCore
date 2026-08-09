package dev.vexsoft.core.api.service.reactor;

import dev.vexsoft.core.api.configuration.ConfigurationSection;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.reactor.ReactionDefinition;
import dev.vexsoft.core.stats.StatDefinition;
import java.util.Collection;
import java.util.List;

/** Converts user-facing configuration sections into validated reaction definitions. */
public interface ReactionConfigurationService extends VexService {

  /** Loads every reaction map stored at the supplied configuration path. */
  List<ReactionDefinition> load(ConfigurationSection configuration, String path);

  /** Loads and atomically replaces this plugin's active reactions. */
  void reload(ConfigurationSection configuration, String path);

  /**
   * Reconciles this plugin's stats and reactions as one reload operation.
   *
   * <p>If reaction compilation fails, the previous stat definitions are restored and the previous
   * compiled reaction snapshot remains active.</p>
   */
  void reload(
      ConfigurationSection configuration,
      String path,
      Collection<StatDefinition> stats
  );
}
