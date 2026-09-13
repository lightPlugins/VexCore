package dev.vexsoft.core.paper.inventory.view;

import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.inventory.InventoryContext;
import dev.vexsoft.core.paper.inventory.InventoryElement;
import dev.vexsoft.core.paper.inventory.InventoryKey;
import dev.vexsoft.core.paper.inventory.ItemSaleSession;
import dev.vexsoft.core.paper.inventory.MutableInventoryView;
import dev.vexsoft.core.paper.inventory.element.RefreshableInventoryElement;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Reusable checkpointed input area with protected controls and explicit native item transfers. */
public abstract class ItemDepositView extends AbstractInventoryView implements MutableInventoryView {
    private final Player player;
    private final ItemSaleSession session;
    private final Predicate<ItemStack> accepted;
    private final ScheduleService schedules;
    private boolean pending;
    private boolean closed;

    /** Binds an escrow to the first inputSlots slots of a fixed-size menu. */
    protected ItemDepositView(
        final VexServiceRegistry services,
        final InventoryKey key,
        final Player player,
        final ItemSaleSession session,
        final Predicate<ItemStack> accepted
    ) {
        super(services, key, 54);
        this.player = player;
        this.session = session;
        this.accepted = accepted;
        if (session.inventory().getSize() > 36) {
            throw new IllegalArgumentException("Input inventory must not overlap the footer");
        }
        schedules = services.require(ScheduleService.class);
        for (int slot = 0; slot < session.inventory().getSize(); slot++) {
            int index = slot;
            addElement(slot, new RefreshableInventoryElement(context -> session.inventory().getItem(index)));
        }
    }

    /** Returns the session backing this menu. */
    protected final ItemSaleSession saleSession() {
        return session;
    }

    @Override
    public void onInventoryClick(final InventoryContext context, final InventoryClickEvent event) {
        if (event.isCancelled()) {
            return;
        }
        event.setCancelled(true);
        if (pending || closed || session.isLocked()) {
            return;
        }
        int raw = event.getRawSlot();
        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT && click != ClickType.SHIFT_LEFT) {
            return;
        }
        if (raw >= session.inventory().getSize() && raw < getSize()) {
            InventoryElement element = getElements(context).get(raw);
            if (element != null && element.isClickable()) {
                defer(context, () -> element.onClick(context, event));
            }
            return;
        }
        if (raw < 0 || (raw >= getSize() && (event.getSlot() < 0 || event.getSlot() >= 36))) {
            return;
        }
        boolean top = raw < getSize();
        int slot = top ? raw : event.getSlot();
        Inventory source = top ? session.inventory() : player.getInventory();
        ItemStack current = source.getItem(slot);
        ItemStack expectedItem = current == null ? null : current.clone();
        ItemStack expectedCursor = player.getItemOnCursor().clone();
        defer(context, () -> {
            if (Objects.equals(expectedItem, source.getItem(slot)) && expectedCursor.equals(player.getItemOnCursor())) {
                session.transfer(() -> transfer(top, slot, click));
            }
        });
    }

    @Override
    public void onInventoryDrag(final InventoryContext context, final InventoryDragEvent event) {
        if (event.isCancelled()) {
            return;
        }
        event.setCancelled(true);
        if (pending || closed || session.isLocked()) {
            return;
        }
        Map<Integer, ItemStack> changes = new LinkedHashMap<>();
        Map<Integer, ItemStack> before = new LinkedHashMap<>();
        for (var entry : event.getNewItems().entrySet()) {
            int raw = entry.getKey();
            if (raw < 0 || raw >= getSize() + 36
                || (raw >= session.inventory().getSize() && raw < getSize())
                || (raw < getSize() && !accepted.test(entry.getValue()))) {
                return;
            }
            int encoded = raw < getSize() ? raw : getSize() + event.getView().convertSlot(raw);
            changes.put(encoded, entry.getValue().clone());
            ItemStack previous = itemAt(encoded);
            before.put(encoded, previous == null ? null : previous.clone());
        }
        ItemStack oldCursor = event.getOldCursor().clone();
        ItemStack newCursor = event.getCursor() == null ? null : event.getCursor().clone();
        defer(context, () -> {
            if (!player.getItemOnCursor().equals(oldCursor)) {
                return;
            }
            for (var entry : before.entrySet()) {
                if (!Objects.equals(entry.getValue(), itemAt(entry.getKey()))) {
                    return;
                }
            }
            session.transfer(() -> {
                changes.forEach(this::setItemAt);
                player.setItemOnCursor(newCursor);
            });
        });
    }

    private void defer(final InventoryContext context, final Runnable transfer) {
        pending = true;
        // A cancelled drag restores its cursor after dispatch; mutate and save only on the next tick.
        if (schedules.runForLater(player, 1L, () -> {
            pending = false;
            if (closed || context.getInventoryService().getCurrentView(player.getUniqueId()).orElse(null) != this) {
                return;
            }
            try {
                transfer.run();
                if (!closed) {
                    context.getInventoryService().refresh(player);
                }
            } catch (RuntimeException failure) {
                player.closeInventory();
                throw failure;
            }
        }, () -> pending = false).isEmpty()) {
            pending = false;
        }
    }

    private void transfer(final boolean top, final int slot, final ClickType click) {
        Inventory source = top ? session.inventory() : player.getInventory();
        ItemStack current = source.getItem(slot);
        if (click == ClickType.SHIFT_LEFT) {
            if (current == null || current.isEmpty() || (!top && !accepted.test(current))) {
                return;
            }
            Inventory target = top ? player.getInventory() : session.inventory();
            var remaining = target.addItem(current.clone());
            source.setItem(slot, remaining.isEmpty() ? null : remaining.values().iterator().next());
            return;
        }
        ItemStack cursor = player.getItemOnCursor().clone();
        if (!cursor.isEmpty() && top && !accepted.test(cursor)) {
            return;
        }
        if (cursor.isEmpty()) {
            if (current == null || current.isEmpty()) {
                return;
            }
            int count = click == ClickType.RIGHT ? (current.getAmount() + 1) / 2 : current.getAmount();
            ItemStack picked = current.clone();
            picked.setAmount(count);
            ItemStack remainder = current.clone();
            remainder.setAmount(current.getAmount() - count);
            source.setItem(slot, remainder.isEmpty() ? null : remainder);
            player.setItemOnCursor(picked);
        } else if (current == null || current.isEmpty() || current.isSimilar(cursor)) {
            int existing = current == null || current.isEmpty() ? 0 : current.getAmount();
            int count = Math.min(click == ClickType.RIGHT ? 1 : cursor.getAmount(), cursor.getMaxStackSize() - existing);
            if (count > 0) {
                ItemStack placed = cursor.clone();
                placed.setAmount(existing + count);
                source.setItem(slot, placed);
                cursor.setAmount(cursor.getAmount() - count);
                player.setItemOnCursor(cursor.isEmpty() ? null : cursor);
            }
        } else if (click == ClickType.LEFT) {
            source.setItem(slot, cursor);
            player.setItemOnCursor(current.clone());
        }
    }

    private ItemStack itemAt(final int encoded) {
        return encoded < getSize() ? session.inventory().getItem(encoded)
            : player.getInventory().getItem(encoded - getSize());
    }

    private void setItemAt(final int encoded, final ItemStack item) {
        if (encoded < getSize()) {
            session.inventory().setItem(encoded, item);
        } else {
            player.getInventory().setItem(encoded - getSize(), item);
        }
    }

    @Override
    public void onClose(final InventoryContext context) {
        closed = true;
        session.close();
        super.onClose(context);
    }
}
