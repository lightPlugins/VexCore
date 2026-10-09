package dev.vexsoft.core.paper.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

public final class VexComponentDataTest {

    @Test
    public void adventureBreakingUsesImmutablePhysicalCarrierKeysAndCanHideItsNativeTooltip() {
        var source = new HashSet<>(Set.of(NamespacedKey.minecraft("coal_ore")));
        var value = VexComponentData.CAN_BREAK.normalize(new VexAdventureBlocks(source));
        source.clear();
        assertEquals(Set.of(NamespacedKey.minecraft("coal_ore")), value.blocks());
        assertEquals(VexComponentTarget.ITEM, VexComponentData.CAN_BREAK.getTarget());
        assertThrows(IllegalArgumentException.class, () -> new VexAdventureBlocks(Set.of(new NamespacedKey("nexo", "ore"))));
        assertEquals(Set.of(VexComponentKey.CAN_BREAK),
            new VexTooltipDisplay(false, Set.of(VexComponentKey.CAN_BREAK)).hiddenComponents());
        assertEquals(Set.of(), new VexAdventureBlocks(Set.of()).blocks());
    }

    @Test
    public void copiesLoreBeforeStoringIt() {
        List<Component> source = new ArrayList<>();

        source.add(Component.text("First"));

        List<Component> normalized = VexComponentData.LORE.normalize(source);

        source.add(Component.text("Second"));

        assertEquals(List.of(Component.text("First")), normalized);
    }

    @Test
    public void rejectsInvalidStackSizes() {
        assertThrows(IllegalArgumentException.class, () -> VexComponentData.MAX_STACK_SIZE.normalize(0));
        assertThrows(IllegalArgumentException.class, () -> VexComponentData.MAX_STACK_SIZE.normalize(100));
    }

    @Test
    public void validatesNativeCooldownsInTicksWithAnOptionalSharedGroup() {
        var group = new NamespacedKey("isles", "arkie-capsule");
        var cooldown = new VexUseCooldown(200, group);

        assertEquals(cooldown, VexComponentData.USE_COOLDOWN.normalize(cooldown));
        assertEquals(VexComponentTarget.ITEM, VexComponentData.USE_COOLDOWN.getTarget());
        assertEquals(1, new VexUseCooldown(1, null).ticks());
        assertThrows(IllegalArgumentException.class, () -> new VexUseCooldown(0, group));
        assertThrows(IllegalArgumentException.class, () -> new VexUseCooldown(-1, group));
    }

    @Test
    public void marksPresentationComponentsForPackets() {
        assertEquals(VexComponentTarget.PACKET_PRESENTATION, VexComponentData.DISPLAY_NAME.getTarget());
        assertEquals(VexComponentTarget.PACKET_PRESENTATION, VexComponentData.LORE.getTarget());
    }

    @Test
    public void exposesItemModelAndTooltipStyleAsItemComponents() {
        NamespacedKey value = new NamespacedKey("vexcore", "default");

        assertEquals(value, VexComponentData.ITEM_MODEL.normalize(value));
        assertEquals(value, VexComponentData.TOOLTIP_STYLE.normalize(value));
        assertEquals(VexComponentTarget.ITEM, VexComponentData.ITEM_MODEL.getTarget());
        assertEquals(VexComponentTarget.ITEM, VexComponentData.TOOLTIP_STYLE.getTarget());
    }

    @Test
    public void validatesPlayerHeadTexturesBeforeVersionAdaptation() {
        VexPlayerHeadProfile profile = new VexPlayerHeadProfile("e30=");

        assertEquals(profile, VexComponentData.PLAYER_HEAD_PROFILE.normalize(profile));
        assertEquals(VexComponentTarget.ITEM, VexComponentData.PLAYER_HEAD_PROFILE.getTarget());
        assertThrows(IllegalArgumentException.class, () -> new VexPlayerHeadProfile("not base64"));
    }
}
