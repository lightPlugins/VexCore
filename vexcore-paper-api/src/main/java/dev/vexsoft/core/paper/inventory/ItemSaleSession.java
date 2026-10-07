package dev.vexsoft.core.paper.inventory;

import dev.vexsoft.core.api.service.player.DataService;
import dev.vexsoft.core.api.service.player.PlayerService;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.currency.Currency;
import dev.vexsoft.core.currency.CurrencyContainer;
import dev.vexsoft.core.number.WholeAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Owner-thread item input and immediate currency settlement with asynchronous player-data saves. */
public final class ItemSaleSession {

    private final Player player;
    private final VexServiceRegistry services;
    private final Inventory escrow;
    private boolean closed;

    /** Creates a transient input inventory for the player's menu. */
    public ItemSaleSession(final VexServiceRegistry services, final Player player, final int slots) {
        this(services, player, Bukkit.createInventory(null, slots));
    }

    ItemSaleSession(final VexServiceRegistry services, final Player player, final Inventory escrow) {
        this.services = services;
        this.player = player;
        this.escrow = escrow;
    }

    /** Returns the input inventory, whose contents are returned when the session closes. */
    public Inventory inventory() {
        return escrow;
    }

    /** Returns whether the menu session has closed. */
    public boolean isLocked() {
        return closed;
    }

    /** Applies a validated input transfer immediately on the player's owning thread. */
    public void transfer(final Runnable operation) {
        if (!closed) {
            operation.run();
        }
    }

    /** Prices eligible items in the supplied inventory without changing them. */
    public static ItemSaleQuote quote(final Inventory source, final Function<ItemStack, WholeAmount> valuation) {
        WholeAmount total = WholeAmount.ZERO;
        int count = 0;
        for (ItemStack item : Objects.requireNonNull(source.getStorageContents(), "storage contents")) {
            if (item == null || item.isEmpty()) {
                continue;
            }
            WholeAmount price = valuation.apply(item);
            if (price != null && price.isPositive()) {
                total = total.add(price.multiply(item.getAmount()));
                count += item.getAmount();
            }
        }
        return new ItemSaleQuote(count, total);
    }

    /** Sells the eligible selection when it matches the displayed quote, then queues a database save. */
    public boolean sell(
        final Inventory source,
        final Currency currency,
        final Function<ItemStack, WholeAmount> valuation,
        final ItemSaleQuote shown
    ) {
        if (closed) {
            return false;
        }
        ItemStack[] after = Objects.requireNonNull(source.getStorageContents(), "storage contents");
        WholeAmount amount = WholeAmount.ZERO;
        int count = 0;
        for (int slot = 0; slot < after.length; slot++) {
            ItemStack item = after[slot];
            if (item != null && !item.isEmpty()) {
                WholeAmount price = valuation.apply(item);
                if (price != null && price.isPositive()) {
                    after[slot] = null;
                    amount = amount.add(price.multiply(item.getAmount()));
                    count += item.getAmount();
                }
            }
        }
        ItemSaleQuote current = new ItemSaleQuote(count, amount);
        if (current.count() == 0 || !current.equals(shown)) {
            return false;
        }
        var owner = services.require(PlayerService.class).require(player.getUniqueId());
        if (!owner.getContainer(CurrencyContainer.class).deposit(currency, amount).successful()) {
            return false;
        }
        source.setStorageContents(after);
        UUID playerId = player.getUniqueId();
        try {
            // The sale is complete; database responses never replay a payout or change inventory contents.
            services.require(DataService.class).save(playerId).whenComplete((ignored, failure) -> {
                if (failure != null) {
                    logSaveFailure(playerId, failure);
                }
            });
        } catch (RuntimeException failure) {
            logSaveFailure(playerId, failure);
        }
        return true;
    }

    /** Returns input items and the cursor once; overflow or death returns become ordinary world drops. */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        List<ItemStack> returns = new ArrayList<>();
        for (ItemStack item : Objects.requireNonNull(escrow.getStorageContents(), "input contents")) {
            if (item != null && !item.isEmpty()) {
                returns.add(item);
            }
        }
        escrow.clear();
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && !cursor.isEmpty()) {
            returns.add(cursor.clone());
            player.setItemOnCursor(null);
        }
        for (ItemStack item : returns) {
            if (player.isDead()) {
                drop(item);
            } else {
                player.getInventory().addItem(item).values().forEach(this::drop);
            }
        }
    }

    /** Compatibility hook; transient input sessions no longer recover native crash journals. */
    @Deprecated
    public static boolean recover(final VexServiceRegistry services, final Player player) {
        return true;
    }

    /** Compatibility hook; input transfers no longer write native checkpoints. */
    @Deprecated
    public void checkpoint() {
    }

    private void drop(final ItemStack item) {
        player.getWorld().dropItemNaturally(player.getLocation(), item);
    }

    private static void logSaveFailure(final UUID playerId, final Throwable failure) {
        System.getLogger(ItemSaleSession.class.getName()).log(System.Logger.Level.ERROR,
            "Unable to save item sale player data for " + playerId, failure);
    }
}
