package dev.vexsoft.core.paper.service.upgrade;

import dev.vexsoft.core.paper.upgrade.UpgradeAnimationPacing;
import java.util.SplittableRandom;
import java.util.TreeSet;

/** Precomputed visual checkpoints and pacing; breakthrough checks are independent of the durable success roll. */
final class UpgradeAnimationTimeline {

    private final int[] ticks;
    private final int[] progress;
    private final int resolution;
    private final int duration;
    private final SplittableRandom breakthroughs;
    private int checkedMilestone = -1;
    private int breakthroughChecks;

    UpgradeAnimationTimeline(int duration, long seed) {
        this(new UpgradeAnimationPacing(0, 7, 9, duration, duration,
            duration >= 160 ? 60 : 0, duration >= 160 ? 80 : 0), seed);
    }

    UpgradeAnimationTimeline(UpgradeAnimationPacing pacing, long seed) {
        var random = new SplittableRandom(seed);
        duration = random.nextInt(pacing.minChargeTicks(), pacing.maxChargeTicks() + 1);
        int steps = random.nextInt(pacing.minSteps(), pacing.maxSteps() + 1);
        ticks = new int[steps];
        progress = new int[steps];
        var milestones = new TreeSet<Integer>();
        while (milestones.size() < steps - 1) {
            milestones.add(random.nextInt(5, 97));
        }
        int index = 0;
        for (var milestone : milestones) {
            progress[index++] = milestone;
        }
        progress[steps - 1] = 99;
        resolution = random.nextInt(steps);
        breakthroughs = random.split();
        int longPause = pacing.maxLongPauseTicks() > 0 ? random.nextInt(1, steps - 1) : -1;
        int longDuration = longPause >= 0
            ? random.nextInt(pacing.minLongPauseTicks(), pacing.maxLongPauseTicks() + 1) : 0;
        int minimum = 3;
        int flexible = duration - longDuration - minimum * (steps - (longPause >= 0 ? 1 : 0));
        double[] weights = new double[steps];
        double total = 0;
        for (index = 0; index < steps; index++) {
            weights[index] = index == longPause ? 0 : 0.15 + Math.pow(random.nextDouble(), 2) * 3;
            total += weights[index];
        }
        double allocated = 0;
        int previousAllocation = 0;
        int elapsed = 0;
        for (index = 0; index < steps; index++) {
            if (index == longPause) {
                elapsed += longDuration;
            } else {
                allocated += weights[index] / total * flexible;
                int allocation = (int) Math.round(allocated);
                elapsed += minimum + allocation - previousAllocation;
                previousAllocation = allocation;
            }
            ticks[index] = index == steps - 1 ? duration : elapsed;
        }
    }

    int duration() {
        return duration;
    }

    int steps() {
        return ticks.length;
    }

    int breakthroughChecks() {
        return breakthroughChecks;
    }

    boolean checkBreakthrough(int tick, double chance) {
        for (int index = 0; index < ticks.length; index++) {
            if (ticks[index] == tick && index > checkedMilestone) {
                checkedMilestone = index;
                breakthroughChecks++;
                return breakthroughs.nextDouble() < chance;
            }
        }
        return false;
    }

    int resolutionTick() {
        return ticks[resolution];
    }

    int resolutionProgress() {
        return progress[resolution];
    }

    Frame frame(int tick) {
        return frame(tick, -1);
    }

    Frame frame(int tick, int pulseAge) {
        return frame(tick, pulseAge, 12);
    }

    Frame frame(int tick, int pulseAge, int finalFillTicks) {
        int milestone = -1;
        for (int index = 0; index < ticks.length; index++) {
            if (tick >= ticks[index]) {
                milestone = index;
            }
        }
        if (milestone < 0) {
            return new Frame(0, 0, 0, false, 0);
        }
        int age = pulseAge < 0 ? tick - ticks[milestone] : pulseAge;
        int fadeTicks = milestone + 1 == ticks.length ? 18
            : Math.min(18, Math.max(2, ticks[milestone + 1] - ticks[milestone] - 1));
        double flash = Math.max(0, 1 - age / (double) fadeTicks);
        flash = flash * flash * (3 - 2 * flash);
        int retained = progress[milestone];
        int previous = milestone == 0 ? 0 : progress[milestone - 1];
        int fillTicks = milestone + 1 == ticks.length ? Math.min(12, Math.max(1, finalFillTicks))
            : Math.min(12, ticks[milestone + 1] - ticks[milestone] - 1);
        double filled = Math.min(1, (age + 1) / (double) fillTicks);
        filled = filled * (2 - filled);
        // Sweep forward at full opacity while the bright accent fades; never overshoot or retract.
        int pixels = (int) Math.round(1.8 * (previous + (retained - previous) * filled));
        return new Frame(retained, pixels,
            retained >= 69 ? 2 : retained >= 24 ? 1 : 0, age == 0, flash);
    }

    record Frame(int progress, int barFill, int runes, boolean step, double flash) {
    }
}
