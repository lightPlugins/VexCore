package dev.vexsoft.core.paper.reward.item;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Creates and presents reward items belonging to one registered namespace. */
public interface RewardItemProvider {

    /** Validates a reference against the provider's current definitions. */
    void validate(RewardItem item);

    /** Creates one physical item while preserving its domain identity and upgrade level. */
    ItemStack create(RewardItem item);

    /** Returns the localized, styled name used in reward announcements. */
    Component name(Player viewer, RewardItem item);
}
