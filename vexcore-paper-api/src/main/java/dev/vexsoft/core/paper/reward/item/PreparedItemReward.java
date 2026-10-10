package dev.vexsoft.core.paper.reward.item;

import dev.vexsoft.core.reward.CompiledReward;
import java.util.List;
import org.bukkit.inventory.ItemStack;

/** Once-prepared physical rewards that can be routed into another transactional destination. */
public interface PreparedItemReward extends CompiledReward {

    /** Returns independent copies of the selected stacks, preserving their amounts and identities. */
    List<ItemStack> preparedItems();
}
