package dev.vexsoft.core.paper.items;

import org.bukkit.NamespacedKey;

/** Fixed item use cooldown in ticks, optionally shared by a namespaced group. */
public record VexUseCooldown(int ticks, NamespacedKey group) {

    /** Validates a positive fixed duration and an optional shared cooldown group. */
    public VexUseCooldown {
        if (ticks < 1) {
            throw new IllegalArgumentException("Item use cooldown must be at least one tick");
        }
    }
}
