package dev.vexsoft.core.paper.packets.interaction;

import dev.vexsoft.core.paper.packets.display.DisplayLifecycle;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;

/** Immutable creation request for one viewer-specific virtual interaction entity. */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class FakeInteractionRequest {

  private final Location location;
  private final float width;
  private final float height;
  private final FakeInteractHandler interactHandler;
  private final Set<DisplayLifecycle> lifecycle;

  /**
   * Starts an interaction request with a one-block hitbox and standard lifecycle cleanup.
   *
   * @param location spawn location in the viewer's current world
   * @return configurable request builder
   */
  public static FakeInteractionRequestBuilder builder(final Location location) {
    return internalBuilder()
        .location(location)
        .width(1.0F)
        .height(1.0F)
        .interactHandler(interaction -> { })
        .lifecycle(EnumSet.allOf(DisplayLifecycle.class));
  }

  /**
   * Returns a defensive copy of the interaction location.
   *
   * @return copied interaction location
   */
  public Location getLocation() {
    return location.clone();
  }

  /**
   * Returns the immutable lifecycle policy for this interaction.
   *
   * @return lifecycle events that automatically remove the interaction
   */
  public Set<DisplayLifecycle> getLifecycle() {
    return Set.copyOf(lifecycle);
  }

  @Builder(builderMethodName = "internalBuilder")
  private static FakeInteractionRequest create(
      final Location location,
      final float width,
      final float height,
      final FakeInteractHandler interactHandler,
      final Set<DisplayLifecycle> lifecycle
  ) {
    Location checkedLocation = Objects.requireNonNull(location, "location").clone();
    if (checkedLocation.getWorld() == null) {
      throw new IllegalArgumentException("location must have a world");
    }
    if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0.0F || height <= 0.0F) {
      throw new IllegalArgumentException("interaction dimensions must be finite and positive");
    }
    Set<DisplayLifecycle> checkedLifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    return new FakeInteractionRequest(
        checkedLocation,
        width,
        height,
        Objects.requireNonNull(interactHandler, "interactHandler"),
        checkedLifecycle.isEmpty()
            ? Set.of()
            : Set.copyOf(EnumSet.copyOf(checkedLifecycle))
    );
  }
}
