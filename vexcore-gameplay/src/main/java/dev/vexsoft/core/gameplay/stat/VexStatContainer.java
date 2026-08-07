package dev.vexsoft.core.gameplay.stat;

import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.api.player.DataContainerKey;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/** Array-backed stat container used by one loaded player. */
public final class VexStatContainer implements StatContainer {

  private final VexPlayer player;
  private final StatRegistryCoordinatorService registry;
  private volatile RegisteredStat[] activeStats = new RegisteredStat[0];
  private volatile VexPlayerStat[] views = new VexPlayerStat[0];
  private double[] permanentValues = new double[0];
  private double[] flatModifiers = new double[0];
  private double[] additiveMultipliers = new double[0];
  private double[] totalMultipliers = new double[0];
  private volatile double[] calculatedValues = new double[0];
  private boolean[] changed = new boolean[0];
  private int batchDepth;
  private boolean closed;

  /** Creates and attaches a stat container to the dynamic registry. */
  public VexStatContainer(
      final VexPlayer player,
      final StatRegistryCoordinatorService registry
  ) {
    this.player = Objects.requireNonNull(player, "player");
    this.registry = Objects.requireNonNull(registry, "registry");
    registry.attach(this);
  }

  @Override
  public PlayerStat getStat(final Stat stat) {
    return findStat(stat).orElseThrow(
        () -> new IllegalStateException("Stat registration is not active: " + stat.getKey())
    );
  }

  @Override
  public Optional<PlayerStat> findStat(final Stat stat) {
    if (!(Objects.requireNonNull(stat, "stat") instanceof RegisteredStat registered)) {
      return Optional.empty();
    }
    int slot = registered.getRuntimeId();
    RegisteredStat[] currentStats = activeStats;
    VexPlayerStat[] currentViews = views;
    return registered.isRegistered()
        && slot >= 0
        && slot < currentStats.length
        && currentStats[slot] == registered
        && currentViews[slot] != null
        ? Optional.of(currentViews[slot])
        : Optional.empty();
  }

  @Override
  public synchronized StatUpdateBatch beginUpdate() {
    requireOpen();
    batchDepth++;
    return new Batch(this);
  }

  @Override
  public synchronized void close() {
    if (closed) {
      return;
    }
    closed = true;
    activeStats = new RegisteredStat[0];
    views = new VexPlayerStat[0];
    permanentValues = new double[0];
    flatModifiers = new double[0];
    additiveMultipliers = new double[0];
    totalMultipliers = new double[0];
    calculatedValues = new double[0];
    changed = new boolean[0];
    registry.detach(this);
  }

  @Override
  public synchronized void onDataReset(final DataContainerKey<?> key) {
    if (key != GameplayPlayerData.STATS || closed) {
      return;
    }
    RegisteredStat[] current = activeStats;
    for (int slot = 0; slot < current.length; slot++) {
      if (current[slot] == null) {
        continue;
      }
      permanentValues[slot] = 0D;
      markChanged(current[slot]);
    }
  }

  synchronized void activate(final RegisteredStat stat) {
    requireOpen();
    int slot = stat.getRuntimeId();
    ensureCapacity(slot + 1);
    RegisteredStat[] updatedStats = activeStats.clone();
    VexPlayerStat[] updatedViews = views.clone();
    permanentValues[slot] = readPermanent(stat.getKey());
    flatModifiers[slot] = 0D;
    additiveMultipliers[slot] = 0D;
    totalMultipliers[slot] = 1D;
    updatedViews[slot] = new VexPlayerStat(this, stat);
    recalculateNow(stat);
    updatedStats[slot] = stat;
    views = updatedViews;
    activeStats = updatedStats;
  }

  synchronized void deactivate(final RegisteredStat stat) {
    int slot = stat.getRuntimeId();
    if (!isCurrent(stat, slot)) {
      return;
    }
    RegisteredStat[] updatedStats = activeStats.clone();
    VexPlayerStat[] updatedViews = views.clone();
    updatedStats[slot] = null;
    updatedViews[slot] = null;
    activeStats = updatedStats;
    views = updatedViews;
    permanentValues[slot] = 0D;
    flatModifiers[slot] = 0D;
    additiveMultipliers[slot] = 0D;
    totalMultipliers[slot] = 1D;
    calculatedValues[slot] = 0D;
    changed[slot] = false;
  }

