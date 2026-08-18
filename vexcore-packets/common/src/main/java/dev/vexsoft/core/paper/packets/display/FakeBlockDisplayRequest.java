package dev.vexsoft.core.paper.packets.display;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;

/** Immutable creation request for one viewer-specific fake block display. */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class FakeBlockDisplayRequest {

  private final Location location;
  private final BlockData blockData;
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

  /**
   * Starts a request with safe display defaults and defensive copies of mutable inputs.
   *
   * @param location spawn location in the viewer's current world
   * @param blockData block state rendered by the display
   * @return configurable request builder
   */
  public static FakeBlockDisplayRequestBuilder builder(
      final Location location,
      final BlockData blockData
  ) {
    return internalBuilder()
        .location(location)
        .blockData(blockData)
        .transformation(DisplayTransformation.identity())
        .billboard(DisplayBillboard.FIXED)
        .viewRange(1.0F)
        .shadowStrength(1.0F)
        .lifecycle(EnumSet.allOf(DisplayLifecycle.class));
  }

  /**
   * Returns a defensive copy of the spawn location.
   *
   * @return copied display location
   */
  public Location getLocation() {
    return location.clone();
  }

  /**
   * Returns a defensive copy of the displayed block data.
   *
   * @return copied block state
   */
  public BlockData getBlockData() {
    return blockData.clone();
  }

  /**
   * Returns the immutable lifecycle policy for this display.
   *
   * @return lifecycle events that automatically remove the display
   */
  public Set<DisplayLifecycle> getLifecycle() {
    return Set.copyOf(lifecycle);
  }

  @Builder(builderMethodName = "internalBuilder")
  private static FakeBlockDisplayRequest create(
      final Location location,
      final BlockData blockData,
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
    return new FakeBlockDisplayRequest(
        checkedLocation,
        Objects.requireNonNull(blockData, "blockData").clone(),
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
