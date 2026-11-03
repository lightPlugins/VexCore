package dev.vexsoft.core.paper.reward.item;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** Consumer-owned formatting that preserves styled names and client-translated vanilla components. */
@FunctionalInterface
public interface RewardItemPresentation {

    /** Styles the standalone item name used by HUD cards without adding a quantity. */
    default Component name(Player viewer, RewardItem item, Component name) {
        return name;
    }

    /** Formats one item amount or range without preparing, creating, or delivering its reward. */
    Component describe(Player viewer, RewardItem item, String amount, Component name);
}
