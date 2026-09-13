package dev.vexsoft.core.paper.inventory;

import static org.junit.jupiter.api.Assertions.*;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.service.currency.CurrencyRegistry;
import dev.vexsoft.core.api.service.player.DataService;
import dev.vexsoft.core.api.service.player.PlayerService;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.currency.Currency;
import dev.vexsoft.core.currency.CurrencyContainer;
import dev.vexsoft.core.currency.CurrencyKey;
import dev.vexsoft.core.currency.CurrencyTransaction;
import dev.vexsoft.core.number.WholeAmount;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;

/** Exercises native-checkpoint/database interruption boundaries without a running server. */
final class ItemSaleSessionTest {
    private static final Function<ItemStack, WholeAmount> PRICE = item -> WholeAmount.of(100);

    @Test
    void staleQuoteNeverRemovesItemsOrCreditsCurrency() {
        Fixture fixture = new Fixture();
        fixture.storage.slots[0] = new Stack(3);
        ItemSaleSession session = fixture.session();
        assertFalse(session.sell(fixture.storage.inventory, fixture.currency, PRICE,
            new ItemSaleQuote(2, WholeAmount.of(200))));
        assertEquals(3, fixture.storage.count());
        assertEquals(0, fixture.balance);
    }

    @Test
    void failedTransferRestoresInventoryEscrowAndCursor() {
        Fixture fixture = new Fixture();
        fixture.storage.slots[0] = new Stack(3);
        ItemSaleSession session = fixture.session();
        assertThrows(IllegalStateException.class, () -> session.transfer(() -> {
            fixture.storage.inventory.setItem(0, null);
            session.inventory().setItem(0, new Stack(2));
            fixture.cursor = new Stack(1);
            throw new IllegalStateException("Transfer failed");
        }));
        assertEquals(3, fixture.storage.count());
        assertTrue(session.inventory().isEmpty());
        assertTrue(fixture.cursor.isEmpty());
    }

    @Test
    void successCheckpointsRemovalAndDurablePayout() {
        Fixture fixture = new Fixture();
        fixture.storage.slots[0] = new Stack(3);
        ItemSaleSession session = fixture.session();
        assertTrue(session.sell(fixture.storage.inventory, fixture.currency, PRICE,
            new ItemSaleQuote(3, WholeAmount.of(300))));
        assertEquals(0, fixture.storage.count());
        assertEquals(300, fixture.durableBalance);
        assertTrue(fixture.pdc.isEmpty());
    }

    @Test
    void failedDatabaseSaveRetainsReplayableSaleAcrossRestart() {
        Fixture fixture = new Fixture();
        fixture.storage.slots[0] = new Stack(2);
        fixture.failDatabase = true;
        ItemSaleSession session = fixture.session();
        assertThrows(RuntimeException.class, () -> session.sell(fixture.storage.inventory, fixture.currency, PRICE,
            new ItemSaleQuote(2, WholeAmount.of(200))));
        fixture.restart();
        assertTrue(fixture.session().settle());
        assertEquals(200, fixture.durableBalance);
        assertEquals(0, fixture.storage.count());
    }

    @Test
    void crashAfterDatabaseAcknowledgementDoesNotPayTwice() {
        Fixture fixture = new Fixture();
        fixture.storage.slots[0] = new Stack(2);
        fixture.failNativeAt = 2;
        ItemSaleSession session = fixture.session();
        assertThrows(IllegalStateException.class, () -> session.sell(fixture.storage.inventory, fixture.currency, PRICE,
            new ItemSaleQuote(2, WholeAmount.of(200))));
        fixture.restart();
        assertTrue(fixture.session().settle());
        assertEquals(200, fixture.balance);
        assertEquals(1, fixture.receipts.size());
    }

    @Test
    void rejectedCreditReturnsTheOriginalItems() {
        Fixture fixture = new Fixture();
        fixture.rejectCredit = true;
        ItemSaleSession session = fixture.session();
        session.inventory().setItem(0, new Stack(4));
        assertFalse(session.sell(session.inventory(), fixture.currency, PRICE,
            new ItemSaleQuote(4, WholeAmount.of(400))));
        assertEquals(4, fixture.storage.count());
        assertEquals(0, fixture.balance);
    }

