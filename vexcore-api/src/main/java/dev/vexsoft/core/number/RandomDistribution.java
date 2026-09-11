package dev.vexsoft.core.number;

import java.util.List;
import java.util.function.ToDoubleFunction;
import java.util.random.RandomGenerator;

/** Shared bounded random sampling for gameplay catalogs and numerical rolls. */
public final class RandomDistribution {

    private RandomDistribution() {
    }

    /** Picks an entry proportionally to its non-negative finite weight. */
    public static <T> T weighted(
        final List<T> entries,
        final ToDoubleFunction<T> weight,
        final RandomGenerator random
    ) {
        double totalWeight = 0.0D;

        for (T entry : entries) {
            double entryWeight = weight.applyAsDouble(entry);

            if (!Double.isFinite(entryWeight) || entryWeight < 0.0D) {
                throw new IllegalArgumentException("Invalid selection weight");
            }

            totalWeight += entryWeight;
        }

        if (!Double.isFinite(totalWeight) || totalWeight <= 0.0D) {
            throw new IllegalArgumentException("Selection needs positive total weight");
        }

        double remainingRoll = random.nextDouble(totalWeight);
        T lastSelectedEntry = null;

        for (T entry : entries) {
            double entryWeight = weight.applyAsDouble(entry);

            if (entryWeight > 0.0D) {
                lastSelectedEntry = entry;
                remainingRoll -= entryWeight;

                if (remainingRoll < 0.0D) {
                    return entry;
                }
            }
        }

        return lastSelectedEntry;
    }

    /** Samples a bounded range, biasing toward the maximum with increasing non-negative bonus. */
    public static double biased(
        final double minimum,
        final double maximum,
        final double bonus,
        final double uniformSample
    ) {
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || maximum < minimum || !Double.isFinite(
            maximum - minimum) || !Double.isFinite(bonus) || bonus < 0.0D || !Double.isFinite(uniformSample)
            || uniformSample < 0.0D || uniformSample > 1.0D) {
            throw new IllegalArgumentException("Invalid bounded distribution");
        }

        return minimum + (maximum - minimum) * (1.0D - Math.pow(1.0D - uniformSample, 1.0D + bonus / 100.0D));
    }

    /** Rolls a percentage, with exact zero and hundred boundaries. */
    public static boolean chance(final double percent, final RandomGenerator random) {
        if (!Double.isFinite(percent)) {
            throw new IllegalArgumentException("Chance must be finite");
        }

        return percent > 0.0D && (percent >= 100.0D || random.nextDouble(100.0D) < percent);
    }
}
