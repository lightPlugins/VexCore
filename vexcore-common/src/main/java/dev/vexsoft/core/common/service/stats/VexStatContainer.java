package dev.vexsoft.core.common.service.stats;

import dev.vexsoft.core.api.player.DataContainerKey;
import dev.vexsoft.core.api.player.profile.PlayerProfileContext;
import dev.vexsoft.core.api.player.VexPlayer;
import dev.vexsoft.core.stats.PlayerStat;
import dev.vexsoft.core.stats.Stat;
import dev.vexsoft.core.stats.StatContainer;
import dev.vexsoft.core.stats.StatDefinition;
import dev.vexsoft.core.stats.StatModifier;
import dev.vexsoft.core.stats.StatModifierHandle;
import dev.vexsoft.core.stats.StatUpdateBatch;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/** Array-backed stats guarded by the owning player lock, including profile installation and cached handles. */
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
    private long[] profileGenerations = new long[0];
    private int batchDepth;
    private boolean closed;

    /** Creates and attaches a stat container to the dynamic registry. */
    public VexStatContainer(final VexPlayer player, final StatRegistryCoordinatorService registry) {
        this.player = Objects.requireNonNull(player, "player");
        this.registry = Objects.requireNonNull(registry, "registry");
        registry.attach(this);
    }

    @Override
    public PlayerStat getStat(final Stat stat) {
        return findStat(stat).orElseThrow(() -> new IllegalStateException(
            "Stat registration is not active: " + stat.getKey()));
    }

    @Override
    public Optional<PlayerStat> findStat(final Stat stat) {
        if (!(Objects.requireNonNull(stat, "stat") instanceof RegisteredStat registered)) {
            return Optional.empty();
        }

        int slot = registered.getRuntimeId();
        RegisteredStat[] currentStats = activeStats;
        VexPlayerStat[] currentViews = views;

        return registered.isRegistered() && slot >= 0 && slot < currentStats.length && currentStats[slot] == registered
            && currentViews[slot] != null ? Optional.of(currentViews[slot]) : Optional.empty();
    }

    @Override
    public StatUpdateBatch beginUpdate() {
        synchronized (player) {
            requireOpen();
            batchDepth++;

            return new Batch(this);
        }
    }

    @Override
    public void close() {
        registry.detach(this);

        synchronized (player) {
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
        }
    }

    @Override
    public void onDataReset(final DataContainerKey<?> key) {
        synchronized (player) {
            if (closed) {
                return;
            }

            RegisteredStat[] current = activeStats;

            for (int slot = 0; slot < current.length; slot++) {
                if (current[slot] == null || GameplayPlayerData.key(current[slot].getDefinition()) != key) {
                    continue;
                }

                permanentValues[slot] = 0D;
                markChanged(current[slot]);
            }
        }
    }

    @Override
    public void onProfileChanged(final PlayerProfileContext previous, final PlayerProfileContext current) {
        synchronized (player) {
            if (closed) {
                return;
            }
            for (RegisteredStat stat : activeStats.clone()) {
                if (stat != null && stat.getDefinition().getDataScope().domain().filter(current.domain()::equals).isPresent()) {
                    activate(stat);
                }
            }
        }
    }

    void activate(final RegisteredStat stat) {
        synchronized (player) {
            requireOpen();
            int slot = stat.getRuntimeId();

            ensureCapacity(slot + 1);
            RegisteredStat[] updatedStats = activeStats.clone();
            VexPlayerStat[] updatedViews = views.clone();

            permanentValues[slot] = readPermanent(stat);
            flatModifiers[slot] = 0D;
            additiveMultipliers[slot] = 0D;
            totalMultipliers[slot] = 1D;
            updatedViews[slot] = new VexPlayerStat(this, stat, ++profileGenerations[slot]);
            recalculateNow(stat);
            updatedStats[slot] = stat;
            views = updatedViews;
            activeStats = updatedStats;
        }
    }

    void deactivate(final RegisteredStat stat) {
        synchronized (player) {
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
    }

    void definitionChanged(final RegisteredStat stat) {
        synchronized (player) {
            if (isCurrent(stat, stat.getRuntimeId())) {
                markChanged(stat);
            }
        }
    }

    double getCalculated(final RegisteredStat stat) {
        int slot = stat.getRuntimeId();
        RegisteredStat[] currentStats = activeStats;
        double[] currentValues = calculatedValues;

        if (!stat.isRegistered() || slot < 0 || slot >= currentStats.length || currentStats[slot] != stat
            || slot >= currentValues.length) {
            throw new IllegalStateException("Stat registration is no longer active: " + stat.getKey());
        }

        return currentValues[slot];
    }

    double getPermanent(final RegisteredStat stat) {
        synchronized (player) {
            requireActive(stat);

            return permanentValues[stat.getRuntimeId()];
        }
    }

    void setPermanent(final RegisteredStat stat, final double value) {
        synchronized (player) {
            requireFinite(value, "value");
            requireActive(stat);
            int slot = stat.getRuntimeId();

            permanentValues[slot] = value;
            writePermanent(stat, value);
            markChanged(stat);
        }
    }

    void addPermanent(final RegisteredStat stat, final double amount) {
        synchronized (player) {
            requireFinite(amount, "amount");
            requireActive(stat);
            int slot = stat.getRuntimeId();
            double updated = permanentValues[slot] + amount;

            requireFinite(updated, "result");
            permanentValues[slot] = updated;
            writePermanent(stat, updated);
            markChanged(stat);
        }
    }

    StatModifierHandle addModifier(final RegisteredStat stat, final StatModifier modifier) {
        synchronized (player) {
            requireActive(stat);
            StatModifier checkedModifier = Objects.requireNonNull(modifier, "modifier");
            int slot = stat.getRuntimeId();

            apply(slot, checkedModifier, false);
            markChanged(stat);

            return new ModifierHandle(this, stat, checkedModifier, profileGenerations[slot]);
        }
    }

    boolean isActive(final RegisteredStat stat) {
        synchronized (player) {
            return !closed && isCurrent(stat, stat.getRuntimeId());
        }
    }

    private void removeModifier(final RegisteredStat stat, final StatModifier modifier, final long generation) {
        synchronized (player) {
            if (!isCurrent(stat, stat.getRuntimeId()) || profileGenerations[stat.getRuntimeId()] != generation) {
                return;
            }

            apply(stat.getRuntimeId(), modifier, true);
            markChanged(stat);
        }
    }

    private void apply(final int slot, final StatModifier modifier, final boolean remove) {
        double direction = remove ? -1D : 1D;

        switch (modifier.operation()) {
            case FLAT -> flatModifiers[slot] += direction * modifier.amount();
            case ADDITIVE_MULTIPLIER -> additiveMultipliers[slot] += direction * modifier.amount();
            case TOTAL_MULTIPLIER -> totalMultipliers[slot] =
                remove ? totalMultipliers[slot] / modifier.amount() : totalMultipliers[slot] * modifier.amount();
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
        double result = (definition.getDefaultValue() + permanentValues[slot] + flatModifiers[slot]) * (1D
            + additiveMultipliers[slot]) * totalMultipliers[slot];

        calculatedValues[slot] = Math.clamp(result, definition.getMinimum(), definition.getMaximum());
        changed[slot] = false;
    }

    private void finishBatch() {
        synchronized (player) {
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
    }

    private double readPermanent(final RegisteredStat stat) {
        DataContainerKey<StatData> key = GameplayPlayerData.key(stat.getDefinition());
        if (!player.has(key) || key.getScope().domain().filter(domain -> player.findProfileContext(domain).isEmpty())
            .isPresent()) {
            return 0;
        }
        return player.read(
            key,
            data -> data.getPermanentValues().getOrDefault(stat.getKey().toString(), 0D)
        );
    }

    private void writePermanent(final RegisteredStat stat, final double value) {
        player.update(
            GameplayPlayerData.key(stat.getDefinition()),
            data -> {
                Map<String, Double> values = data.getPermanentValues();

                if (value == 0D) {
                    values.remove(stat.getKey().toString());
                } else {
                    values.put(stat.getKey().toString(), value);
                }
            }
        );
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
        profileGenerations = Arrays.copyOf(profileGenerations, size);
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

    private void requireGeneration(final RegisteredStat stat, final long generation) {
        synchronized (player) {
            requireActive(stat);
            if (profileGenerations[stat.getRuntimeId()] != generation) {
                throw new IllegalStateException("Stat view belongs to a retired profile generation");
            }
            player.addressOf(GameplayPlayerData.key(stat.getDefinition()));
        }
    }

    private <T> T inGeneration(final RegisteredStat stat, final long generation, final Supplier<T> operation) {
        synchronized (player) {
            requireGeneration(stat, generation);
            return operation.get();
        }
    }

    private boolean isActive(final RegisteredStat stat, final long generation) {
        synchronized (player) {
            return isActive(stat) && profileGenerations[stat.getRuntimeId()] == generation;
        }
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

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class VexPlayerStat implements PlayerStat {

        private final VexStatContainer container;
        private final RegisteredStat stat;
        private final long generation;

        @Override
        public Stat getStat() {
            return stat;
        }

        @Override
        public double getValue() {
            return container.inGeneration(stat, generation, () -> container.getCalculated(stat));
        }

        @Override
        public double getPermanent() {
            return container.inGeneration(stat, generation, () -> container.getPermanent(stat));
        }

        @Override
        public void setPermanent(final double value) {
            container.inGeneration(stat, generation, () -> {
                container.setPermanent(stat, value);
                return null;
            });
        }

        @Override
        public void addPermanent(final double amount) {
            container.inGeneration(stat, generation, () -> {
                container.addPermanent(stat, amount);
                return null;
            });
        }

        @Override
        public StatModifierHandle addModifier(final StatModifier modifier) {
            return container.inGeneration(stat, generation, () -> container.addModifier(stat, modifier));
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class ModifierHandle implements StatModifierHandle {

        private final VexStatContainer container;
        private final RegisteredStat stat;
        private final StatModifier modifier;
        private final long generation;
        private final AtomicBoolean removed = new AtomicBoolean();

        @Override
        public boolean isActive() {
            return !removed.get() && container.isActive(stat, generation);
        }

        @Override
        public void remove() {
            if (removed.compareAndSet(false, true)) {
                container.removeModifier(stat, modifier, generation);
            }
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class Batch implements StatUpdateBatch {

        private final VexStatContainer container;
        private final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                container.finishBatch();
            }
        }
    }
}
