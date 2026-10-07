package dev.vexsoft.core.stats.contribution;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.stats.StatKey;
import dev.vexsoft.core.stats.StatModifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Calculates the complete current runtime stat snapshot for one external system. */
public interface StatContributionProvider {

    /** Returns the single-modifier view; consumers requiring all operations use {@link #calculateModifiers}. */
    Map<StatKey, StatModifier> calculate(VexPlayer player);

    /** Returns all desired modifiers per stat; existing single-modifier providers keep their behavior. */
    default Map<StatKey, List<StatModifier>> calculateModifiers(final VexPlayer player) {
        Map<StatKey, List<StatModifier>> result = new LinkedHashMap<>();
        calculate(player).forEach((key, modifier) -> result.put(key, List.of(modifier)));
        return Map.copyOf(result);
    }
}
