package dev.vexsoft.core.paper.service.inventory;

import dev.vexsoft.core.api.service.registry.VexService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Presents a protected shortcut in the first personal crafting input slot. */
public interface CraftingShortcutService extends VexService {

    /** Installs or refreshes a player-owned shortcut and disables vanilla crafting for that player. */
    void show(Player player, ItemStack item, Runnable action);

    /** Removes the shortcut and its player action. */
    void hide(Player player);

    /** Refreshes the shortcut after crafting or inventory changes. */
    void refresh(Player player);

    /** Executes a registered shortcut on the player's next tick. */
    void activate(Player player);

    /** Reports whether this player has an active shortcut and disabled vanilla crafting. */
    boolean isCraftingBlocked(Player player);

    /** Closes recipe-book panels for a player whose crafting is disabled. */
    void closeRecipeBook(Player player);

    /** Identifies protected shortcut items, including copies submitted by creative clients. */
    boolean isShortcut(ItemStack item);
}
