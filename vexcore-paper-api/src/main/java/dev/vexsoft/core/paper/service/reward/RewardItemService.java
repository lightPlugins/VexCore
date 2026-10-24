package dev.vexsoft.core.paper.service.reward;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.number.WholeAmount;
import dev.vexsoft.core.paper.reward.item.RewardItem;
import dev.vexsoft.core.paper.reward.item.RewardItemPresentation;
import dev.vexsoft.core.paper.reward.item.RewardItemProvider;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Resolves item providers and delivers inventory overflow privately to its owner. */
public interface RewardItemService extends VexService {

    /** Registers a namespace and returns a handle that removes only this provider. */
    AutoCloseable register(String namespace, RewardItemProvider provider);

    /** Registers namespace-specific display formatting without replacing item validation or delivery. */
    AutoCloseable registerPresentation(String namespace, RewardItemPresentation presentation);

    /** Validates an item reference against the registered provider. */
    void validate(RewardItem item);

    /** Creates a fresh physical reward item. */
    ItemStack create(RewardItem item);

    /** Returns whether the current provider routes this reference into persistent virtual storage. */
    default boolean isVirtual(RewardItem item) {
        return false;
    }

    /** Credits a prepared virtual quantity inside the caller's existing player transaction. */
    default void deliverVirtual(VexPlayer player, RewardItem item, WholeAmount amount) {
        throw new IllegalStateException("Virtual reward delivery is unavailable");
    }

    /** Resolves the provider's localized item name. */
    Component name(Player viewer, RewardItem item);

    /** Describes a quantity or range, preserving provider names and client translations. */
    default Component describe(Player viewer, RewardItem item, String amount) {
        return Component.text(amount + "× ").append(name(viewer, item));
    }

    /** Inserts prepared stacks and defers owner-only overflow drops until the player transaction commits. */
    void deliver(VexPlayer player, List<ItemStack> items);
}
