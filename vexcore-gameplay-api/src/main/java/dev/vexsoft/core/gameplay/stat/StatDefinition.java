package dev.vexsoft.core.gameplay.stat;

import java.util.Objects;
import lombok.Getter;

/** Immutable numeric rules used by one registered stat. */
@Getter
public final class StatDefinition {

  /** Stable stat identity. */
  private final StatKey key;
  /** Base value used before player-specific values and modifiers. */
  private final double defaultValue;
  /** Inclusive lower bound of the calculated value. */
  private final double minimum;
  /** Inclusive upper bound of the calculated value. */
  private final double maximum;

  private StatDefinition(final Builder builder) {
    key = builder.key;
    defaultValue = requireFinite(builder.defaultValue, "defaultValue");
    minimum = requireBound(builder.minimum, "minimum");
    maximum = requireBound(builder.maximum, "maximum");
    if (minimum > maximum) {
      throw new IllegalArgumentException("Stat minimum must not exceed its maximum");
    }
    if (defaultValue < minimum || defaultValue > maximum) {
      throw new IllegalArgumentException("Stat default value must be within its bounds");
    }
  }

  /** Starts building a definition for the supplied stable key. */
  public static Builder builder(final StatKey key) {
    return new Builder(key);
  }

  private static double requireFinite(final double value, final String name) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(name + " must be finite");
    }
    return value;
  }

  private static double requireBound(final double value, final String name) {
    if (Double.isNaN(value)) {
      throw new IllegalArgumentException(name + " must not be NaN");
    }
    return value;
  }

  /** Mutable builder for a stat definition. */
  public static final class Builder {

    private final StatKey key;
    private double defaultValue;
    private double minimum = Double.NEGATIVE_INFINITY;
    private double maximum = Double.POSITIVE_INFINITY;

    private Builder(final StatKey key) {
      this.key = Objects.requireNonNull(key, "key");
    }

    /** Sets the base value. */
    public Builder defaultValue(final double value) {
      defaultValue = value;
      return this;
    }

    /** Sets the inclusive lower bound. */
    public Builder minimum(final double value) {
      minimum = value;
      return this;
    }

    /** Sets the inclusive upper bound. */
    public Builder maximum(final double value) {
      maximum = value;
      return this;
    }

    /** Creates the validated immutable definition. */
    public StatDefinition build() {
      return new StatDefinition(this);
    }
  }
}
