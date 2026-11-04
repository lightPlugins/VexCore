package dev.vexsoft.core.paper.packets.display.item;

import dev.vexsoft.core.paper.packets.display.DisplayBillboard;
import dev.vexsoft.core.paper.packets.display.DisplayBrightness;
import dev.vexsoft.core.paper.packets.display.DisplayGlowColor;
import dev.vexsoft.core.paper.packets.display.DisplayTransformation;
import lombok.Builder;
import lombok.Value;
import org.bukkit.inventory.ItemStack;

/** Partial item-display update in which {@code null} properties remain unchanged. */
@Value
@Builder
public class FakeItemDisplayUpdate {

    ItemStack itemStack;
    ItemDisplayTransform itemTransform;
    DisplayTransformation transformation;
    DisplayBillboard billboard;
    DisplayBrightness brightness;
    Float viewRange;
    Float shadowRadius;
    Float shadowStrength;
    Float displayWidth;
    Float displayHeight;
    Integer interpolationDelay;
    Integer interpolationDuration;
    Integer teleportDuration;
    Boolean glowing;
    DisplayGlowColor glowColor;

    public ItemStack getItemStack() {
        return itemStack == null ? null : itemStack.clone();
    }

    /** Creates an update that only replaces the displayed item stack. */
    public static FakeItemDisplayUpdate item(final ItemStack itemStack) {
        return builder().itemStack(itemStack).build();
    }
}
