package dev.vexsoft.core.packets.display;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class FakeTextDisplayRequest {

  private final Location location;
  private final Component text;
  private final int lineWidth;
  private final int backgroundColor;
  private final byte textOpacity;
  private final boolean shadowed;
  private final boolean seeThrough;
  private final boolean defaultBackground;
  private final TextDisplayAlignment alignment;
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
  private final Set<DisplayLifecycle> lifecycle;

  public static FakeTextDisplayRequestBuilder builder(
      final Location location,
      final Component text
  ) {
    return internalBuilder()
        .location(location)
        .text(text)
        .lineWidth(200)
        .backgroundColor(0x40000000)
        .textOpacity((byte) 0xFF)
        .alignment(TextDisplayAlignment.CENTER)
        .transformation(DisplayTransformation.identity())
        .billboard(DisplayBillboard.FIXED)
        .viewRange(1.0F)
        .shadowStrength(1.0F)
        .lifecycle(EnumSet.allOf(DisplayLifecycle.class));
  }

  public Location getLocation() {
    return location.clone();
  }

  public Set<DisplayLifecycle> getLifecycle() {
    return copyLifecycle(lifecycle);
  }

  @Builder(builderMethodName = "internalBuilder")
  private static FakeTextDisplayRequest create(
      final Location location,
      final Component text,
      final int lineWidth,
      final int backgroundColor,
      final byte textOpacity,
      final boolean shadowed,
      final boolean seeThrough,
      final boolean defaultBackground,
      final TextDisplayAlignment alignment,
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
      final Set<DisplayLifecycle> lifecycle
  ) {
    Location checkedLocation = Objects.requireNonNull(location, "location").clone();
    if (checkedLocation.getWorld() == null) {
      throw new IllegalArgumentException("location must have a world");
    }
    return new FakeTextDisplayRequest(
        checkedLocation,
        Objects.requireNonNull(text, "text"),
        lineWidth,
        backgroundColor,
        textOpacity,
        shadowed,
        seeThrough,
        defaultBackground,
        Objects.requireNonNull(alignment, "alignment"),
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
        copyLifecycle(lifecycle)
    );
  }

  private static Set<DisplayLifecycle> copyLifecycle(final Set<DisplayLifecycle> lifecycle) {
    Set<DisplayLifecycle> checked = Objects.requireNonNull(lifecycle, "lifecycle");
    return checked.isEmpty()
        ? Set.of()
        : Set.copyOf(EnumSet.copyOf(checked));
  }
}
