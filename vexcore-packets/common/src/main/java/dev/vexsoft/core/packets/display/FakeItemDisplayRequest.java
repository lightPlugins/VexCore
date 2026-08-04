package dev.vexsoft.core.packets.display;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class FakeItemDisplayRequest {

  private final Location location;
  private final ItemStack itemStack;
  private final ItemDisplayTransform itemTransform;
  private final DisplayTransformation transformation;
  private final DisplayBillboard billboard;
  private final DisplayBrightness brightness;
  private final float viewRange;
  private final float shadowRadius;
  private final float shadowStrength;
  private final float displayWidth;
  private final float displayHeight;
  private final int interpolationDelay;
  private final int interpolationDuration;
  private final int teleportDuration;
  private final boolean glowing;
  private final DisplayGlowColor glowColor;
  private final Set<DisplayLifecycle> lifecycle;

  public static FakeItemDisplayRequestBuilder builder(
      final Location location,
      final ItemStack itemStack
  ) {
    return internalBuilder()
        .location(location)
        .itemStack(itemStack)
        .itemTransform(ItemDisplayTransform.FIXED)
        .transformation(DisplayTransformation.identity())
        .billboard(DisplayBillboard.FIXED)
        .viewRange(1.0F)
        .shadowStrength(1.0F)
        .lifecycle(EnumSet.allOf(DisplayLifecycle.class));
  }

  public Location getLocation() {
    return location.clone();
  }

  public ItemStack getItemStack() {
    return itemStack.clone();
  }

  public Set<DisplayLifecycle> getLifecycle() {
    return lifecycle;
  }

  @Builder(builderMethodName = "internalBuilder")
  private static FakeItemDisplayRequest create(
      final Location location,
      final ItemStack itemStack,
      final ItemDisplayTransform itemTransform,
      final DisplayTransformation transformation,
      final DisplayBillboard billboard,
      final DisplayBrightness brightness,
      final float viewRange,
      final float shadowRadius,
      final float shadowStrength,
      final float displayWidth,
      final float displayHeight,
      final int interpolationDelay,
      final int interpolationDuration,
      final int teleportDuration,
      final boolean glowing,
      final DisplayGlowColor glowColor,
      final Set<DisplayLifecycle> lifecycle
  ) {
    Location checkedLocation = Objects.requireNonNull(location, "location").clone();
    if (checkedLocation.getWorld() == null) {
      throw new IllegalArgumentException("location must have a world");
    }
    Set<DisplayLifecycle> checkedLifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    return new FakeItemDisplayRequest(
        checkedLocation,
        Objects.requireNonNull(itemStack, "itemStack").clone(),
        Objects.requireNonNull(itemTransform, "itemTransform"),
        Objects.requireNonNull(transformation, "transformation"),
        Objects.requireNonNull(billboard, "billboard"),
        brightness,
        viewRange,
        shadowRadius,
        shadowStrength,
        displayWidth,
        displayHeight,
        interpolationDelay,
        interpolationDuration,
        teleportDuration,
        glowing,
        glowColor,
        checkedLifecycle.isEmpty()
            ? Set.of()
            : Set.copyOf(EnumSet.copyOf(checkedLifecycle))
    );
  }
}
