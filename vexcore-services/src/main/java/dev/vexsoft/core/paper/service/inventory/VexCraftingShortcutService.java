package dev.vexsoft.core.paper.service.inventory;

import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.service.RecipeBookPacketAdapterService;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Reserves the first personal crafting input and disables vanilla player crafting. */
@Dependencies({ScheduleService.class, RecipeBookPacketAdapterService.class})
public final class VexCraftingShortcutService implements CraftingShortcutService, AutoCloseable {

    private static final NamespacedKey MARKER = new NamespacedKey("vexcore", "crafting_shortcut");
    private final Map<UUID, Shortcut> shortcuts = new ConcurrentHashMap<>();
    private final Map<UUID, CraftingInventory> projections = new ConcurrentHashMap<>();
    private final ScheduleService schedules;
    private final RecipeBookPacketAdapterService recipeBooks;
    private final Set<UUID> refreshing = new HashSet<>();

    public VexCraftingShortcutService(final VexServiceRegistry services) {
        schedules = services.require(ScheduleService.class);
        recipeBooks = services.require(RecipeBookPacketAdapterService.class);
    }

    @Override
    public void show(final Player player, final ItemStack item, final Runnable action) {
        ItemStack display = item.clone();
        display.editMeta(meta -> meta.getPersistentDataContainer().set(MARKER, PersistentDataType.BYTE, (byte) 1));
        boolean first = shortcuts.put(player.getUniqueId(), new Shortcut(player, display, action)) == null;
        if (first) {
            closeRecipeBook(player);
        }
        refresh(player);
    }

    @Override
    public void hide(final Player player) {
        shortcuts.remove(player.getUniqueId());
        CraftingInventory crafting = projections.remove(player.getUniqueId());
        if (crafting != null && isShortcut(crafting.getItem(1))) {
            crafting.setItem(1, null);
        }
    }

    @Override
    public void refresh(final Player player) {
        Shortcut shortcut = shortcuts.get(player.getUniqueId());
        if (shortcut == null || !(player.getOpenInventory().getTopInventory() instanceof CraftingInventory crafting)) {
            return;
        }
        synchronized (refreshing) {
            if (!refreshing.add(player.getUniqueId())) {
                return;
            }
        }
        try {
            boolean personal = crafting.getMatrix().length == 4;
            if (personal) {
                projections.put(player.getUniqueId(), crafting);
            }
            if (crafting.getResult() != null) {
                crafting.setResult(null);
            }
            for (int slot = 1; slot <= crafting.getMatrix().length; slot++) {
                ItemStack existing = crafting.getItem(slot);
                if (personal && slot == 1 && shortcut.item().equals(existing)) {
                    continue;
                }
                if (existing != null && !existing.isEmpty()) {
                    crafting.setItem(slot, null);
                    if (!isShortcut(existing)) {
                        player.getInventory().addItem(existing).values().forEach(leftover ->
                            player.getWorld().dropItemNaturally(player.getLocation(), leftover));
                    }
                }
                if (personal && slot == 1) {
                    crafting.setItem(slot, shortcut.item().clone());
                }
            }
        } finally {
            synchronized (refreshing) {
                refreshing.remove(player.getUniqueId());
            }
        }
    }

    @Override
    public boolean isCraftingBlocked(final Player player) {
        return shortcuts.containsKey(player.getUniqueId());
    }

    @Override
    public void closeRecipeBook(final Player player) {
        if (isCraftingBlocked(player)) {
            recipeBooks.closeRecipeBook(player);
        }
    }

    @Override
    public void activate(final Player player) {
        Shortcut shortcut = shortcuts.get(player.getUniqueId());
        if (shortcut != null) {
            schedules.runFor(
                player, () -> {
                    Shortcut current = shortcuts.get(player.getUniqueId());
                    if (current != null) {
                        current.action().run();
                    }
                }
            );
        }
    }

    @Override
    public boolean isShortcut(final ItemStack item) {
        return item != null && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(MARKER, PersistentDataType.BYTE);
    }

    @Override
    public void close() {
        for (Shortcut shortcut : shortcuts.values()) {
            // Owner tasks are cancelled during shutdown, so cleanup cannot be deferred.
            hide(shortcut.player());
        }
        shortcuts.clear();
        projections.clear();
    }

    private record Shortcut(Player player, ItemStack item, Runnable action) {

    }
}