    @Test
    void closePreservesOverflowAndRecoversItWhenSpaceIsAvailable() {
        Fixture fixture = new Fixture();
        for (int index = 0; index < fixture.storage.slots.length; index++) {
            fixture.storage.slots[index] = new Stack(64);
        }
        ItemSaleSession session = fixture.session();
        session.inventory().setItem(0, new Stack(5));
        session.close();
        assertFalse(fixture.savedPdc.isEmpty());
        fixture.restart();
        fixture.storage.slots[0] = null;
        fixture.session().close();
        assertEquals(5, fixture.storage.slots[0].getAmount());
        assertTrue(fixture.savedPdc.isEmpty());
    }

    @Test
    void deathDefersReturnAndCloseIsIdempotent() {
        Fixture fixture = new Fixture();
        ItemSaleSession session = fixture.session();
        session.inventory().setItem(0, new Stack(5));
        fixture.dead = true;
        session.close();
        session.close();
        assertEquals(0, fixture.storage.count());
        fixture.restart();
        fixture.session().close();
        assertEquals(5, fixture.storage.count());
    }

    @Test
    void cursorCheckpointSurvivesNativeCursorLoss() {
        Fixture fixture = new Fixture();
        ItemSaleSession session = fixture.session();
        fixture.cursor = new Stack(7);
        session.checkpoint();
        fixture.restart();
        fixture.session().close();
        assertEquals(7, fixture.storage.count());
    }

