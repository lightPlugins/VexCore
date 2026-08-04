package dev.vexsoft.core.packets.display;

import lombok.Builder;
import lombok.Value;
import org.bukkit.inventory.ItemStack;

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

  public static FakeItemDisplayUpdate item(final ItemStack itemStack) {
    return builder().itemStack(itemStack).build();
  }
}
