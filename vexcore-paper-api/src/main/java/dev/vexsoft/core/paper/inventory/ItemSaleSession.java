package dev.vexsoft.core.paper.inventory;

import dev.vexsoft.core.api.service.currency.CurrencyRegistry;
import dev.vexsoft.core.api.service.player.DataService;
import dev.vexsoft.core.api.service.player.PlayerService;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.currency.Currency;
import dev.vexsoft.core.currency.CurrencyContainer;
import dev.vexsoft.core.currency.CurrencyKey;
import dev.vexsoft.core.number.WholeAmount;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Owner-thread item escrow with replayable currency settlement. Native inventory and escrow are
 * checkpointed together in player data; currency receipts survive independent database saves.
 */
public final class ItemSaleSession {
    private static final NamespacedKey JOURNAL = new NamespacedKey("vexcore", "item_sale");
    private final Player player;
    private final VexServiceRegistry services;
    private final Inventory escrow;
    private final Function<byte[], ItemStack> decoder;
    private final List<ItemStack> pendingReturns = new ArrayList<>();
    private String operation = "";
    private String currencyKey = "";
    private WholeAmount pendingAmount = WholeAmount.ZERO;
    private List<ItemStack> sold = new ArrayList<>();
    private boolean closed;

    /** Opens or restores a checkpoint. Call recover before opening a new gameplay menu. */
    public ItemSaleSession(final VexServiceRegistry services, final Player player, final int slots) {
        this(services, player, Bukkit.createInventory(null, slots), ItemStack::deserializeBytes);
    }

    ItemSaleSession(final VexServiceRegistry services, final Player player, final Inventory escrow,
                    final Function<byte[], ItemStack> decoder) {
        this.services = services;
        this.player = player;
        this.escrow = escrow;
        this.decoder = decoder;
        restore();
    }

    /** Returns the backing slots; call checkpoint after an owner-thread transfer. */
    public Inventory inventory() {
        return escrow;
    }

    /** Returns whether an interrupted settlement prevents further transfers. */
    public boolean isLocked() {
        return closed || !operation.isEmpty();
    }

