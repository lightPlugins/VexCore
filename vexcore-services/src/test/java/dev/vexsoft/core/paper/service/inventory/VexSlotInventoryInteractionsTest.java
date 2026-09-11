package dev.vexsoft.core.paper.service.inventory;

import static org.junit.jupiter.api.Assertions.*;

import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.inventory.*;
import dev.vexsoft.core.paper.inventory.element.*;
import dev.vexsoft.core.paper.inventory.page.*;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.stream.IntStream;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

public final class VexSlotInventoryInteractionsTest {

    @Test
    void ordinaryLowerClicksAndDragsStayNative() {
        Fixture fixture = new Fixture();

        for (ClickType type : List.of(ClickType.LEFT, ClickType.RIGHT, ClickType.NUMBER_KEY)) {
            InventoryClickEvent click = fixture.click(60, type);

            fixture.handler.click(fixture.context, fixture.menu, fixture.elements(), click);

            assertFalse(click.isCancelled());
        }

        var drag = fixture.drag(Set.of(60, 61));

        fixture.handler.drag(fixture.context, fixture.menu, fixture.elements(), drag);

        assertFalse(drag.isCancelled());
    }

    @Test
    void shiftMovesBetweenBackpackAndHotbarWithoutTouchingMenu() {
        Fixture fixture = new Fixture();

        fixture.storage[9] = new Stack("ore", 10);
        fixture.storage[0] = new Stack("ore", 60);
        var click = fixture.click(63, ClickType.SHIFT_LEFT);

        fixture.handler.click(fixture.context, fixture.menu, fixture.elements(), click);

        assertTrue(click.isCancelled());
        assertNull(fixture.storage[9]);
        assertEquals(64, fixture.storage[0].getAmount());
        assertEquals(6, fixture.storage[1].getAmount());
        assertEquals(0, fixture.menu.transfers);
    }

    @Test
    void doubleClickCollectsOnlyRealInventoryStacks() {
        Fixture fixture = new Fixture();

        fixture.cursor = new Stack("ore", 1);
        fixture.storage[3] = new Stack("ore", 8);
        var click = fixture.click(57, ClickType.DOUBLE_CLICK);

        fixture.handler.click(fixture.context, fixture.menu, fixture.elements(), click);

        assertTrue(click.isCancelled());
        assertEquals(9, fixture.cursor.getAmount());
        assertNull(fixture.storage[3]);
        assertEquals(0, fixture.menu.transfers);
    }

    @Test
    void exchangeUsesBackingItemsAndNeverTheRenderedPlaceholder() {
        Fixture fixture = new Fixture();

        fixture.cursor = new Stack("helmet", 1);
        fixture.handler.click(fixture.context, fixture.menu, fixture.elements(), fixture.click(11, ClickType.LEFT));

        assertTrue(fixture.cursor.isEmpty());
        assertEquals("helmet", ((Stack) fixture.menu.stored).id);

        fixture.handler.click(fixture.context, fixture.menu, fixture.elements(), fixture.click(11, ClickType.LEFT));

        assertEquals("helmet", ((Stack) fixture.cursor).id);
        assertTrue(fixture.menu.stored.isEmpty());
        assertEquals(2, fixture.menu.transfers);
    }

    @Test
    void singleSlotDragRunsAfterCursorRestoreAndClosingAbortsIt() {
        Fixture fixture = new Fixture();

        fixture.cursor = new Stack("helmet", 1);
        var drag = fixture.drag(Set.of(11));

        fixture.handler.drag(fixture.context, fixture.menu, fixture.elements(), drag);

        assertTrue(drag.isCancelled());
        assertEquals(0, fixture.menu.transfers);

        fixture.runScheduled();

        assertEquals(1, fixture.menu.transfers);

        fixture.cursor = new Stack("helmet", 1);
        fixture.handler.drag(fixture.context, fixture.menu, fixture.elements(), fixture.drag(Set.of(11)));
        fixture.handler.cancel(fixture.id);
        fixture.runScheduled();

        assertEquals(1, fixture.menu.transfers);
    }