  synchronized void definitionChanged(final RegisteredStat stat) {
    if (isCurrent(stat, stat.getRuntimeId())) {
      markChanged(stat);
    }
  }

  double getCalculated(final RegisteredStat stat) {
    int slot = stat.getRuntimeId();
    RegisteredStat[] currentStats = activeStats;
    double[] currentValues = calculatedValues;
    if (!stat.isRegistered() || slot < 0 || slot >= currentStats.length
        || currentStats[slot] != stat || slot >= currentValues.length) {
      throw new IllegalStateException("Stat registration is no longer active: " + stat.getKey());
    }
    return currentValues[slot];
  }

  synchronized double getPermanent(final RegisteredStat stat) {
    requireActive(stat);
    return permanentValues[stat.getRuntimeId()];
  }

  synchronized void setPermanent(final RegisteredStat stat, final double value) {
    requireFinite(value, "value");
    requireActive(stat);
    int slot = stat.getRuntimeId();
    permanentValues[slot] = value;
    writePermanent(stat.getKey(), value);
    markChanged(stat);
  }

  synchronized void addPermanent(final RegisteredStat stat, final double amount) {
    requireFinite(amount, "amount");
    requireActive(stat);
    int slot = stat.getRuntimeId();
    double updated = permanentValues[slot] + amount;
    requireFinite(updated, "result");
    permanentValues[slot] = updated;
    writePermanent(stat.getKey(), updated);
    markChanged(stat);
  }

  synchronized StatModifierHandle addModifier(
      final RegisteredStat stat,
      final StatModifier modifier
  ) {
    requireActive(stat);
    StatModifier checkedModifier = Objects.requireNonNull(modifier, "modifier");
    int slot = stat.getRuntimeId();
    apply(slot, checkedModifier, false);
    markChanged(stat);
    return new ModifierHandle(this, stat, checkedModifier);
  }

  synchronized boolean isActive(final RegisteredStat stat) {
    return !closed && isCurrent(stat, stat.getRuntimeId());
  }

  private synchronized void removeModifier(
      final RegisteredStat stat,
      final StatModifier modifier
  ) {
    if (!isCurrent(stat, stat.getRuntimeId())) {
      return;
    }
    apply(stat.getRuntimeId(), modifier, true);
    markChanged(stat);
  }

  private void apply(final int slot, final StatModifier modifier, final boolean remove) {
    double direction = remove ? -1D : 1D;
    switch (modifier.operation()) {
      case FLAT -> flatModifiers[slot] += direction * modifier.amount();
      case ADDITIVE_MULTIPLIER -> additiveMultipliers[slot] += direction * modifier.amount();
      case TOTAL_MULTIPLIER -> totalMultipliers[slot] = remove
          ? totalMultipliers[slot] / modifier.amount()
          : totalMultipliers[slot] * modifier.amount();
      default -> throw new IllegalStateException("Unsupported stat operation: " + modifier.operation());
    }
  }

  private void markChanged(final RegisteredStat stat) {
    int slot = stat.getRuntimeId();
    if (batchDepth > 0) {
      changed[slot] = true;
    } else {
      recalculateNow(stat);
    }
  }

  private void recalculateNow(final RegisteredStat stat) {
    int slot = stat.getRuntimeId();
    StatDefinition definition = stat.getDefinition();
    double result = (definition.getDefaultValue()
        + permanentValues[slot]
        + flatModifiers[slot])
        * (1D + additiveMultipliers[slot])
        * totalMultipliers[slot];
    calculatedValues[slot] = Math.clamp(
        result,
        definition.getMinimum(),
        definition.getMaximum()
    );
    changed[slot] = false;
  }

