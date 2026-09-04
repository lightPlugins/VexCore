package dev.vexsoft.core.paper.mob.spawner;

import dev.vexsoft.core.paper.mob.MobKey;
import java.util.Objects;
import org.bukkit.Location;

/** Immutable population, activation, and placement settings for one mob spawner point. */
public final class MobSpawnerDefinition {

  private final MobSpawnerKey key;
  private final MobKey mobKey;
  private final Location anchor;
  private final MobSpawnerScope scope;
  private final double activationRadius;
  private final double deactivationRadius;
  private final int maximumAlive;
  private final int spawnBatchSize;
  private final long spawnIntervalTicks;
  private final boolean exactSpawnPosition;
  private final MobSpawnPositionRules positionRules;

  private MobSpawnerDefinition(final Builder builder) {
    key = builder.key;
    mobKey = builder.mobKey;
    anchor = builder.anchor.clone();
    scope = builder.scope;
    activationRadius = positive(builder.activationRadius, "activationRadius");
    deactivationRadius = positive(builder.deactivationRadius, "deactivationRadius");
    if (deactivationRadius < activationRadius) {
      throw new IllegalArgumentException("deactivationRadius must not be smaller than activationRadius");
    }
    if (builder.maximumAlive < 1 || builder.spawnBatchSize < 1
        || builder.spawnBatchSize > builder.maximumAlive || builder.spawnIntervalTicks < 1L) {
      throw new IllegalArgumentException("Invalid population size, batch size, or spawn interval");
    }
    maximumAlive = builder.maximumAlive;
    spawnBatchSize = builder.spawnBatchSize;
    spawnIntervalTicks = builder.spawnIntervalTicks;
    exactSpawnPosition = builder.exactSpawnPosition;
    positionRules = builder.positionRules;
  }

  /** Starts building a spawner at a loaded-world anchor. */
  public static Builder builder(
      final MobSpawnerKey key,
      final MobKey mobKey,
      final Location anchor
  ) {
    return new Builder(key, mobKey, anchor);
  }

  /** Returns the stable spawner key. */
  public MobSpawnerKey key() {
    return key;
  }

  /** Returns the mob definition spawned by this point. */
  public MobKey mobKey() {
    return mobKey;
  }

  /** Returns a defensive copy of the spawner anchor. */
  public Location anchor() {
    return anchor.clone();
  }

  /** Returns whether the population is global or isolated per player. */
  public MobSpawnerScope scope() {
    return scope;
  }

  /** Returns the radius at which an inactive population activates. */
  public double activationRadius() {
    return activationRadius;
  }

  /** Returns the radius outside which an active population deactivates. */
  public double deactivationRadius() {
    return deactivationRadius;
  }

  /** Returns the maximum living mobs in one population. */
  public int maximumAlive() {
    return maximumAlive;
  }

  /** Returns the maximum mobs created by one due spawn pulse. */
  public int spawnBatchSize() {
    return spawnBatchSize;
  }

  /** Returns the delay between population spawn pulses. */
  public long spawnIntervalTicks() {
    return spawnIntervalTicks;
  }

  /** Returns whether mobs spawn at the exact anchor without ground-position selection. */
  public boolean exactSpawnPosition() {
    return exactSpawnPosition;
  }

  /** Returns the cave-safe position-selection rules. */
  public MobSpawnPositionRules positionRules() {
    return positionRules;
  }

  @Override
  public boolean equals(final Object other) {
    if (!(other instanceof MobSpawnerDefinition definition)) {
      return false;
    }
    return key.equals(definition.key)
        && mobKey.equals(definition.mobKey)
        && anchor.equals(definition.anchor)
        && scope == definition.scope
        && Double.compare(activationRadius, definition.activationRadius) == 0
        && Double.compare(deactivationRadius, definition.deactivationRadius) == 0
        && maximumAlive == definition.maximumAlive
        && spawnBatchSize == definition.spawnBatchSize
        && spawnIntervalTicks == definition.spawnIntervalTicks
        && exactSpawnPosition == definition.exactSpawnPosition
        && positionRules.equals(definition.positionRules);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        key, mobKey, anchor, scope, activationRadius, deactivationRadius,
        maximumAlive, spawnBatchSize, spawnIntervalTicks, exactSpawnPosition, positionRules
    );
  }

  private static double positive(final double value, final String name) {
    if (!Double.isFinite(value) || value <= 0.0D) {
      throw new IllegalArgumentException(name + " must be finite and greater than zero");
    }
    return value;
  }

  /** Builder for immutable mob spawner definitions. */
  public static final class Builder {

    private final MobSpawnerKey key;
    private final MobKey mobKey;
    private final Location anchor;
    private MobSpawnerScope scope = MobSpawnerScope.GLOBAL;
    private double activationRadius = 32.0D;
    private double deactivationRadius = 40.0D;
    private int maximumAlive = 4;
    private int spawnBatchSize = 1;
    private long spawnIntervalTicks = 200L;
    private boolean exactSpawnPosition;
    private MobSpawnPositionRules positionRules = MobSpawnPositionRules.defaults(6.0D);

    private Builder(
        final MobSpawnerKey key,
        final MobKey mobKey,
        final Location anchor
    ) {
      this.key = Objects.requireNonNull(key, "key");
      this.mobKey = Objects.requireNonNull(mobKey, "mobKey");
      this.anchor = Objects.requireNonNull(anchor, "anchor").clone();
      if (this.anchor.getWorld() == null) {
        throw new IllegalArgumentException("anchor must have a world");
      }
    }

    /** Sets whether the population is global or per player. */
    public Builder scope(final MobSpawnerScope value) {
      scope = Objects.requireNonNull(value, "value");
      return this;
    }

    /** Sets activation and larger deactivation radii. */
    public Builder activationRadii(final double activation, final double deactivation) {
      activationRadius = activation;
      deactivationRadius = deactivation;
      return this;
    }

    /** Sets maximum alive population and spawn batch size. */
    public Builder population(final int maximum, final int batchSize) {
      maximumAlive = maximum;
      spawnBatchSize = batchSize;
      return this;
    }

    /** Sets the interval between due spawn pulses in server ticks. */
    public Builder spawnIntervalTicks(final long value) {
      spawnIntervalTicks = value;
      return this;
    }

    /** Sets whether mobs spawn at the exact anchor without ground-position selection. */
    public Builder exactSpawnPosition(final boolean value) {
      exactSpawnPosition = value;
      return this;
    }

    /** Sets cave-safe spawn-position rules. */
    public Builder positionRules(final MobSpawnPositionRules value) {
      positionRules = Objects.requireNonNull(value, "value");
      return this;
    }

    /** Creates the validated immutable spawner definition. */
    public MobSpawnerDefinition build() {
      return new MobSpawnerDefinition(this);
    }
  }
}
