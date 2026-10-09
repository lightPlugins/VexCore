package dev.vexsoft.core.paper.service.reward;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.VexService;
import dev.vexsoft.core.paper.reward.item.RewardItem;
import dev.vexsoft.core.paper.reward.item.RewardItemProvider;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.Component;

/** Resolves item providers and delivers inventory overflow privately to its owner. */
public interface RewardItemService extends VexService {

    /** Registers a namespace and returns a handle that removes only this provider. */
    AutoCloseable register(String namespace, RewardItemProvider provider);

    /** Validates an item reference against the registered provider. */
    void validate(RewardItem item);

    /** Creates a fresh physical reward item. */
    ItemStack create(RewardItem item);

    /** Resolves the provider's localized item name. */
    Component name(Player viewer, RewardItem item);

    /** Inserts prepared stacks and defers owner-only overflow drops until the player transaction commits. */
    void deliver(VexPlayer player, List<ItemStack> items);
}
