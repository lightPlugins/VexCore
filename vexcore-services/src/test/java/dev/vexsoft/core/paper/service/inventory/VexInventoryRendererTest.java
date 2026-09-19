package dev.vexsoft.core.paper.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.inventory.InventoryContext;
import dev.vexsoft.core.paper.inventory.InventoryElement;
import io.papermc.paper.datacomponent.DataComponentType;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class VexInventoryRendererTest {

    @Test
    void refreshOnlyWritesChangedSlotsAndClearsRemovedElements() {
        ItemStack unchanged = new TestStack(4);
        ItemStack[] contents = {unchanged, new TestStack(2), new TestStack(3)};
        List<Integer> writes = new ArrayList<>();
        Inventory inventory = (Inventory) Proxy.newProxyInstance(Inventory.class.getClassLoader(),
            new Class<?>[]{Inventory.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getSize" -> contents.length;
                case "getItem" -> contents[(int) args[0]];
                case "setItem" -> {
                    writes.add((int) args[0]);
                    contents[(int) args[0]] = (ItemStack) args[1];
                    yield null;
                }
                default -> throw new AssertionError("Unexpected inventory operation: " + method.getName());
            });
        InventoryContext context = new InventoryContext(stub(VexServiceRegistry.class), stub(Player.class),
            stub(InventoryService.class));
        Map<Integer, InventoryElement> elements = Map.of(0, viewer -> new TestStack(4),
            1, viewer -> new TestStack(5));
        VexInventoryRenderer renderer = new VexInventoryRenderer();
        renderer.renderInto(context, inventory, elements, null);
        assertEquals(List.of(1, 2), writes);
        org.junit.jupiter.api.Assertions.assertSame(unchanged, contents[0]);
        assertEquals(new TestStack(5), contents[1]);
        assertNull(contents[2]);
        writes.clear();
        renderer.renderInto(context, inventory, elements, null);
        assertEquals(List.of(), writes);
    }

    private static <T> T stub(final Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
            (proxy, method, args) -> null));
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class TestStack extends ItemStack {
        private final int count;

        @Override
        public ItemStack clone() {
            return new TestStack(count);
        }

        @Override
        public Set<DataComponentType> getDataTypes() {
            return Set.of();
        }

        @Override
        public boolean equals(final Object other) {
            return other instanceof TestStack stack && count == stack.count;
        }

        @Override
        public int hashCode() {
            return count;
        }
    }

    @Test
    void usesOwnerFallbackWhenItemHasNoTooltipStyle() {
        NamespacedKey fallback = NamespacedKey.minecraft("common");

        assertEquals(fallback, VexInventoryRenderer.tooltipStyle(null, fallback, false));
    }

    @Test
    void preservesExplicitItemTooltipStyle() {
        NamespacedKey configured = NamespacedKey.minecraft("rare");

        assertEquals(
            configured,
            VexInventoryRenderer.tooltipStyle(configured, NamespacedKey.minecraft("common"), false)
        );
    }

    @Test
    void leavesStyleAbsentWhenOwnerHasNoFallback() {
        assertNull(VexInventoryRenderer.tooltipStyle(null, null, false));
    }

    @Test
    void leavesHiddenDecorationWithoutFallbackStyle() {
        assertNull(VexInventoryRenderer.tooltipStyle(null, NamespacedKey.minecraft("common"), true));
    }
}
