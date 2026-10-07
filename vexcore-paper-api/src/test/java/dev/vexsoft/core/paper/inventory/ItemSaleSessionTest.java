package dev.vexsoft.core.paper.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import dev.vexsoft.core.api.player.VexPlayer;
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
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

/** Exercises immediate sales, asynchronous storage and ordinary input returns without a running server. */
final class ItemSaleSessionTest {
    private static final Function<ItemStack, WholeAmount> PRICE = item -> WholeAmount.of(100);

    @Test
    void saleFinishesWithoutNativeSavesOrWaitingForTheDatabase() {
        Fixture fixture = new Fixture();
        fixture.storage.slots[0] = new Stack(2);
        fixture.database = new CompletableFuture<>();
        ItemSaleSession session = fixture.session();
        assertTrue(assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
            session.sell(fixture.storage.inventory, fixture.currency, PRICE,
                new ItemSaleQuote(2, WholeAmount.of(200)))));
        assertEquals(200, fixture.balance);
        assertEquals(0, fixture.storage.count());
        assertFalse(fixture.database.isDone());
        assertEquals(0, fixture.nativeSaves);
        fixture.storage.slots[0] = new Stack(5);
        fixture.database.complete(null);
        assertEquals(5, fixture.storage.count());
        assertEquals(200, fixture.balance);
    }

    @Test
    void failedAsyncSaveDoesNotReplayThePayoutOrRestoreSoldItems() {
        Fixture fixture = new Fixture();
        fixture.storage.slots[0] = new Stack(2);
        fixture.database = new CompletableFuture<>();
        assertTrue(fixture.session().sell(fixture.storage.inventory, fixture.currency, PRICE,
            new ItemSaleQuote(2, WholeAmount.of(200))));
        fixture.database.completeExceptionally(new IllegalStateException("Database unavailable"));
        assertEquals(200, fixture.balance);
        assertEquals(0, fixture.storage.count());
        assertEquals(0, fixture.nativeSaves);
    }

    @Test
    void failedEnqueueDoesNotInvalidateACompletedSale() {
        Fixture fixture = new Fixture();
        fixture.storage.slots[0] = new Stack(2);
        fixture.rejectSave = true;
        assertTrue(fixture.session().sell(fixture.storage.inventory, fixture.currency, PRICE,
            new ItemSaleQuote(2, WholeAmount.of(200))));
        assertEquals(200, fixture.balance);
        assertEquals(0, fixture.storage.count());
    }

    @Test
    void rejectedCreditKeepsTheOriginalSelectionAndChangedQuotesDoNotSell() {
        Fixture fixture = new Fixture();
        fixture.rejectCredit = true;
        ItemSaleSession session = fixture.session();
        session.inventory().setItem(0, new Stack(4));
        assertFalse(session.sell(session.inventory(), fixture.currency, PRICE,
            new ItemSaleQuote(4, WholeAmount.of(400))));
        assertEquals(4, session.inventory().getItem(0).getAmount());
        assertEquals(0, fixture.balance);
        fixture.rejectCredit = false;
        assertFalse(session.sell(session.inventory(), fixture.currency, PRICE,
            new ItemSaleQuote(3, WholeAmount.of(300))));
        assertEquals(4, session.inventory().getItem(0).getAmount());
        assertEquals(0, fixture.balance);
    }

    @Test
    void closeReturnsInputAndCursorOnlyOnceAndDropsOverflow() {
        Fixture fixture = new Fixture();
        for (int index = 0; index < fixture.storage.slots.length; index++) {
            fixture.storage.slots[index] = new Stack(64);
        }
        ItemSaleSession session = fixture.session();
        session.inventory().setItem(0, new Stack(5));
        fixture.cursor = new Stack(7);
        session.close();
        session.close();
        assertEquals(12, fixture.dropped);
        assertEquals(0, fixture.cursor.getAmount());
        assertTrue(session.inventory().isEmpty());
        assertTrue(session.isLocked());
        assertEquals(0, fixture.nativeSaves);
        assertFalse(session.sell(fixture.storage.inventory, fixture.currency, PRICE,
            ItemSaleSession.quote(fixture.storage.inventory, PRICE)));
    }

    @Test
    void ordinaryCloseReturnsItemsAndDeathUsesWorldDrops() {
        Fixture fixture = new Fixture();
        ItemSaleSession session = fixture.session();
        session.inventory().setItem(0, new Stack(5));
        session.close();
        assertEquals(5, fixture.storage.count());
        assertEquals(0, fixture.dropped);
        fixture.dead = true;
        ItemSaleSession death = fixture.session();
        death.inventory().setItem(0, new Stack(3));
        death.close();
        death.close();
        assertEquals(3, fixture.dropped);
        assertEquals(5, fixture.storage.count());
    }

    static final class Fixture {
        final Slots storage = new Slots(36, true);
        ItemStack cursor = new Stack(0);
        int nativeSaves;
        boolean rejectSave;
        boolean rejectCredit;
        boolean dead;
        int balance;
        int dropped;
        CompletableFuture<Void> database = CompletableFuture.completedFuture(null);
        final World world = proxy(World.class, (ignored, method, args) -> {
            assertEquals("dropItemNaturally", method.getName());
            dropped += ((ItemStack) args[1]).getAmount();
            return null;
        });
        final Currency currency = proxy(Currency.class, (ignored, method, args) -> switch (method.getName()) {
            case "getKey" -> CurrencyKey.of("test", "coins");
            case "isRegistered" -> true;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        final UUID id = UUID.randomUUID();
        final Player player = proxy(Player.class, (ignored, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "getInventory" -> storage.inventory;
            case "getWorld" -> world;
            case "getLocation" -> null;
            case "getItemOnCursor" -> cursor;
            case "isDead" -> dead;
            case "setItemOnCursor" -> {
                cursor = args[0] == null ? new Stack(0) : ((ItemStack) args[0]).clone();
                yield null;
            }
            case "saveData" -> {
                nativeSaves++;
                yield null;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        });
        final CurrencyContainer wallet = proxy(CurrencyContainer.class, (ignored, method, args) -> {
            if (method.getName().equals("deposit")) {
                int previous = balance;
                if (!rejectCredit) {
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
            DataService data = proxy(DataService.class, (ignored, method, args) -> {
                assertEquals("save", method.getName());
                if (rejectSave) {
                    throw new IllegalStateException("Save queue unavailable");
                }
                return database;
            });
            Map<Class<?>, Object> implementations = Map.of(PlayerService.class, players, DataService.class, data);
            services = proxy(VexServiceRegistry.class, (ignored, method, args) -> implementations.get(args[0]));
        }

        ItemSaleSession session() {
            return new ItemSaleSession(services, player, new Slots(36, false).inventory);
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

    @AllArgsConstructor(access = AccessLevel.PACKAGE)
    static final class Stack extends ItemStack {
        @Getter(onMethod_ = @Override)
        @Setter(onMethod_ = @Override)
        private int amount;

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
