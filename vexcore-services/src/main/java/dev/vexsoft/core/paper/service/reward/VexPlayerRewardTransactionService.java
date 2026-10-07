package dev.vexsoft.core.paper.service.reward;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.api.service.reward.PlayerRewardTransactionService;
import dev.vexsoft.core.api.service.reward.RewardService;
import dev.vexsoft.core.execution.PlayerExecutionContext;
import java.util.LinkedHashMap;
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
        int[] slots = new int[platform.getInventory().getSize() + 1];
        slots[0] = -1;
        for (int index = 1; index < slots.length; index++) {
            slots[index] = index - 1;
        }
        // Unrestricted callbacks may change any slot; callers with a known scope use the explicit overload.
        return execute(player, operation, slots);
    }

    @Override
    public boolean execute(final VexPlayer player, final BooleanSupplier operation, final int... inventorySlots) {
        if (inventorySlots.length == 0) {
            return rewards.executeAtomically(new PlayerExecutionContext(player, Map.of()), operation);
        }
        Player platform = player.requirePlatformPlayer(Player.class);
        var inventory = platform.getInventory();
        Map<Integer, ItemStack> before = new LinkedHashMap<>();
        for (int slot : inventorySlots) {
            if (slot < -1 || slot >= inventory.getSize()) {
                throw new IllegalArgumentException("Invalid reward inventory slot: " + slot);
            }
            if (!before.containsKey(slot)) {
                ItemStack item = slot == -1 ? platform.getItemOnCursor() : inventory.getItem(slot);
                before.put(slot, item == null ? null : item.clone());
            }
        }
        boolean success = false;
        try {
            success = rewards.executeAtomically(new PlayerExecutionContext(player, Map.of()), operation);
            return success;
        } finally {
            if (!success) {
                before.forEach((slot, item) -> {
                    if (slot == -1) {
                        platform.setItemOnCursor(item);
                    } else {
                        inventory.setItem(slot, item);
                    }
                });
            }
        }
    }
}
