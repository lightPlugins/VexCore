package dev.vexsoft.core.paper.packets.display;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Partial damage-overlay update in which {@code null} properties remain unchanged. */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class FakeBlockDamageOverlayUpdate {

  private final Integer damageStage;
  private final DisplayTransformation transformation;
  private final Integer interpolationDelay;
  private final Integer interpolationDuration;
  private final Integer teleportDuration;

  /**
   * Creates an update that changes only the visible crack stage.
   *
   * @param damageStage {@code -1} to hide or {@code 0-9} to show
   * @return partial damage overlay update
   */
  public static FakeBlockDamageOverlayUpdate stage(final int damageStage) {
    return builder().damageStage(damageStage).build();
  }

  /** Returns a validating partial-update builder. */
  public static FakeBlockDamageOverlayUpdateBuilder builder() {
    return internalBuilder();
  }

  @Builder(builderMethodName = "internalBuilder")
  private static FakeBlockDamageOverlayUpdate create(
      final Integer damageStage,
      final DisplayTransformation transformation,
      final Integer interpolationDelay,
      final Integer interpolationDuration,
      final Integer teleportDuration
  ) {
    if (damageStage != null && (damageStage < -1 || damageStage > 9)) {
      throw new IllegalArgumentException("damageStage must be between -1 and 9");
    }
    requireNonNegative(interpolationDelay, "interpolationDelay");
    requireNonNegative(interpolationDuration, "interpolationDuration");
    requireNonNegative(teleportDuration, "teleportDuration");
    return new FakeBlockDamageOverlayUpdate(
        damageStage,
        transformation,
        interpolationDelay,
        interpolationDuration,
        teleportDuration
    );
  }

  private static void requireNonNegative(final Integer value, final String name) {
    if (value != null && value < 0) {
      throw new IllegalArgumentException(name + " must be non-negative");
    }
  }
}
