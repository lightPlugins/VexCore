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
import org.bukkit.event.inventory.InventoryAction;
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

    /** Reports a completed or rejected item interaction; controls provide their own feedback. */
    protected void onItemInteraction(final boolean successful) {
    }

    @Override
    public void onInventoryClick(final InventoryContext context, final InventoryClickEvent event) {
        if (event.isCancelled()) {
            return;
        }
        event.setCancelled(true);
        if (pending || closed || session.isLocked()) {
            onItemInteraction(false);
            return;
        }
        int raw = event.getRawSlot();
        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT
            && click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) {
            onItemInteraction(false);
            return;
        }
        if (raw >= session.inventory().getSize() && raw < getSize()) {
            InventoryElement element = getElements(context).get(raw);
            if (element != null && element.isClickable()) {
                defer(context, () -> element.onClick(context, event));
            } else {
                onItemInteraction(false);
            }
            return;
        }
        if (raw < 0 || (raw >= getSize() && (event.getSlot() < 0 || event.getSlot() >= 36))) {
            onItemInteraction(false);
            return;
        }
        boolean top = raw < getSize();
        if (!top && (click == ClickType.LEFT || click == ClickType.RIGHT)) {
            // Let the server apply ordinary storage clicks without a cancel/cursor correction.
            // Persist afterwards, when the native inventory and cursor contain the result.
            if (defer(context, () -> {
                session.checkpoint();
                onItemInteraction(!event.isCancelled() && event.getAction() != InventoryAction.NOTHING);
            }, false)) {
                event.setCancelled(false);
            } else {
                onItemInteraction(false);
            }
            return;
        }
        int slot = top ? raw : event.getSlot();
        Inventory source = top ? session.inventory() : player.getInventory();
        ItemStack previous = source.getItem(slot);
        ItemStack before = previous == null ? null : previous.clone();
        ItemStack cursorBefore = player.getItemOnCursor().clone();
        // Cancelled clicks may be applied synchronously. Only cancelled drags restore the cursor
        // after dispatch and require a deferred transfer. Render before the click correction.
        try {
            session.transfer(() -> transfer(top, slot, click));
            context.getInventoryService().refresh(player);
        } catch (RuntimeException failure) {
            onItemInteraction(false);
            defer(context, player::closeInventory);
            throw failure;
        }
        onItemInteraction(!Objects.equals(before, source.getItem(slot))
            || !cursorBefore.equals(player.getItemOnCursor()));
    }

    @Override
    public void onInventoryDrag(final InventoryContext context, final InventoryDragEvent event) {
        if (event.isCancelled()) {
            return;
        }
        event.setCancelled(true);
        if (pending || closed || session.isLocked()) {
            onItemInteraction(false);
            return;
        }
        if (!event.getRawSlots().isEmpty()
            && event.getRawSlots().stream().allMatch(slot -> slot >= getSize() && slot < getSize() + 36)) {
            if (defer(context, () -> {
                session.checkpoint();
                onItemInteraction(!event.isCancelled());
            }, false)) {
                event.setCancelled(false);
            } else {
                onItemInteraction(false);
            }
            return;
        }
        Map<Integer, ItemStack> changes = new LinkedHashMap<>();
        Map<Integer, ItemStack> before = new LinkedHashMap<>();
        for (var entry : event.getNewItems().entrySet()) {
            int raw = entry.getKey();
            if (raw < 0 || raw >= getSize() + 36
                || (raw >= session.inventory().getSize() && raw < getSize())
                || (raw < getSize() && !accepted.test(entry.getValue()))) {
                onItemInteraction(false);
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
                onItemInteraction(false);
                return;
            }
            for (var entry : before.entrySet()) {
                if (!Objects.equals(entry.getValue(), itemAt(entry.getKey()))) {
                    onItemInteraction(false);
                    return;
                }
            }
            session.transfer(() -> {
                changes.forEach(this::setItemAt);
                player.setItemOnCursor(newCursor);
            });
            onItemInteraction(!changes.isEmpty());
        });
    }

    private void defer(final InventoryContext context, final Runnable transfer) {
        if (!defer(context, transfer, true)) {
            onItemInteraction(false);
        }
    }

    private boolean defer(final InventoryContext context, final Runnable transfer, final boolean blockInteractions) {
        if (blockInteractions) {
            pending = true;
        }
        // A cancelled drag restores its cursor after dispatch; mutate and save only on the next tick.
        boolean scheduled = schedules.runForLater(player, 1L, () -> {
            if (blockInteractions) {
                pending = false;
            }
            if (closed || context.getInventoryService().getCurrentView(player.getUniqueId()).orElse(null) != this) {
                return;
            }
            try {
                transfer.run();
                if (!closed) {
                    context.getInventoryService().refresh(player);
                }
            } catch (RuntimeException failure) {
                onItemInteraction(false);
                player.closeInventory();
                throw failure;
            }
        }, () -> {
            if (blockInteractions) {
                pending = false;
            }
        }).isPresent();
        if (!scheduled && blockInteractions) {
            pending = false;
        }
        return scheduled;
    }

    private void transfer(final boolean top, final int slot, final ClickType click) {
        Inventory source = top ? session.inventory() : player.getInventory();
        ItemStack current = source.getItem(slot);
        if (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) {
            if (current == null || current.isEmpty() || (!top && !accepted.test(current))) {
                return;
            }
            ItemStack remaining = shift(current, top);
            source.setItem(slot, remaining.isEmpty() ? null : remaining);
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

    private ItemStack shift(final ItemStack current, final boolean toPlayer) {
        Inventory target = toPlayer ? player.getInventory() : session.inventory();
        ItemStack remaining = current.clone();
        int size = toPlayer ? 36 : target.getSize();
        // Chest quick-move merges first, then fills empty slots. Returning items traverses
        // the displayed player slots backwards (hotbar 8..0, then backpack 35..9).
        for (int pass = 0; pass < 2 && !remaining.isEmpty(); pass++) {
            for (int index = 0; index < size && !remaining.isEmpty(); index++) {
                int slot = toPlayer ? index < 9 ? 8 - index : 44 - index : index;
                ItemStack existing = target.getItem(slot);
                boolean empty = existing == null || existing.isEmpty();
                if (pass == 0 ? empty || !existing.isSimilar(remaining) : !empty) {
                    continue;
                }
                int amount = empty ? 0 : existing.getAmount();
                int moved = Math.min(remaining.getAmount(), remaining.getMaxStackSize() - amount);
                if (moved <= 0) {
                    continue;
                }
                ItemStack placed = remaining.clone();
                placed.setAmount(amount + moved);
                target.setItem(slot, placed);
                remaining.setAmount(remaining.getAmount() - moved);
            }
        }
        return remaining;
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
