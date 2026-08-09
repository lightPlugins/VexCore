package dev.vexsoft.core.api.service.stats;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.stats.StatKey;
import java.util.List;
import net.kyori.adventure.text.Component;

/** Resolves stat presentation from the owning plugin's localization files. */
public interface StatLocalizationService extends VexService {

  /** Resolves the localized display name of an active stat for a player. */
  Component getName(VexPlayer player, StatKey stat);

  /** Resolves the localized description lines of an active stat for a player. */
  List<Component> getDescription(VexPlayer player, StatKey stat);
}