    /** Applies one input transfer and rolls back both inventories and the cursor if it fails. */
    public void transfer(final Runnable operation) {
        if (isLocked()) {
            return;
        }
        ItemStack[] previousEscrow = escrow.getStorageContents();
        for (int slot = 0; slot < previousEscrow.length; slot++) {
            previousEscrow[slot] = previousEscrow[slot] == null ? null : previousEscrow[slot].clone();
        }
        ItemStack previousCursor = player.getItemOnCursor().clone();
        try {
            InventoryTransactions.withRollback(player.getInventory(), () -> {
                operation.run();
                checkpoint();
                return true;
            });
        } catch (RuntimeException failure) {
            escrow.setStorageContents(previousEscrow);
            player.setItemOnCursor(previousCursor);
            try {
                checkpoint();
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }

    /** Prices eligible items in the supplied inventory without changing them. */
    public static ItemSaleQuote quote(final Inventory source, final Function<ItemStack, WholeAmount> valuation) {
        WholeAmount total = WholeAmount.ZERO;
        int count = 0;
        for (ItemStack item : source.getStorageContents()) {
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

    /** Sells the current eligible selection only when it still matches the displayed quote. */
    public boolean sell(
        final Inventory source,
        final Currency currency,
        final Function<ItemStack, WholeAmount> valuation,
        final ItemSaleQuote shown
    ) {
        if (isLocked()) {
            return false;
        }
        ItemStack[] before = source.getStorageContents();
        ItemStack[] after = before.clone();
        List<ItemStack> selected = new ArrayList<>();
        WholeAmount amount = WholeAmount.ZERO;
        int count = 0;
        for (int slot = 0; slot < after.length; slot++) {
            ItemStack item = after[slot];
            if (item != null && !item.isEmpty()) {
                WholeAmount price = valuation.apply(item);
                if (price != null && price.isPositive()) {
                    selected.add(item.clone());
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
        operation = UUID.randomUUID().toString();
        currencyKey = currency.getKey().toString();
        pendingAmount = current.amount();
        sold = selected;
        source.setStorageContents(after);
        // Persist removal plus pending payout before touching the independent currency database.
        checkpoint();
        return settle();
    }

    boolean settle() {
        if (operation.isEmpty()) {
            return true;
        }
        Currency currency = services.require(CurrencyRegistry.class).find(CurrencyKey.parse(currencyKey)).orElse(null);
        if (currency == null) {
            return false;
        }
        CurrencyContainer wallet = services.require(PlayerService.class)
            .require(player.getUniqueId()).getContainer(CurrencyContainer.class);
        var result = wallet.depositOnce(currency, pendingAmount, "item-sale:" + operation);
        if (!result.successful()) {
            pendingReturns.addAll(sold);
            clearSettlement();
            returnItems();
            checkpoint();
            return false;
        }
        // Keep the native journal until the balance AND its replay receipt are durable.
        services.require(DataService.class).save(player.getUniqueId()).join();
        clearSettlement();
        checkpoint();
        return true;
    }

    private void clearSettlement() {
        operation = "";
        currencyKey = "";
        pendingAmount = WholeAmount.ZERO;
        sold = new ArrayList<>();
    }

    /** Returns unsold items without dropping overflow and checkpoints the remaining obligation. */
    public void close() {
        if (closed) {
            return;
        }
        for (ItemStack item : escrow.getStorageContents()) {
            if (item != null && !item.isEmpty()) {
                pendingReturns.add(item.clone());
            }
        }
        escrow.clear();
        ItemStack cursor = player.getItemOnCursor();
        if (!cursor.isEmpty()) {
            pendingReturns.add(cursor.clone());
            player.setItemOnCursor(null);
        }
        if (!player.isDead()) {
            returnItems();
        }
        checkpoint();
        closed = true;
    }

    private void returnItems() {
        List<ItemStack> remaining = new ArrayList<>();
        InventoryTransactions.withRollback(player.getInventory(), () -> {
            for (ItemStack item : pendingReturns) {
                remaining.addAll(player.getInventory().addItem(item.clone()).values());
            }
            return true;
        });
        pendingReturns.clear();
        pendingReturns.addAll(remaining);
    }

    /** Replays a pending payout and returns escrow on login; false keeps an unresolved sale locked. */
    public static boolean recover(final VexServiceRegistry services, final Player player) {
        if (!player.getPersistentDataContainer().has(JOURNAL, PersistentDataType.BYTE_ARRAY)) {
            return true;
        }
        ItemSaleSession session = new ItemSaleSession(services, player, 36);
        boolean settled = session.settle();
        session.close();
        return settled;
    }

    /** Saves escrow and cursor with the native inventory. Must run after cancelled-event transfers. */
    public void checkpoint() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(1);
                writeItems(output, Arrays.asList(escrow.getStorageContents()));
                writeItems(output, pendingReturns);
                writeItems(output, List.of(player.getItemOnCursor()));
                output.writeUTF(operation);
                output.writeUTF(currencyKey);
                output.writeUTF(pendingAmount.toString());
                writeItems(output, sold);
            }
            if (escrow.isEmpty() && pendingReturns.isEmpty() && operation.isEmpty()
                && player.getItemOnCursor().isEmpty()) {
                player.getPersistentDataContainer().remove(JOURNAL);
            } else {
                player.getPersistentDataContainer().set(JOURNAL, PersistentDataType.BYTE_ARRAY, bytes.toByteArray());
            }
            player.saveData();
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to checkpoint item escrow", failure);
        }
    }

    private void restore() {
        byte[] bytes = player.getPersistentDataContainer().get(JOURNAL, PersistentDataType.BYTE_ARRAY);
        if (bytes == null) {
            return;
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != 1) {
                throw new IOException("Unsupported escrow version");
            }
            List<ItemStack> contents = readItems(input);
            for (int slot = 0; slot < contents.size(); slot++) {
                if (slot < escrow.getSize()) {
                    escrow.setItem(slot, contents.get(slot));
                } else if (contents.get(slot) != null) {
                    pendingReturns.add(contents.get(slot));
                }
            }
            pendingReturns.addAll(readItems(input));
            List<ItemStack> cursor = readItems(input);
            // Native player saves do not include the carried cursor; recover its checkpoint after a crash.
            if (player.getItemOnCursor().isEmpty()) {
                cursor.stream().filter(item -> item != null && !item.isEmpty()).forEach(pendingReturns::add);
            }
            operation = input.readUTF();
            currencyKey = input.readUTF();
            pendingAmount = WholeAmount.parse(input.readUTF());
            sold = readItems(input);
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to restore item escrow", failure);
        }
    }

    private static void writeItems(final DataOutputStream output, final List<ItemStack> items) throws IOException {
        output.writeInt(items.size());
        for (ItemStack item : items) {
            byte[] data = item == null || item.isEmpty() ? new byte[0] : item.serializeAsBytes();
            output.writeInt(data.length);
            output.write(data);
        }
    }

    private List<ItemStack> readItems(final DataInputStream input) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > 4096) {
            throw new IOException("Invalid escrow item count");
        }
        List<ItemStack> items = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            int length = input.readInt();
            if (length < 0 || length > 4_194_304) {
                throw new IOException("Invalid escrow item length");
            }
            byte[] data = input.readNBytes(length);
            if (data.length != length) {
                throw new IOException("Truncated escrow item");
            }
            items.add(length == 0 ? null : decoder.apply(data));
        }
        return items;
    }
}
