package dev.vexsoft.core.paper.mob;

import dev.vexsoft.core.paper.packets.display.DisplayBillboard;
import dev.vexsoft.core.paper.packets.display.DisplayBrightness;
import java.util.Objects;
import java.util.Optional;

/** Immutable viewer-specific hologram placement and text-display settings for a mob definition. */
public final class MobHologramDefinition {

  private final float offsetY;
  private final boolean automaticOffset;
  private final MobHologramRenderer renderer;
  private final DisplayBillboard billboard;
  private final float scale;
  private final int backgroundColor;
  private final boolean defaultBackground;
  private final boolean shadowed;
  private final boolean seeThrough;
  private final DisplayBrightness brightness;
  private final int lineWidth;

  private MobHologramDefinition(final Builder builder) {
    offsetY = builder.offsetY;
    automaticOffset = builder.automaticOffset;
    renderer = builder.renderer;
    billboard = builder.billboard;
    scale = builder.scale;
    backgroundColor = builder.backgroundColor;
    defaultBackground = builder.defaultBackground;
    shadowed = builder.shadowed;
    seeThrough = builder.seeThrough;
    brightness = builder.brightness;
    lineWidth = builder.lineWidth;
  }

  /** Starts building a styled mob hologram. */
  public static Builder builder(
      final float offsetY,
      final boolean automaticOffset,
      final MobHologramRenderer renderer
  ) {
    return new Builder(offsetY, automaticOffset, renderer);
  }

  /** Creates an automatically height-adjusted hologram with default display settings. */
  public static MobHologramDefinition automatic(
      final float padding,
      final MobHologramRenderer renderer
  ) {
    return builder(padding, true, renderer).build();
  }

  /** Creates a fixed-offset hologram with default display settings. */
  public static MobHologramDefinition fixed(
      final float offsetY,
      final MobHologramRenderer renderer
  ) {
    return builder(offsetY, false, renderer).build();
  }

  /** Returns the fixed offset or automatic padding above the mob. */
  public float offsetY() {
    return offsetY;
  }

  /** Returns whether the mob height is added to the configured offset. */
  public boolean automaticOffset() {
    return automaticOffset;
  }

  /** Returns the viewer-specific text renderer. */
  public MobHologramRenderer renderer() {
    return renderer;
  }

  /** Returns the text display billboard mode. */
  public DisplayBillboard billboard() {
    return billboard;
  }

  /** Returns the uniform text display scale. */
  public float scale() {
    return scale;
  }

  /** Returns the text display ARGB background color. */
  public int backgroundColor() {
    return backgroundColor;
  }

  /** Returns whether the client default text background is enabled. */
  public boolean defaultBackground() {
    return defaultBackground;
  }

  /** Returns whether text shadow is enabled. */
  public boolean shadowed() {
    return shadowed;
  }

  /** Returns whether the text remains visible through blocks. */
  public boolean seeThrough() {
    return seeThrough;
  }

  /** Returns the optional fixed block and sky brightness. */
  public Optional<DisplayBrightness> brightness() {
    return Optional.ofNullable(brightness);
  }

  /** Returns the wrapping width in client text-display units. */
  public int lineWidth() {
    return lineWidth;
  }

  /** Builder for styled viewer-specific mob holograms. */
  public static final class Builder {

    private final float offsetY;
    private final boolean automaticOffset;
    private final MobHologramRenderer renderer;
    private DisplayBillboard billboard = DisplayBillboard.CENTER;
    private float scale = 1.0F;
    private int backgroundColor;
    private boolean defaultBackground;
    private boolean shadowed;
    private boolean seeThrough;
    private DisplayBrightness brightness;
    private int lineWidth = 200;

    private Builder(
        final float offsetY,
        final boolean automaticOffset,
        final MobHologramRenderer renderer
    ) {
      if (!Float.isFinite(offsetY)) {
        throw new IllegalArgumentException("offsetY must be finite");
      }
      this.offsetY = offsetY;
      this.automaticOffset = automaticOffset;
      this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    /** Sets the text display billboard mode. */
    public Builder billboard(final DisplayBillboard value) {
      billboard = Objects.requireNonNull(value, "value");
      return this;
    }

    /** Sets the uniform text display scale. */
    public Builder scale(final float value) {
      if (!Float.isFinite(value) || value <= 0.0F) {
        throw new IllegalArgumentException("scale must be finite and positive");
      }
      scale = value;
      return this;
    }

    /** Sets the text display ARGB background color. */
    public Builder backgroundColor(final int value) {
      backgroundColor = value;
      return this;
    }

    /** Sets whether the client default text background is enabled. */
    public Builder defaultBackground(final boolean value) {
      defaultBackground = value;
      return this;
    }

    /** Sets whether text shadow is enabled. */
    public Builder shadowed(final boolean value) {
      shadowed = value;
      return this;
    }

    /** Sets whether the text remains visible through blocks. */
    public Builder seeThrough(final boolean value) {
      seeThrough = value;
      return this;
    }

    /** Sets fixed block and sky brightness. */
    public Builder brightness(final DisplayBrightness value) {
      brightness = Objects.requireNonNull(value, "value");
      return this;
    }

    /** Sets the positive wrapping width in client text-display units. */
    public Builder lineWidth(final int value) {
      if (value < 1) {
        throw new IllegalArgumentException("lineWidth must be positive");
      }
      lineWidth = value;
      return this;
    }

    /** Creates the immutable hologram definition. */
    public MobHologramDefinition build() {
      return new MobHologramDefinition(this);
    }
  }
}
