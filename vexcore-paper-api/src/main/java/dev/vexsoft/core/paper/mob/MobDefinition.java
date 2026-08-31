package dev.vexsoft.core.paper.mob;

import dev.vexsoft.core.paper.mob.goal.MobGoalDefinition;
import dev.vexsoft.core.paper.packets.display.DisplayGlowColor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.entity.EntityType;

/** Immutable defaults used to create non-persistent custom mob instances. */
public final class MobDefinition {

  private final MobKey key;
  private final EntityType entityType;
  private final double maxHealth;
  private final double scale;
  private final double movementSpeed;
  private final double rotationSpeed;
  private final double knockbackResistance;
  private final boolean baby;
  private final boolean silent;
  private final boolean gravity;
  private final boolean collidable;
  private final DisplayGlowColor glow;
  private final MobHologramDefinition hologram;
  private final List<MobGoalDefinition> goals;

  private MobDefinition(final Builder builder) {
    key = builder.key;
    entityType = Objects.requireNonNull(builder.entityType, "entityType");
    if (!entityType.isAlive() || !entityType.isSpawnable()) {
      throw new IllegalArgumentException("entityType must be a spawnable living entity");
    }
    maxHealth = positiveFinite(builder.maxHealth, "maxHealth");
    scale = positiveFinite(builder.scale, "scale");
    movementSpeed = nonNegativeFinite(builder.movementSpeed, "movementSpeed");
    rotationSpeed = nonNegativeFinite(builder.rotationSpeed, "rotationSpeed");
    knockbackResistance = builder.knockbackResistance;
    if (!Double.isFinite(knockbackResistance)
        || knockbackResistance < 0.0D || knockbackResistance > 1.0D) {
      throw new IllegalArgumentException("knockbackResistance must be between zero and one");
    }
    baby = builder.baby;
    silent = builder.silent;
    gravity = builder.gravity;
    collidable = builder.collidable;
    glow = builder.glow;
    hologram = builder.hologram;
    goals = List.copyOf(builder.goals);
  }

  /** Starts building a definition for the supplied key and vanilla entity type. */
  public static Builder builder(final MobKey key, final EntityType entityType) {
    return new Builder(key, entityType);
  }

  /** Returns the stable definition key. */
  public MobKey key() {
    return key;
  }

  /** Returns the vanilla carrier entity type. */
  public EntityType entityType() {
    return entityType;
  }

  /** Returns the custom maximum health. */
  public double maxHealth() {
    return maxHealth;
  }

  /** Returns the initial native scale. */
  public double scale() {
    return scale;
  }

  /** Returns the logical movement speed consumed by navigation goals. */
  public double movementSpeed() {
    return movementSpeed;
  }

  /** Returns the maximum rotation speed in degrees per second. */
  public double rotationSpeed() {
    return rotationSpeed;
  }

  /** Returns normalized custom knockback resistance. */
  public double knockbackResistance() {
    return knockbackResistance;
  }

  /** Returns whether ageable carriers should be babies. */
  public boolean baby() {
    return baby;
  }

  /** Returns whether vanilla sounds are suppressed. */
  public boolean silent() {
    return silent;
  }

  /** Returns whether vanilla gravity remains enabled. */
  public boolean gravity() {
    return gravity;
  }

  /** Returns whether physical entity collisions remain enabled. */
  public boolean collidable() {
    return collidable;
  }

  /** Returns the default viewer-specific glow color. */
  public Optional<DisplayGlowColor> glow() {
    return Optional.ofNullable(glow);
  }

  /** Returns optional viewer-specific hologram settings. */
  public Optional<MobHologramDefinition> hologram() {
    return Optional.ofNullable(hologram);
  }

  /** Returns every opt-in goal in registration order. */
  public List<MobGoalDefinition> goals() {
    return goals;
  }

  private static double positiveFinite(final double value, final String name) {
    if (!Double.isFinite(value) || value <= 0.0D) {
      throw new IllegalArgumentException(name + " must be finite and greater than zero");
    }
    return value;
  }

  private static double nonNegativeFinite(final double value, final String name) {
    if (!Double.isFinite(value) || value < 0.0D) {
      throw new IllegalArgumentException(name + " must be finite and non-negative");
    }
    return value;
  }

  /** Builder for immutable custom mob definitions. */
  public static final class Builder {

    private final MobKey key;
    private final EntityType entityType;
    private double maxHealth = 20.0D;
    private double scale = 1.0D;
    private double movementSpeed;
    private double rotationSpeed = 180.0D;
    private double knockbackResistance = 1.0D;
    private boolean baby;
    private boolean silent = true;
    private boolean gravity;
    private boolean collidable;
    private DisplayGlowColor glow;
    private MobHologramDefinition hologram;
    private final List<MobGoalDefinition> goals = new ArrayList<>();

    private Builder(final MobKey key, final EntityType entityType) {
      this.key = Objects.requireNonNull(key, "key");
      this.entityType = Objects.requireNonNull(entityType, "entityType");
    }

    /** Sets the custom maximum health. */
    public Builder maxHealth(final double value) {
      maxHealth = value;
      return this;
    }

    /** Sets the native entity scale. */
    public Builder scale(final double value) {
      scale = value;
      return this;
    }

    /** Sets the logical movement speed. */
    public Builder movementSpeed(final double value) {
      movementSpeed = value;
      return this;
    }

    /** Sets the maximum rotation speed in degrees per second. */
    public Builder rotationSpeed(final double value) {
      rotationSpeed = value;
      return this;
    }

    /** Sets normalized custom knockback resistance. */
    public Builder knockbackResistance(final double value) {
      knockbackResistance = value;
      return this;
    }

    /** Sets whether ageable carrier entities spawn as babies. */
    public Builder baby(final boolean value) {
      baby = value;
      return this;
    }

    /** Sets whether vanilla entity sounds are suppressed. */
    public Builder silent(final boolean value) {
      silent = value;
      return this;
    }

    /** Sets whether vanilla gravity is enabled. */
    public Builder gravity(final boolean value) {
      gravity = value;
      return this;
    }

    /** Sets whether physical collisions are enabled. */
    public Builder collidable(final boolean value) {
      collidable = value;
      return this;
    }

    /** Sets the default viewer-specific glow color. */
    public Builder glow(final DisplayGlowColor value) {
      glow = value;
      return this;
    }

    /** Sets viewer-specific passenger hologram settings. */
    public Builder hologram(final MobHologramDefinition value) {
      hologram = value;
      return this;
    }

    /** Adds one opt-in custom goal. */
    public Builder goal(final MobGoalDefinition value) {
      goals.add(Objects.requireNonNull(value, "value"));
      return this;
    }

    /** Creates the validated immutable definition. */
    public MobDefinition build() {
      return new MobDefinition(this);
    }
  }
}