    static final class Fixture {
        final Map<NamespacedKey, byte[]> pdc = new HashMap<>();
        Map<NamespacedKey, byte[]> savedPdc = new HashMap<>();
        final Slots storage = new Slots(36, true);
        ItemStack[] savedStorage = new ItemStack[36];
        ItemStack cursor = new Stack(0);
        int nativeSaves;
        int failNativeAt = -1;
        boolean failDatabase;
        boolean rejectCredit;
        boolean dead;
        int balance;
        int durableBalance;
        Set<String> receipts = new HashSet<>();
        Set<String> durableReceipts = new HashSet<>();
        final Currency currency = proxy(Currency.class, (ignored, method, args) -> switch (method.getName()) {
            case "getKey" -> CurrencyKey.of("test", "coins");
            case "isRegistered" -> true;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        final PersistentDataContainer container = proxy(PersistentDataContainer.class, (ignored, method, args) -> {
            switch (method.getName()) {
                case "get":
                    return pdc.get(args[0]);
                case "has":
                    return pdc.containsKey(args[0]);
                case "set":
                    pdc.put((NamespacedKey) args[0], ((byte[]) args[2]).clone());
                    return null;
                case "remove":
                    pdc.remove(args[0]);
                    return null;
                default:
                    throw new UnsupportedOperationException(method.getName());
            }
        });
        final UUID id = UUID.randomUUID();
        final Player player = proxy(Player.class, (ignored, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "getPersistentDataContainer" -> container;
            case "getInventory" -> storage.inventory;
            case "getItemOnCursor" -> cursor;
            case "isDead" -> dead;
            case "setItemOnCursor" -> {
                cursor = args[0] == null ? new Stack(0) : ((ItemStack) args[0]).clone();
                yield null;
            }
            case "saveData" -> {
                if (++nativeSaves == failNativeAt) {
                    throw new IllegalStateException("Native storage unavailable");
                }
                savedPdc = new HashMap<>(pdc);
                savedStorage = copy(storage.slots);
                yield null;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        });
        final CurrencyContainer wallet = proxy(CurrencyContainer.class, (ignored, method, args) -> {
            if (method.getName().equals("depositOnce")) {
                int previous = balance;
                if (!rejectCredit && receipts.add((String) args[2])) {
                    balance += ((WholeAmount) args[1]).toBigInteger().intValueExact();
                }
                return new CurrencyTransaction(rejectCredit ? CurrencyTransaction.Status.MAXIMUM_EXCEEDED
                    : CurrencyTransaction.Status.SUCCESS, WholeAmount.of(previous), WholeAmount.of(balance), "");
            }
            throw new UnsupportedOperationException(method.getName());
        });
        final VexPlayer vexPlayer = new VexPlayer(id, "Merchant", type -> type == CurrencyContainer.class ? 0 : -1);
        final VexServiceRegistry services;

        Fixture() {
            vexPlayer.installContainer(0, CurrencyContainer.class, wallet);
            PlayerService players = proxy(PlayerService.class, (ignored, method, args) -> vexPlayer);
            CurrencyRegistry currencies = proxy(CurrencyRegistry.class,
                (ignored, method, args) -> Optional.of(currency));
            DataService data = proxy(DataService.class, (ignored, method, args) -> {
                if (failDatabase) {
                    return CompletableFuture.failedFuture(new IllegalStateException("Database unavailable"));
                }
                durableBalance = balance;
                durableReceipts = new HashSet<>(receipts);
                return CompletableFuture.completedFuture(null);
            });
            Map<Class<?>, Object> implementations = Map.of(PlayerService.class, players,
                CurrencyRegistry.class, currencies, DataService.class, data);
            services = proxy(VexServiceRegistry.class, (ignored, method, args) -> implementations.get(args[0]));
        }

        ItemSaleSession session() {
            return new ItemSaleSession(services, player, new Slots(36, false).inventory,
                bytes -> new Stack(ByteBuffer.wrap(bytes).getInt()));
        }

        void restart() {
            pdc.clear();
            pdc.putAll(savedPdc);
            storage.slots = copy(savedStorage);
            cursor = new Stack(0);
            balance = durableBalance;
            receipts = new HashSet<>(durableReceipts);
            failDatabase = false;
            failNativeAt = -1;
            dead = false;
        }
    }

    static final class Slots {
        ItemStack[] slots;
        final Inventory inventory;

        Slots(final int size, final boolean player) {
            slots = new ItemStack[size];
            Class<? extends Inventory> type = player ? PlayerInventory.class : Inventory.class;
            inventory = proxy(type, (ignored, method, args) -> {
                switch (method.getName()) {
                    case "getSize":
                        return slots.length;
                    case "getContents":
                    case "getStorageContents":
                        return copy(slots);
                    case "setContents":
                    case "setStorageContents":
                        slots = copy((ItemStack[]) args[0]);
                        return null;
                    case "isEmpty":
                        return count() == 0;
                    case "getItem":
                        return slots[(int) args[0]];
                    case "setItem":
                        slots[(int) args[0]] = args[1] == null ? null : ((ItemStack) args[1]).clone();
                        return null;
                    case "clear":
                        slots = new ItemStack[slots.length];
                        return null;
                    case "addItem":
                        Map<Integer, ItemStack> left = new LinkedHashMap<>();
                        ItemStack[] added = (ItemStack[]) args[0];
                        for (int index = 0; index < added.length; index++) {
                            int remaining = added[index].getAmount();
                            for (int slot = 0; slot < slots.length && remaining > 0; slot++) {
                                int current = slots[slot] == null ? 0 : slots[slot].getAmount();
                                int moved = Math.min(64 - current, remaining);
                                if (moved > 0) {
                                    slots[slot] = new Stack(current + moved);
                                    remaining -= moved;
                                }
                            }
                            if (remaining > 0) {
                                left.put(index, new Stack(remaining));
                            }
                        }
                        return left;
                    default:
                        throw new UnsupportedOperationException(method.getName());
                }
            });
        }

        int count() {
            int count = 0;
            for (ItemStack item : slots) {
                count += item == null ? 0 : item.getAmount();
            }
            return count;
        }
    }

    static final class Stack extends ItemStack {
        private int amount;

        Stack(final int amount) {
            this.amount = amount;
        }

        @Override
        public int getAmount() {
            return amount;
        }

        @Override
        public void setAmount(final int value) {
            amount = value;
        }

        @Override
        public boolean isEmpty() {
            return amount == 0;
        }

        @Override
        public int getMaxStackSize() {
            return 64;
        }

        @Override
        public boolean isSimilar(final ItemStack other) {
            return other instanceof Stack;
        }

        @Override
        public boolean equals(final Object other) {
            return other instanceof Stack stack && stack.amount == amount;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(amount);
        }

        @Override
        public ItemStack clone() {
            return new Stack(amount);
        }

        @Override
        public byte[] serializeAsBytes() {
            return ByteBuffer.allocate(4).putInt(amount).array();
        }
    }

    static ItemStack[] copy(final ItemStack[] items) {
        ItemStack[] result = items.clone();
        for (int index = 0; index < result.length; index++) {
            result[index] = result[index] == null ? null : result[index].clone();
        }
        return result;
    }

    static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
