package dev.vexsoft.core.paper.reward.item;

import org.bukkit.NamespacedKey;

/** Portable reference to a reward item with a persistent upgrade level. */
public record RewardItem(String key, int upgradeLevel) {

    /** Validates the full namespaced identity and nonnegative upgrade level. */
    public RewardItem {
        if (key == null || !key.contains(":") || NamespacedKey.fromString(key) == null || upgradeLevel < 0) {
            throw new IllegalArgumentException("Invalid reward item identity or upgrade level");
        }
    }
}
