package dev.vexsoft.core.paper.packets.display;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;

/** Immutable creation request for a viewer-specific block-display damage overlay. */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class FakeBlockDamageOverlayRequest {

  private final Location location;
  private final NamespacedKey modelPrefix;
  private final int damageStage;
  private final DisplayTransformation transformation;
  private final float viewRange;
  private final int interpolationDelay;
  private final int interpolationDuration;
  private final int teleportDuration;
  private final Set<DisplayLifecycle> lifecycle;

  /**
   * Starts a request with an invisible overlay and safe display defaults.
   *
   * <p>Damage stages resolve item models by appending {@code _0} through {@code _9} to the model
   * prefix. Stage {@code -1} renders no overlay.</p>
   *
   * @param location overlay origin
   * @param modelPrefix namespaced resource-pack item-model prefix
   * @return configurable request builder
   */
  public static FakeBlockDamageOverlayRequestBuilder builder(
      final Location location,
      final NamespacedKey modelPrefix
  ) {
    return internalBuilder()
        .location(location)
        .modelPrefix(modelPrefix)
        .damageStage(-1)
        .transformation(DisplayTransformation.identity())
        .viewRange(1.0F)
        .lifecycle(EnumSet.allOf(DisplayLifecycle.class));
  }

  /**
   * Returns a defensive copy of the overlay origin.
   *
   * @return copied location
   */
  public Location getLocation() {
    return location.clone();
  }

  /**
   * Returns an immutable lifecycle policy.
   *
   * @return lifecycle events
   */
  public Set<DisplayLifecycle> getLifecycle() {
    return Set.copyOf(lifecycle);
  }

  @Builder(builderMethodName = "internalBuilder")
  private static FakeBlockDamageOverlayRequest create(
      final Location location,
      final NamespacedKey modelPrefix,
      final int damageStage,
      final DisplayTransformation transformation,
      final float viewRange,
      final int interpolationDelay,
      final int interpolationDuration,
      final int teleportDuration,
      final Set<DisplayLifecycle> lifecycle
  ) {
    Location checkedLocation = Objects.requireNonNull(location, "location").clone();
    if (checkedLocation.getWorld() == null) {
      throw new IllegalArgumentException("location must have a world");
    }
    requireDamageStage(damageStage);
    if (!Float.isFinite(viewRange) || viewRange < 0.0F) {
      throw new IllegalArgumentException("viewRange must be finite and non-negative");
    }
    if (interpolationDelay < 0 || interpolationDuration < 0 || teleportDuration < 0) {
      throw new IllegalArgumentException("display durations must be non-negative");
    }
    Set<DisplayLifecycle> checkedLifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    return new FakeBlockDamageOverlayRequest(
        checkedLocation,
        Objects.requireNonNull(modelPrefix, "modelPrefix"),
        damageStage,
        Objects.requireNonNull(transformation, "transformation"),
        viewRange,
        interpolationDelay,
        interpolationDuration,
        teleportDuration,
        checkedLifecycle.isEmpty()
            ? Set.of()
            : Set.copyOf(EnumSet.copyOf(checkedLifecycle))
    );
  }

  private static void requireDamageStage(final int damageStage) {
    if (damageStage < -1 || damageStage > 9) {
      throw new IllegalArgumentException("damageStage must be between -1 and 9");
    }
  }
}
