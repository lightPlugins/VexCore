package dev.vexsoft.core.paper.service.reward;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.service.reward.PlayerRewardTransactionService;
import dev.vexsoft.core.api.service.reward.RewardService;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import java.util.Arrays;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Extends persistent reward transactions with the online player's inventory rollback. */
@Dependencies(RewardService.class)
public final class VexPlayerRewardTransactionService implements PlayerRewardTransactionService {

    private final RewardService rewards;

    public VexPlayerRewardTransactionService(final VexServiceRegistry services) {
        rewards = services.require(RewardService.class);
    }

    @Override
    public boolean execute(final VexPlayer player, final BooleanSupplier operation) {
        Player platform = player.requirePlatformPlayer(Player.class);
        ItemStack[] inventory = Arrays.stream(platform.getInventory().getContents())
            .map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new);
        ItemStack cursor = platform.getItemOnCursor().clone();
        boolean success = false;
        try {
            success = rewards.executeAtomically(new PlayerExecutionContext(player, Map.of()), operation);
            return success;
        } finally {
            if (!success) {
                platform.getInventory().setContents(inventory);
                platform.setItemOnCursor(cursor);
            }
        }
    }
}