    @Test
    void rejectedSchedulingDoesNotBlockFurtherInventoryClicks() {
        Fixture fixture = new Fixture();

        fixture.rejectScheduling = true;
        fixture.handler.drag(fixture.context, fixture.menu, fixture.elements(), fixture.drag(Set.of(11)));
        var click = fixture.click(60, ClickType.LEFT);

        fixture.handler.click(fixture.context, fixture.menu, fixture.elements(), click);

        assertFalse(click.isCancelled());
        assertEquals(0, fixture.menu.transfers);
    }

    @Test
    void mixedDragAndProtectedClicksNeverPartlyTransfer() {
        Fixture fixture = new Fixture();
        var mixed = fixture.drag(Set.of(11, 60));

        fixture.handler.drag(fixture.context, fixture.menu, fixture.elements(), mixed);

        assertTrue(mixed.isCancelled());

        var click = fixture.click(11, ClickType.LEFT);

        click.setCancelled(true);
        fixture.handler.click(fixture.context, fixture.menu, fixture.elements(), click);

        assertEquals(0, fixture.menu.transfers);
        assertTrue(fixture.scheduled.isEmpty());
    }

    @Test
    void existingPaginationShowsTwelveThenSixEntriesAndAcceptsOnlyLeftClick() {
        Fixture fixture = new Fixture();

        assertEquals(2, fixture.menu.getPageCount(fixture.context));

        for (ClickType type : List.of(
            ClickType.RIGHT,
            ClickType.SHIFT_LEFT,
            ClickType.NUMBER_KEY,
            ClickType.SWAP_OFFHAND
        )) {
            var click = fixture.click(44, type);

            fixture.handler.click(fixture.context, fixture.menu, fixture.elements(), click);

            assertEquals(0, fixture.menu.getPage());
            assertTrue(click.isCancelled());
        }

        fixture.handler.click(fixture.context, fixture.menu, fixture.elements(), fixture.click(44, ClickType.LEFT));

        assertEquals(1, fixture.menu.getPage());
        assertEquals(13, ((Entry) fixture.elements().get(14)).number);
        assertEquals(18, ((Entry) fixture.elements().get(25)).number);
        assertFalse(fixture.elements().containsKey(32));

        fixture.handler.click(fixture.context, fixture.menu, fixture.elements(), fixture.click(17, ClickType.LEFT));

        assertEquals(0, fixture.menu.getPage());
    }

    private static final class Fixture {

        final UUID id = UUID.randomUUID();
        final ItemStack[] storage = new ItemStack[36];
        ItemStack cursor = new Stack("empty", 0);
        boolean rejectScheduling;
        final List<Runnable> scheduled = new ArrayList<>();
        final Inventory top = proxy(
            Inventory.class,
            (proxyObject, invokedMethod, arguments) -> invokedMethod.getName().equals("getSize") ? 54 : null
        );
        final PlayerInventory inventory = proxy(
            PlayerInventory.class,
            (proxyObject, invokedMethod, arguments) -> switch (invokedMethod.getName()) {
                case "getItem" -> storage[(int) arguments[0]];
                case "setItem" -> {
                    storage[(int) arguments[0]] = (ItemStack) arguments[1];
                    yield null;
                }
                default -> null;
            }
        );
        final Player player = proxy(
            Player.class,
            (proxyObject, invokedMethod, arguments) -> switch (invokedMethod.getName()) {
                case "getUniqueId" -> id;
                case "getInventory" -> inventory;
                case "getItemOnCursor" -> cursor;
                case "setItemOnCursor" -> {
                    cursor = (ItemStack) arguments[0];
                    yield null;
                }
                case "getOpenInventory" -> this.view;
                default -> null;
            }
        );
        final org.bukkit.inventory.InventoryView view = proxy(
            org.bukkit.inventory.InventoryView.class,
            (proxyObject, invokedMethod, arguments) -> switch (invokedMethod.getName()) {
                case "getPlayer" -> player;
                case "getTopInventory" -> top;
                case "getInventory" -> (int) arguments[0] >= 54 ? inventory : top;
                case "convertSlot" -> (int) arguments[0] >= 54 ? (int) arguments[0] - 54 : (int) arguments[0];
                default -> null;
            }
        );
        final ScheduleService schedules = proxy(
            ScheduleService.class,
            (proxyObject, invokedMethod, arguments) -> {
                if (invokedMethod.getName().equals("runForLater")) {
                    if (rejectScheduling) {
                        return Optional.empty();
                    }

                    scheduled.add((Runnable) arguments[2]);

                    return Optional.of(proxy(
                        dev.vexsoft.core.paper.scheduler.VexTask.class,
                        (task, method, args) -> null
                    ));
                }

                return null;
            }
        );
        final VexServiceRegistry registry =
            proxy(VexServiceRegistry.class, (proxyObject, invokedMethod, arguments) -> schedules);
        final Menu menu = new Menu(registry);
        final InventoryService service = proxy(
            InventoryService.class,
            (proxyObject, invokedMethod, arguments) -> invokedMethod.getName().equals("getCurrentView") ? Optional.of(
                menu) : null
        );
        final InventoryContext context = new InventoryContext(registry, player, service);
        final VexSlotInventoryInteractions handler = new VexSlotInventoryInteractions(schedules);

