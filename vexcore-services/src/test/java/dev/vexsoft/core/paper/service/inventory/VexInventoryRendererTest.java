package dev.vexsoft.core.paper.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

class VexInventoryRendererTest {

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
