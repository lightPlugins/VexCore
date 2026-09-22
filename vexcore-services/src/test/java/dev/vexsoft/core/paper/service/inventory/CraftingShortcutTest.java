package dev.vexsoft.core.paper.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.packets.service.RecipeBookPacketAdapterService;
import dev.vexsoft.core.paper.service.scheduler.ScheduleService;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;

/** Verifies protected crafting inputs, vanilla crafting denial and ingredient preservation. */
public final class CraftingShortcutTest {

    @Test
    void shortcutOccupiesFirstInputAndReturnsExistingIngredients() {
        Fixture fixture = new Fixture();
        fixture.result = new Stack("recipe");
        fixture.matrix[0] = new Stack("ingredient");
        fixture.show();
        assertNull(fixture.result);
        assertTrue(fixture.shortcuts.isShortcut(fixture.matrix[0]));
        assertEquals(List.of(new Stack("ingredient")), fixture.returned);
        fixture.shortcuts.refresh(fixture.player);
        assertEquals(1, fixture.returned.size());
        fixture.shortcuts.hide(fixture.player);
        assertNull(fixture.matrix[0]);
    }

    @Test
    void everyClickTransferIsCancelledAndOnlyPrimaryClicksOpenTheMenu() {
        for (ClickType click : List.of(
            ClickType.LEFT, ClickType.SHIFT_LEFT, ClickType.NUMBER_KEY,
            ClickType.DROP, ClickType.CONTROL_DROP, ClickType.SWAP_OFFHAND, ClickType.DOUBLE_CLICK
        )) {
            Fixture fixture = new Fixture();
            fixture.show();
            InventoryClickEvent event = new InventoryClickEvent(
                fixture.view, InventoryType.SlotType.CRAFTING,
                1, click, InventoryAction.PICKUP_ALL
            );
            fixture.listener.click(event);
            assertTrue(event.isCancelled());
            fixture.scheduled.forEach(Runnable::run);
            assertEquals(click.isLeftClick() || click.isRightClick() ? 1 : 0, fixture.opened);
        }
    }

