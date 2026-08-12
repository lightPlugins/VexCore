package dev.vexsoft.core.stats.contribution;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.stats.StatKey;
import dev.vexsoft.core.stats.StatModifier;
import java.util.Map;

/** Calculates the complete current runtime stat snapshot for one external system. */
public interface StatContributionProvider {

  /** Returns the complete desired contribution for this source and player. */
  Map<StatKey, StatModifier> calculate(VexPlayer player);
}