  private synchronized void finishBatch() {
    if (batchDepth <= 0) {
      return;
    }
    batchDepth--;
    if (batchDepth > 0) {
      return;
    }
    RegisteredStat[] current = activeStats;
    for (int slot = 0; slot < changed.length; slot++) {
      if (changed[slot] && slot < current.length && current[slot] != null) {
        recalculateNow(current[slot]);
      }
    }
  }

  private double readPermanent(final StatKey key) {
    return player.read(
        GameplayPlayerData.STATS,
        data -> data.getPermanentValues().getOrDefault(key.toString(), 0D)
    );
  }

  private void writePermanent(final StatKey key, final double value) {
    player.update(GameplayPlayerData.STATS, data -> {
      Map<String, Double> values = data.getPermanentValues();
      if (value == 0D) {
        values.remove(key.toString());
      } else {
        values.put(key.toString(), value);
      }
    });
  }

  private void ensureCapacity(final int required) {
    if (activeStats.length >= required) {
      return;
    }
    int size = Math.max(required, Math.max(4, activeStats.length * 2));
    activeStats = Arrays.copyOf(activeStats, size);
    views = Arrays.copyOf(views, size);
    permanentValues = Arrays.copyOf(permanentValues, size);
    flatModifiers = Arrays.copyOf(flatModifiers, size);
    additiveMultipliers = Arrays.copyOf(additiveMultipliers, size);
    int oldLength = totalMultipliers.length;
    totalMultipliers = Arrays.copyOf(totalMultipliers, size);
    Arrays.fill(totalMultipliers, oldLength, size, 1D);
    calculatedValues = Arrays.copyOf(calculatedValues, size);
    changed = Arrays.copyOf(changed, size);
  }

  private void requireActive(final RegisteredStat stat) {
    requireOpen();
    if (!isCurrent(stat, stat.getRuntimeId())) {
      throw new IllegalStateException("Stat registration is no longer active: " + stat.getKey());
    }
  }

  private boolean isCurrent(final RegisteredStat stat, final int slot) {
    RegisteredStat[] current = activeStats;
    return stat.isRegistered() && slot >= 0 && slot < current.length && current[slot] == stat;
  }

  private void requireOpen() {
    if (closed) {
      throw new IllegalStateException("Stat container is closed");
    }
  }

  private static void requireFinite(final double value, final String name) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(name + " must be finite");
    }
  }

  private static final class VexPlayerStat implements PlayerStat {

    private final VexStatContainer container;
    private final RegisteredStat stat;

    private VexPlayerStat(final VexStatContainer container, final RegisteredStat stat) {
      this.container = container;
      this.stat = stat;
    }

    @Override
    public Stat getStat() {
      return stat;
    }

    @Override
    public double getValue() {
      return container.getCalculated(stat);
    }

    @Override
    public double getPermanent() {
      return container.getPermanent(stat);
    }

    @Override
    public void setPermanent(final double value) {
      container.setPermanent(stat, value);
    }

    @Override
    public void addPermanent(final double amount) {
      container.addPermanent(stat, amount);
    }

    @Override
    public StatModifierHandle addModifier(final StatModifier modifier) {
      return container.addModifier(stat, modifier);
    }
  }

  private static final class ModifierHandle implements StatModifierHandle {

    private final VexStatContainer container;
    private final RegisteredStat stat;
    private final StatModifier modifier;
    private final AtomicBoolean removed = new AtomicBoolean();

    private ModifierHandle(
        final VexStatContainer container,
        final RegisteredStat stat,
        final StatModifier modifier
    ) {
      this.container = container;
      this.stat = stat;
      this.modifier = modifier;
    }

    @Override
    public boolean isActive() {
      return !removed.get() && container.isActive(stat);
    }

    @Override
    public void remove() {
      if (removed.compareAndSet(false, true)) {
        container.removeModifier(stat, modifier);
      }
    }
  }

  private static final class Batch implements StatUpdateBatch {

    private final VexStatContainer container;
    private final AtomicBoolean closed = new AtomicBoolean();

    private Batch(final VexStatContainer container) {
      this.container = container;
    }

    @Override
    public void close() {
      if (closed.compareAndSet(false, true)) {
        container.finishBatch();
      }
    }
  }
}