    @Test
    void draggingIntoAnyCraftingInputIsBlocked() {
        Fixture fixture = new Fixture();
        fixture.show();
        InventoryDragEvent event = new InventoryDragEvent(
            fixture.view, new Stack("cursor"),
            new Stack("cursor"), false, Map.of(3, new Stack("cursor"))
        );
        fixture.listener.drag(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void clearsWorkbenchCraftingAndDoesNotExecuteRetiredAction() {
        Fixture fixture = new Fixture();
        fixture.matrix = new ItemStack[9];
        fixture.matrix[0] = new Stack("ingredient");
        fixture.result = new Stack("recipe");
        fixture.show();
        assertNull(fixture.result);
        assertNull(fixture.matrix[0]);
        assertEquals(1, fixture.returned.size());
        fixture.shortcuts.activate(fixture.player);
        fixture.shortcuts.hide(fixture.player);
        fixture.scheduled.forEach(Runnable::run);
        assertEquals(0, fixture.opened);
        assertFalse(fixture.shortcuts.isShortcut(new Stack("recipe")));
    }

    @Test
    void emptyCraftingSlotsAndRecipeBookPlacementAreBlocked() {
        Fixture fixture = new Fixture();
        fixture.show();
        for (int slot = 0; slot <= 4; slot++) {
            var event = new InventoryClickEvent(
                fixture.view, InventoryType.SlotType.CRAFTING,
                slot, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP
            );
            fixture.listener.click(event);
            assertTrue(event.isCancelled());
        }
        var recipe = new PlayerRecipeBookClickEvent(fixture.player, NamespacedKey.minecraft("stick"), false);
        fixture.listener.recipe(recipe);
        assertTrue(recipe.isCancelled());
        fixture.listener.close(new InventoryCloseEvent(fixture.view));
        assertNull(fixture.matrix[0]);
        fixture.shortcuts.hide(fixture.player);
        assertFalse(fixture.shortcuts.isCraftingBlocked(fixture.player));
    }

    @Test
    void shiftClickCannotInsertIntoWorkbench() {
        Fixture fixture = new Fixture();
        fixture.matrix = new ItemStack[9];
        fixture.show();
        var event = new InventoryClickEvent(fixture.view, InventoryType.SlotType.CONTAINER,
            10, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        fixture.listener.click(event);
        assertTrue(event.isCancelled());
    }

    private static final class Fixture {

        private final UUID id = UUID.randomUUID();
        private final List<Runnable> scheduled = new ArrayList<>();
        private ItemStack result;
        private ItemStack[] matrix = new ItemStack[4];
        private int opened;
        private final List<ItemStack> returned = new ArrayList<>();
        private final PlayerInventory playerInventory = proxy(
            PlayerInventory.class, (object, method, arguments) -> {
                if (method.getName().equals("addItem")) {
                    returned.addAll(List.of((ItemStack[]) arguments[0]));
                    return new HashMap<Integer, ItemStack>();
                }
                return null;
            }
        );
        private final CraftingInventory inventory = proxy(
            CraftingInventory.class, (object, method, arguments) ->
                switch (method.getName()) {
                    case "getResult" -> result;
                    case "setResult" -> {
                        result = (ItemStack) arguments[0];
                        yield null;
                    }
                    case "getMatrix" -> matrix;
                    case "getItem" -> (int) arguments[0] == 0 ? result
                        : (int) arguments[0] > matrix.length ? new Stack("inventory") : matrix[(int) arguments[0] - 1];
                    case "setItem" -> {
                        matrix[(int) arguments[0] - 1] = (ItemStack) arguments[1];
                        yield null;
                    }
                    case "getSize" -> matrix.length + 1;
                    case "getType" -> InventoryType.CRAFTING;
                    default -> null;
                }
        );
        private final Player player = proxy(
            Player.class, (object, method, arguments) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getOpenInventory" -> this.view;
                case "getInventory" -> playerInventory;
                default -> null;
            }
        );
        private final org.bukkit.inventory.InventoryView view = proxy(
            org.bukkit.inventory.InventoryView.class,
            (object, method, arguments) -> switch (method.getName()) {
                case "getPlayer" -> player;
                case "getTopInventory", "getInventory" -> inventory;
                case "getItem" -> (int) arguments[0] == 0 ? result
                        : (int) arguments[0] > matrix.length ? new Stack("inventory") : matrix[(int) arguments[0] - 1];
                case "getCursor" -> null;
                case "convertSlot" -> arguments[0];
                default -> null;
            }
        );
        private final ScheduleService schedules = proxy(
            ScheduleService.class, (object, method, arguments) -> {
                if (method.getName().equals("runFor")) {
                    scheduled.add((Runnable) arguments[1]);
                    return Optional.empty();
                }
                return null;
            }
        );
        private final VexServiceRegistry registry = proxy(
            VexServiceRegistry.class, (object, method, arguments) ->
                arguments[0] == ScheduleService.class ? schedules
                    : arguments[0] == RecipeBookPacketAdapterService.class
                      ? proxy(RecipeBookPacketAdapterService.class, (adapter, called, values) -> null) : this.shortcuts
        );
        private final VexCraftingShortcutService shortcuts = new VexCraftingShortcutService(registry);
        private final CraftingShortcutListener listener = new CraftingShortcutListener(registry);

        private void show() {
            shortcuts.show(player, new Stack("shortcut"), () -> opened++);
        }
    }

    private static final class Stack extends ItemStack {

        private final String id;
        private final Map<NamespacedKey, Object> values = new HashMap<>();

        private Stack(final String id) {
            this.id = id;
        }

        @Override
        public ItemStack clone() {
            Stack copy = new Stack(id);
            copy.values.putAll(values);
            return copy;
        }

        @Override
        public boolean hasItemMeta() {
            return !values.isEmpty();
        }

        @Override
        public ItemMeta getItemMeta() {
            PersistentDataContainer data = proxy(
                PersistentDataContainer.class, (object, method, arguments) ->
                    switch (method.getName()) {
                        case "has" -> values.containsKey(arguments[0]);
                        case "set" -> values.put((NamespacedKey) arguments[0], arguments[2]);
                        default -> null;
                    }
            );
            return proxy(ItemMeta.class, (object, method, arguments) -> data);
        }

        @Override
        public boolean setItemMeta(final ItemMeta meta) {
            return true;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public boolean equals(final Object other) {
            return other instanceof Stack stack && id.equals(stack.id) && values.equals(stack.values);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, values);
        }
    }

    private static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
