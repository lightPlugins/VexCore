package dev.vexsoft.core.paper.service.performance;

import dev.vexsoft.core.paper.performance.PerformanceState;
import dev.vexsoft.core.paper.performance.ServerPerformanceSnapshot;

import lombok.experimental.UtilityClass;

@UtilityClass
class PerformanceSnapshotCalculator {

  private static final int TICKS_PER_SAMPLE = 20;
  private static final double NANOS_PER_MILLISECOND = 1_000_000.0D;
  private static final double TARGET_TPS = 20.0D;
  private static final double TICK_BUDGET_MILLIS = 50.0D;
  private static final double MODERATE_MSPT = 25.0D;
  private static final double CRITICAL_MSPT = 40.0D;

  static ServerPerformanceSnapshot calculate(final long[] tickTimes, final long sampledAt) {
    int firstIndex = Math.max(0, tickTimes.length - TICKS_PER_SAMPLE);
    double total = 0.0D;
    double minimum = Double.POSITIVE_INFINITY;
    double maximum = 0.0D;
    int count = 0;
    for (int index = firstIndex; index < tickTimes.length; index++) {
      long tickTime = tickTimes[index];
      if (tickTime <= 0L) {
        continue;
      }
      double millis = tickTime / NANOS_PER_MILLISECOND;
      total += millis;
      minimum = Math.min(minimum, millis);
      maximum = Math.max(maximum, millis);
      count++;
    }
    if (count == 0) {
      return unavailable(sampledAt);
    }
    double average = total / count;
    double tps = average <= TICK_BUDGET_MILLIS
        ? TARGET_TPS
        : Math.min(TARGET_TPS, 1_000.0D / average);
    return new ServerPerformanceSnapshot(
        true,
        average,
        minimum,
        maximum,
        tps,
        sampledAt,
        state(average)
    );
  }

  static ServerPerformanceSnapshot unavailable(final long sampledAt) {
    return new ServerPerformanceSnapshot(
        false,
        0.0D,
        0.0D,
        0.0D,
        0.0D,
        sampledAt,
        PerformanceState.UNAVAILABLE
    );
  }

  private static PerformanceState state(final double averageMspt) {
    if (averageMspt >= CRITICAL_MSPT) {
      return PerformanceState.CRITICAL;
    }
    if (averageMspt >= MODERATE_MSPT) {
      return PerformanceState.MODERATE;
    }
    return PerformanceState.GOOD;
  }
}