        Map<Integer, InventoryElement> elements() {
            return menu.getElements(context);
        }

        InventoryClickEvent click(int slot, ClickType type) {
            return new InventoryClickEvent(
                view,
                InventoryType.SlotType.CONTAINER,
                slot,
                type,
                InventoryAction.PICKUP_ALL
            );
        }

        InventoryDragEvent drag(Set<Integer> slots) {
            Map<Integer, ItemStack> contents = new HashMap<>();

            slots.forEach(slotIndex -> contents.put(slotIndex, new Stack("helmet", 1)));

            return new InventoryDragEvent(view, null, cursor, false, contents);
        }

        void runScheduled() {
            List.copyOf(scheduled).forEach(Runnable::run);
            scheduled.clear();
        }
    }

    private static final class Menu extends PagedInventoryView<Integer> implements SlotInventoryView {

        int transfers;
        ItemStack stored = new Stack("empty", 0);

        Menu(VexServiceRegistry registry) {
            super(
                registry,
                InventoryKey.of("test:slots"),
                54,
                PageBounds.rectangle(5, 1, 3, 4),
                context -> IntStream.rangeClosed(1, 18).boxed().toList()
            );
            addElement(
                11,
                new CursorInventoryElement(
                    context -> new Stack("placeholder", 1),
                    context -> {
                        ItemStack incoming = context.getViewer().getItemOnCursor();

                        context.getViewer().setItemOnCursor(stored);
                        stored = incoming;
                        transfers++;
                    }
                )
            );
            setPreviousButton(17, context -> null);
            setNextButton(44, context -> null);
        }

        @Override
        protected InventoryElement renderPageItem(InventoryContext context, Integer number, int index) {
            return new Entry(number);
        }
    }

    private static final class Entry extends RefreshableInventoryElement {

        final int number;

        Entry(int number) {
            super(context -> null);
            this.number = number;
        }
    }

    /** Minimal stack double: no Bukkit server or item factory is involved in routing tests. */
    private static final class Stack extends ItemStack {

        final String id;
        int amount;

        Stack(String id, int amount) {
            this.id = id;
            this.amount = amount;
        }

        @Override
        public ItemStack clone() {
            return new Stack(id, amount);
        }

        @Override
        public int getAmount() {
            return amount;
        }

        @Override
        public void setAmount(int value) {
            amount = value;
        }

        @Override
        public int getMaxStackSize() {
            return 64;
        }

        @Override
        public boolean isEmpty() {
            return amount == 0;
        }

        @Override
        public boolean isSimilar(ItemStack other) {
            return other instanceof Stack otherStack && id.equals(otherStack.id);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Stack otherStack && id.equals(otherStack.id) && amount == otherStack.amount;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, amount);
        }
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
