package dev.vexsoft.core.paper.mob;

import java.util.Objects;

/** Immutable viewer-specific hologram settings for a mob definition. */
public record MobHologramDefinition(float offsetY, boolean automaticOffset,
                                    MobHologramRenderer renderer) {

  /** Creates and validates hologram settings. */
  public MobHologramDefinition {
    if (!Float.isFinite(offsetY)) {
      throw new IllegalArgumentException("offsetY must be finite");
    }
    Objects.requireNonNull(renderer, "renderer");
  }

  /** Creates an automatically height-adjusted hologram definition. */
  public static MobHologramDefinition automatic(
      final float padding,
      final MobHologramRenderer renderer
  ) {
    return new MobHologramDefinition(padding, true, renderer);
  }

  /** Creates a hologram definition with a fixed passenger offset. */
  public static MobHologramDefinition fixed(
      final float offsetY,
      final MobHologramRenderer renderer
  ) {
    return new MobHologramDefinition(offsetY, false, renderer);
  }
}
