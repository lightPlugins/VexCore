package dev.vexsoft.core.paper.service.inventory;

import dev.vexsoft.core.paper.inventory.InventoryContext;
import dev.vexsoft.core.paper.inventory.InventoryElement;
import dev.vexsoft.core.paper.inventory.PlayerInventoryTransfers;
import dev.vexsoft.core.paper.inventory.SlotInventoryView;
import dev.vexsoft.core.paper.inventory.element.CursorInventoryElement;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Shared item movement for opt-in slot views; persistent ownership stays in the plugin callback.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class VexSlotInventoryInteractions {

    private final ScheduleService schedules;
    private final Map<UUID, Object> pendingDrags = new ConcurrentHashMap<>();
    private final Map<UUID, Object> pendingExchanges = new ConcurrentHashMap<>();

    void click(
        InventoryContext context,
        SlotInventoryView view,
        Map<Integer, InventoryElement> elements,
        InventoryClickEvent event
    ) {
        if (event.isCancelled()) {
            return;
        }

        Player player = context.getViewer();

        if (pendingDrags.containsKey(player.getUniqueId()) || pendingExchanges.containsKey(player.getUniqueId())) {
            event.setCancelled(true);

            return;
        }

        int raw = event.getRawSlot();

        if (raw < 0) {
            return;
        }

        if (raw >= view.getSize()) {
            if (event.isShiftClick()) {
                event.setCancelled(true);

                if (event.getClickedInventory() == player.getInventory()) {
                    PlayerInventoryTransfers.shift(player.getInventory(), event.getSlot());
                }
            } else if (event.getClick() == ClickType.DOUBLE_CLICK) {
                event.setCancelled(true);
                collect(player);
            }

            return;
        }

        event.setCancelled(true);

        if (!view.acceptsMenuClick(event.getClick())) {
            return;
        }

        InventoryElement element = elements.get(raw);

        if (element instanceof CursorInventoryElement cursorSlot) {
            transfer(context, view, cursorSlot);
        } else if (element != null && element.isClickable()) {
            element.onClick(context, event);
        }
    }

    void drag(
        InventoryContext context,
        SlotInventoryView view,
        Map<Integer, InventoryElement> elements,
        InventoryDragEvent event
    ) {
        if (event.isCancelled()) {
            return;
        }

        Player player = context.getViewer();

        if (pendingDrags.containsKey(player.getUniqueId()) || pendingExchanges.containsKey(player.getUniqueId())) {
            event.setCancelled(true);

            return;
        }

        if (event.getRawSlots().stream().noneMatch(slot -> slot < view.getSize())) {
            return;
        }

        event.setCancelled(true);
        // A cursor exchange is one transaction. Never partly apply a drag spanning menu controls.
        if (event.getRawSlots().size() != 1) {
            return;
        }

        int raw = event.getRawSlots().iterator().next();

        if (!(elements.get(raw) instanceof CursorInventoryElement cursorSlot)) {
            return;
        }

        ItemStack expected = event.getOldCursor().clone();
        Inventory inventory = event.getView().getTopInventory();
        Object ticket = new Object();
        UUID id = player.getUniqueId();

        Runnable synchronize = context.getInventoryService().deferSynchronization(player);
        pendingDrags.put(id, ticket);
        // Bukkit restores a cancelled drag's cursor after the event. Exchange only afterwards.
        try {
            if (schedules.runForLater(
                player,
                1L,
                () -> {
                    if (!pendingDrags.remove(id, ticket)) {
                        synchronize.run();
                        return;
                    }

                    if (context.getInventoryService().getCurrentView(id).orElse(null) == view
                        && player.getOpenInventory().getTopInventory() == inventory && player.getItemOnCursor()
                        .equals(expected)) {
                        transfer(context, view, cursorSlot, synchronize);
                    } else {
                        synchronize.run();
                    }
                },
                () -> {
                    pendingDrags.remove(id, ticket);
                    synchronize.run();
                }
            ).isEmpty()) {
                pendingDrags.remove(id, ticket);
                synchronize.run();
            }
        } catch (RuntimeException failure) {
            pendingDrags.remove(id, ticket);
            synchronize.run();
            throw failure;
        }
    }

    void cancel(UUID playerId) {
        pendingDrags.remove(playerId);
        pendingExchanges.remove(playerId);
    }

    private void transfer(InventoryContext context, SlotInventoryView view, CursorInventoryElement element) {
        transfer(context, view, element, context.getInventoryService().deferSynchronization(context.getViewer()));
    }

    private void transfer(InventoryContext context, SlotInventoryView view, CursorInventoryElement element,
        Runnable synchronize) {
        var player = context.getViewer();
        var id = player.getUniqueId();
        var ticket = new Object();
        if (pendingExchanges.putIfAbsent(id, ticket) != null) {
            synchronize.run();
            return;
        }
        Runnable finish = () -> {
            try {
                if (pendingExchanges.remove(id, ticket)) {
                    refresh(context, view);
                }
            } finally {
                synchronize.run();
            }
        };
        try {
            var completion = element.transferAsync(context);
            if (completion.isDone()) {
                finish.run();
            } else {
                completion.whenComplete((ignored, failure) -> {
                    try {
                        var scheduled = schedules.runFor(player, finish, () -> {
                            pendingExchanges.remove(id, ticket);
                            synchronize.run();
                        });
                        if (scheduled.isEmpty()) {
                            pendingExchanges.remove(id, ticket);
                            synchronize.run();
                        }
                    } catch (RuntimeException rejected) {
                        pendingExchanges.remove(id, ticket);
                        synchronize.run();
                    }
                });
            }
        } catch (RuntimeException failure) {
            finish.run();
            throw failure;
        }
    }

    private static void refresh(InventoryContext context, SlotInventoryView view) {
        if (context.getInventoryService().getCurrentView(context.getViewer().getUniqueId()).orElse(null) == view) {
            context.getInventoryService().refresh(context.getViewer());
        }
    }

    /** Double-click collection reads player storage only; matching menu icons are not items. */
    private static void collect(Player player) {
        ItemStack cursor = player.getItemOnCursor().clone();

        if (cursor.isEmpty()) {
            return;
        }

        PlayerInventory inventory = player.getInventory();

        for (int slotIndex = 0; slotIndex < 36 && cursor.getAmount() < cursor.getMaxStackSize(); slotIndex++) {
            ItemStack item = inventory.getItem(slotIndex);

            if (item == null || !item.isSimilar(cursor)) {
                continue;
            }

            int count = Math.min(item.getAmount(), cursor.getMaxStackSize() - cursor.getAmount());
            ItemStack remaining = item.clone();

            remaining.setAmount(item.getAmount() - count);
            inventory.setItem(slotIndex, remaining.isEmpty() ? null : remaining);
            cursor.setAmount(cursor.getAmount() + count);
        }

        player.setItemOnCursor(cursor);
    }
}
