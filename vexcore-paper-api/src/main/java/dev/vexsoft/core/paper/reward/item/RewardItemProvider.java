package dev.vexsoft.core.paper.reward.item;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.number.WholeAmount;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Creates, presents and routes physical or virtual reward items belonging to one registered namespace. */
public interface RewardItemProvider {

    /** Validates a reference against the provider's current definitions. */
    void validate(RewardItem item);

    /** Creates one physical item while preserving its domain identity and upgrade level. */
    ItemStack create(RewardItem item);

    /** Returns the localized, styled name used in reward announcements. */
    Component name(Player viewer, RewardItem item);

    /** Returns whether this reference is delivered as a data-backed balance rather than a native stack. */
    default boolean isVirtual(RewardItem item) {
        return false;
    }

    /** Credits an exact virtual amount inside the caller's player transaction. */
    default void deposit(VexPlayer player, RewardItem item, WholeAmount amount) {
        throw new IllegalStateException("Provider does not support virtual items");
    }
}
