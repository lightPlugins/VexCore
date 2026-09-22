package dev.vexsoft.core.paper.service.inventory;

import com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRecipeBookSettingsChangeEvent;
import org.bukkit.inventory.CraftingInventory;

/** Blocks all transfers of crafting shortcut items and routes their clicks to the owning action. */
@Dependencies({CraftingShortcutService.class, ScheduleService.class})
public final class CraftingShortcutListener implements Listener {

    private final CraftingShortcutService shortcuts;
    private final ScheduleService schedules;

    public CraftingShortcutListener(final VexServiceRegistry services) {
        shortcuts = services.require(CraftingShortcutService.class);
        schedules = services.require(ScheduleService.class);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        boolean craftingSlot = shortcuts.isCraftingBlocked(player)
            && event.getView().getTopInventory() instanceof CraftingInventory
            && event.getRawSlot() >= 0 && event.getRawSlot() < event.getView().getTopInventory().getSize();
        boolean intoWorkbench = shortcuts.isCraftingBlocked(player)
            && event.getView().getTopInventory() instanceof CraftingInventory crafting
            && crafting.getMatrix().length == 9
            && event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY;
        if (craftingSlot || intoWorkbench || shortcuts.isShortcut(event.getCurrentItem()) || shortcuts.isShortcut(event.getCursor())) {
            event.setCancelled(true);
            if (!(event instanceof InventoryCreativeEvent) && event.getRawSlot() == 1
                && shortcuts.isShortcut(event.getCurrentItem())
                && (event.isLeftClick() || event.isRightClick())) {
                shortcuts.activate(player);
            }
        }
        schedules.runFor(player, () -> shortcuts.refresh(player));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(final InventoryDragEvent event) {
        boolean craftingSlots = event.getWhoClicked() instanceof Player player
            && shortcuts.isCraftingBlocked(player) && event.getView().getTopInventory() instanceof CraftingInventory
            && event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize());
        if (craftingSlots || shortcuts.isShortcut(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void prepare(final PrepareItemCraftEvent event) {
        if (event.getView().getPlayer() instanceof Player player) {
            if (shortcuts.isCraftingBlocked(player)) {
                event.getInventory().setResult(null);
                schedules.runFor(player, () -> shortcuts.refresh(player));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void recipe(final PlayerRecipeBookClickEvent event) {
        if (shortcuts.isCraftingBlocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void recipeSettings(final PlayerRecipeBookSettingsChangeEvent event) {
        if (event.isOpen() && shortcuts.isCraftingBlocked(event.getPlayer())) {
            schedules.runFor(event.getPlayer(), () -> shortcuts.closeRecipeBook(event.getPlayer()));
        }
    }

    @EventHandler
    public void close(final InventoryCloseEvent event) {
        if (event.getInventory() instanceof CraftingInventory crafting) {
            for (int slot = 1; slot <= crafting.getMatrix().length; slot++) {
                if (shortcuts.isShortcut(crafting.getItem(slot))) {
                    crafting.setItem(slot, null);
                }
            }
        }
        if (event.getPlayer() instanceof Player player && shortcuts.isCraftingBlocked(player)) {
            schedules.runFor(player, () -> shortcuts.refresh(player));
        }
    }

    @EventHandler
    public void open(final InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && shortcuts.isCraftingBlocked(player)) {
            schedules.runFor(
                player, () -> {
                    shortcuts.closeRecipeBook(player);
                    shortcuts.refresh(player);
                }
            );
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void drop(final PlayerDropItemEvent event) {
        if (shortcuts.isShortcut(event.getItemDrop().getItemStack())) {
            event.getItemDrop().remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void death(final PlayerDeathEvent event) {
        event.getDrops().removeIf(shortcuts::isShortcut);
    }

    @EventHandler
    public void quit(final PlayerQuitEvent event) {
        shortcuts.hide(event.getPlayer());
    }
}
