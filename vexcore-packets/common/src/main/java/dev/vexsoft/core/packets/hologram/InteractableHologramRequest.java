package dev.vexsoft.core.packets.hologram;

import dev.vexsoft.core.packets.display.FakeTextDisplayRequest;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.util.Vector;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class InteractableHologramRequest {

  private final FakeTextDisplayRequest textDisplayRequest;
  private final float hitboxWidth;
  private final float hitboxHeight;
  private final Vector hitboxOffset;
  private final HologramInteractHandler interactHandler;

  public static InteractableHologramRequestBuilder builder(
      final Location location,
      final Component text
  ) {
    return builder(FakeTextDisplayRequest.builder(location, text).build());
  }

  public static InteractableHologramRequestBuilder builder(
      final FakeTextDisplayRequest textDisplayRequest
  ) {
    return internalBuilder()
        .textDisplayRequest(textDisplayRequest)
        .hitboxWidth(1.5F)
        .hitboxHeight(0.5F)
        .hitboxOffset(new Vector())
        .interactHandler(interaction -> { });
  }

  public Vector getHitboxOffset() {
    return hitboxOffset.clone();
  }

  @Builder(builderMethodName = "internalBuilder")
  private static InteractableHologramRequest create(
      final FakeTextDisplayRequest textDisplayRequest,
      final float hitboxWidth,
      final float hitboxHeight,
      final Vector hitboxOffset,
      final HologramInteractHandler interactHandler
  ) {
    if (hitboxWidth <= 0.0F || hitboxHeight <= 0.0F) {
      throw new IllegalArgumentException("hitbox dimensions must be positive");
    }
    return new InteractableHologramRequest(
        Objects.requireNonNull(textDisplayRequest, "textDisplayRequest"),
        hitboxWidth,
        hitboxHeight,
        Objects.requireNonNull(hitboxOffset, "hitboxOffset").clone(),
        Objects.requireNonNull(interactHandler, "interactHandler")
    );
  }
}
