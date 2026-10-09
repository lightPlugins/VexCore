package dev.vexsoft.core.paper.items;

import java.util.Map;
import org.bukkit.NamespacedKey;

/** Grounded native break durations for physical materials, independent of the player's current target. */
public record VexMiningTool(Map<NamespacedKey, Double> ticks) {
    public VexMiningTool {
        ticks = Map.copyOf(ticks);
        ticks.forEach((key, duration) -> {
            if (!key.getNamespace().equals("minecraft") || !Double.isFinite(duration) || duration < 1) {
                throw new IllegalArgumentException("Mining rules require physical blocks and finite positive durations");
            }
        });
    }
}
