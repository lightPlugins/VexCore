package dev.vexsoft.core.paper.service.inventory;

import dev.vexsoft.core.paper.inventory.InventoryContext;
import dev.vexsoft.core.paper.inventory.InventoryElement;
import dev.vexsoft.core.paper.inventory.SlotInventoryView;
import dev.vexsoft.core.paper.inventory.element.CursorInventoryElement;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
final class VexSlotInventoryInteractions {
  private final ScheduleService schedules;
  private final Map<UUID, Object> pendingDrags = new ConcurrentHashMap<>();

  VexSlotInventoryInteractions(ScheduleService schedules) {
    this.schedules = schedules;
  }

  void click(
      InventoryContext context,
      SlotInventoryView view,
      Map<Integer, InventoryElement> elements,
      InventoryClickEvent event) {
    if (event.isCancelled()) {
      return;
    }
    Player player = context.getViewer();
    if (pendingDrags.containsKey(player.getUniqueId())) {
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
          shift(player.getInventory(), event.getSlot());
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
      InventoryDragEvent event) {
    if (event.isCancelled()) {
      return;
    }
    Player player = context.getViewer();
    if (pendingDrags.containsKey(player.getUniqueId())) {
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
    pendingDrags.put(id, ticket);
    // Bukkit restores a cancelled drag's cursor after the event. Exchange only afterwards.
    try {
      if (schedules
          .runForLater(
              player,
              1L,
              () -> {
                if (!pendingDrags.remove(id, ticket)) {
                  return;
                }
                if (context.getInventoryService().getCurrentView(id).orElse(null) == view
                    && player.getOpenInventory().getTopInventory() == inventory
                    && player.getItemOnCursor().equals(expected)) {
                  transfer(context, view, cursorSlot);
                }
              },
              () -> pendingDrags.remove(id, ticket))
          .isEmpty()) {
        pendingDrags.remove(id, ticket);
      }
    } catch (RuntimeException failure) {
      pendingDrags.remove(id, ticket);
      throw failure;
    }
  }

  void cancel(UUID playerId) {
    pendingDrags.remove(playerId);
  }

  private static void transfer(
      InventoryContext context, SlotInventoryView view, CursorInventoryElement element) {
    element.transfer(context);
    if (context.getInventoryService().getCurrentView(context.getViewer().getUniqueId()).orElse(null)
        == view) {
      context.getInventoryService().refresh(context.getViewer());
    }
  }

  /** Shift transfers stay between backpack and hotbar, never into rendered menu slots. */
  private static void shift(PlayerInventory inventory, int source) {
    if (source < 0 || source >= 36) {
      return;
    }
    ItemStack original = inventory.getItem(source);
    if (original == null || original.isEmpty()) {
      return;
    }
    ItemStack remaining = original.clone();
    int start = source < 9 ? 9 : 0;
    int end = source < 9 ? 36 : 9;
    for (int i = start; i < end && !remaining.isEmpty(); i++) {
      ItemStack target = inventory.getItem(i);
      if (target == null || !target.isSimilar(remaining)) {
        continue;
      }
      int count =
          Math.min(
              remaining.getAmount(), Math.max(0, target.getMaxStackSize() - target.getAmount()));
      if (count == 0) {
        continue;
      }
      ItemStack updated = target.clone();
      updated.setAmount(target.getAmount() + count);
      inventory.setItem(i, updated);
      remaining.setAmount(remaining.getAmount() - count);
    }
    for (int i = start; i < end && !remaining.isEmpty(); i++) {
      ItemStack target = inventory.getItem(i);
      if (target != null && !target.isEmpty()) {
        continue;
      }
      int count = Math.min(remaining.getAmount(), remaining.getMaxStackSize());
      ItemStack placed = remaining.clone();
      placed.setAmount(count);
      inventory.setItem(i, placed);
      remaining.setAmount(remaining.getAmount() - count);
    }
    inventory.setItem(source, remaining.isEmpty() ? null : remaining);
  }

  /** Double-click collection reads player storage only; matching menu icons are not items. */
  private static void collect(Player player) {
    ItemStack cursor = player.getItemOnCursor().clone();
    if (cursor.isEmpty()) {
      return;
    }
    PlayerInventory inventory = player.getInventory();
    for (int i = 0; i < 36 && cursor.getAmount() < cursor.getMaxStackSize(); i++) {
      ItemStack item = inventory.getItem(i);
      if (item == null || !item.isSimilar(cursor)) {
        continue;
      }
      int count = Math.min(item.getAmount(), cursor.getMaxStackSize() - cursor.getAmount());
      ItemStack remaining = item.clone();
      remaining.setAmount(item.getAmount() - count);
      inventory.setItem(i, remaining.isEmpty() ? null : remaining);
      cursor.setAmount(cursor.getAmount() + count);
    }
    player.setItemOnCursor(cursor);
  }
}
