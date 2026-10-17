package dev.vexsoft.core.paper.reward.item;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** Consumer-owned formatting that preserves styled names and client-translated vanilla components. */
@FunctionalInterface
public interface RewardItemPresentation {

    /** Formats one item amount or range without preparing, creating, or delivering its reward. */
    Component describe(Player viewer, RewardItem item, String amount, Component name);
}
