package dev.vexsoft.core.paper.service.packets.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vexsoft.core.api.service.registry.ServiceOwner;
import dev.vexsoft.core.paper.packets.internal.FakeItemMetaRule;
import dev.vexsoft.core.paper.packets.item.FakeItemEquipmentAnimation;
import java.util.List;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

/** Ensures armor-only overrides remain registered, including an explicit disabled value. */
final class VexFakeItemMetaWornGlintTest {

    @Test
    void retainsWornOnlyRulesUntilTheyAreExplicitlyCleared() {
        var store = new VexFakeItemMetaStoreService(null);
        ServiceOwner owner = () -> "armor-test";
        var viewer = UUID.randomUUID();
        var key = new NamespacedKey("test", "item_id");
        for (boolean enabled : new boolean[] {true, false}) {
            store.update(owner, viewer, key, "armor", current -> FakeItemMetaRule.builder()
                .wornEnchantmentGlint(enabled).build());
            assertTrue(store.hasAny(viewer));
            assertFalse(store.hasAny(UUID.randomUUID()));
            store.update(owner, viewer, key, "armor", current -> FakeItemMetaRule.builder().build());
            assertFalse(store.hasAny(viewer));
        }
    }

    @Test
    void retainsEquipmentAnimationOnlyRulesUntilTheyAreCleared() {
        var store = new VexFakeItemMetaStoreService(null);
        ServiceOwner owner = () -> "armor-test";
        var viewer = UUID.randomUUID();
        var animation = new FakeItemEquipmentAnimation(NamespacedKey.minecraft("iron"),
            List.of(new NamespacedKey("test", "frame")), 2);
        var key = new NamespacedKey("test", "item_id");
        store.update(owner, viewer, key, "armor", current -> FakeItemMetaRule.builder()
            .equipmentAnimation(animation).build());
        assertTrue(store.hasAny(viewer));
        store.clearOwned(owner);
        assertFalse(store.hasAny(viewer));
    }
}
