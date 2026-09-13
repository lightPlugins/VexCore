package dev.vexsoft.core.paper.inventory;

import static dev.vexsoft.core.paper.inventory.ItemSaleSessionTest.proxy;
import static org.junit.jupiter.api.Assertions.*;

import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.inventory.element.RefreshableInventoryElement;
import dev.vexsoft.core.paper.inventory.view.ItemDepositView;
import dev.vexsoft.core.paper.scheduler.VexTask;
import dev.vexsoft.core.paper.service.inventory.InventoryService;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

final class ItemDepositViewTest {
    @Test
    void shiftMovesItemsInBothDirectionsAndNeverMovesAControl() {
        Fixture fixture = new Fixture();
        fixture.data.storage.slots[0] = new ItemSaleSessionTest.Stack(8);
        fixture.click(54, ClickType.SHIFT_LEFT);
        assertEquals(0, fixture.data.storage.count());
        assertEquals(8, fixture.session.inventory().getItem(0).getAmount());
        fixture.click(0, ClickType.SHIFT_LEFT);
        assertEquals(8, fixture.data.storage.count());
        fixture.click(53, ClickType.SHIFT_LEFT);
        assertEquals(1, fixture.controls);
        assertEquals(8, fixture.data.storage.count());
    }

    @Test
    void cursorPickupSplitAndPlacementConserveItems() {
        Fixture fixture = new Fixture();
        fixture.data.storage.slots[0] = new ItemSaleSessionTest.Stack(9);
        fixture.click(54, ClickType.RIGHT);
        assertEquals(5, fixture.data.cursor.getAmount());
        assertEquals(4, fixture.data.storage.count());
        fixture.click(0, ClickType.RIGHT);
        assertEquals(1, fixture.session.inventory().getItem(0).getAmount());
        assertEquals(4, fixture.data.cursor.getAmount());
        fixture.menu.onClose(fixture.context);
        assertEquals(9, fixture.data.storage.count());
    }

    @Test
    void invalidItemsAndAlternativeClickTypesCannotEnterTheInputArea() {
        Fixture fixture = new Fixture();
        fixture.accept = false;
        fixture.data.storage.slots[0] = new ItemSaleSessionTest.Stack(8);
        fixture.click(54, ClickType.SHIFT_LEFT);
        assertEquals(8, fixture.data.storage.count());
        for (ClickType type : List.of(ClickType.DOUBLE_CLICK, ClickType.NUMBER_KEY, ClickType.SWAP_OFFHAND,
            ClickType.DROP, ClickType.CONTROL_DROP, ClickType.MIDDLE)) {
            fixture.click(0, type);
        }
        assertTrue(fixture.session.inventory().isEmpty());
    }

    @Test
    void dragSpanningControlsIsRejectedAndValidDragRunsAfterEventRestoration() {
        Fixture fixture = new Fixture();
        fixture.data.cursor = new ItemSaleSessionTest.Stack(4);
        InventoryDragEvent blocked = fixture.drag(Map.of(0, new ItemSaleSessionTest.Stack(2),
            53, new ItemSaleSessionTest.Stack(2)));
        fixture.menu.onInventoryDrag(fixture.context, blocked);
        assertTrue(blocked.isCancelled());
        assertTrue(fixture.tasks.isEmpty());
        InventoryDragEvent accepted = fixture.drag(Map.of(0, new ItemSaleSessionTest.Stack(2),
            1, new ItemSaleSessionTest.Stack(2)));
        fixture.menu.onInventoryDrag(fixture.context, accepted);
        assertTrue(fixture.session.inventory().isEmpty());
        fixture.run();
        assertEquals(2, fixture.session.inventory().getItem(0).getAmount());
        assertEquals(2, fixture.session.inventory().getItem(1).getAmount());
        assertTrue(fixture.data.cursor.isEmpty());
    }

    @Test
    void closingBeforeDeferredTransferPreventsLateItemMovement() {
        Fixture fixture = new Fixture();
        fixture.data.storage.slots[0] = new ItemSaleSessionTest.Stack(8);
        fixture.menu.onInventoryClick(fixture.context, fixture.event(54, ClickType.SHIFT_LEFT));
        fixture.menu.onClose(fixture.context);
        fixture.run();
        assertEquals(8, fixture.data.storage.count());
        assertTrue(fixture.session.inventory().isEmpty());
    }

    private static final class Fixture {
        final ItemSaleSessionTest.Fixture data = new ItemSaleSessionTest.Fixture();
        final ItemSaleSession session = data.session();
        final List<Runnable> tasks = new ArrayList<>();
        int controls;
        boolean accept = true;
        final ScheduleService scheduler = proxy(ScheduleService.class, (ignored, method, args) -> {
            tasks.add((Runnable) args[2]);
            return Optional.of(proxy(VexTask.class, (task, invoked, values) -> null));
        });
        final VexServiceRegistry registry = proxy(VexServiceRegistry.class,
            (ignored, method, args) -> args[0] == ScheduleService.class ? scheduler : data.services.require((Class) args[0]));
        final ItemDepositView menu = new ItemDepositView(registry, InventoryKey.of("test:sale"), data.player,
            session, item -> accept) {
            {
                addElement(53, new RefreshableInventoryElement(context -> new ItemSaleSessionTest.Stack(1),
                    (context, event) -> controls++));
            }
        };
        final InventoryService inventories = proxy(InventoryService.class,
            (ignored, method, args) -> method.getName().equals("getCurrentView") ? Optional.of(menu) : null);
        final InventoryContext context = new InventoryContext(registry, data.player, inventories);
        final Inventory top = proxy(Inventory.class, (ignored, method, args) -> 54);
        final org.bukkit.inventory.InventoryView view = proxy(org.bukkit.inventory.InventoryView.class,
            (ignored, method, args) -> switch (method.getName()) {
                case "getPlayer" -> data.player;
                case "getTopInventory" -> top;
                case "getInventory" -> (int) args[0] >= 54 ? data.storage.inventory : top;
                case "convertSlot" -> (int) args[0] >= 54 ? (int) args[0] - 54 : (int) args[0];
                default -> null;
            });

        void click(final int raw, final ClickType type) {
            InventoryClickEvent event = event(raw, type);
            menu.onInventoryClick(context, event);
            assertTrue(event.isCancelled());
            run();
        }

        InventoryClickEvent event(final int raw, final ClickType type) {
            return new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, raw, type,
                InventoryAction.PICKUP_ALL);
        }

        InventoryDragEvent drag(final Map<Integer, ItemStack> contents) {
            return new InventoryDragEvent(view, null, data.cursor, false, contents);
        }

        void run() {
            List.copyOf(tasks).forEach(Runnable::run);
            tasks.clear();
        }
    }
}
