package dev.vexsoft.core.paper.reward.item;

import dev.vexsoft.core.execution.PlayerExecutionContext;
import dev.vexsoft.core.reward.CompiledReward;
import dev.vexsoft.core.reward.RewardResult;
import java.util.List;
import org.bukkit.inventory.ItemStack;

/** Once-prepared item rewards whose physical portion can be routed into another transactional destination. */
public interface PreparedItemReward extends CompiledReward {

    /** Returns independent copies of the selected stacks, preserving their amounts and identities. */
    List<ItemStack> preparedItems();

    /** Grants only prepared virtual balances when a consumer routes physical stacks elsewhere. */
    default RewardResult grantVirtual(PlayerExecutionContext context) {
        return RewardResult.success();
    }
}
