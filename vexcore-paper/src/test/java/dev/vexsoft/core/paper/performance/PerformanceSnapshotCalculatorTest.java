package dev.vexsoft.core.paper.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PerformanceSnapshotCalculatorTest {

  @Test
  void calculatesGoodOneSecondWindow() {
    long[] tickTimes = new long[21];
    tickTimes[0] = millis(100.0D);
    Arrays.fill(tickTimes, 1, tickTimes.length, millis(10.0D));

    ServerPerformanceSnapshot snapshot = PerformanceSnapshotCalculator.calculate(tickTimes, 25L);

    assertTrue(snapshot.isAvailable());
    assertEquals(10.0D, snapshot.getAverageMspt(), 0.001D);
    assertEquals(10.0D, snapshot.getMinimumMspt(), 0.001D);
    assertEquals(10.0D, snapshot.getMaximumMspt(), 0.001D);
    assertEquals(20.0D, snapshot.getCurrentTps(), 0.001D);
    assertEquals(PerformanceState.GOOD, snapshot.getState());
  }

  @Test
  void marksModerateAndCriticalLoads() {
    ServerPerformanceSnapshot moderate = PerformanceSnapshotCalculator.calculate(
        repeated(30.0D),
        1L
    );
    ServerPerformanceSnapshot critical = PerformanceSnapshotCalculator.calculate(
        repeated(45.0D),
        2L
    );

    assertEquals(PerformanceState.MODERATE, moderate.getState());
    assertEquals(PerformanceState.CRITICAL, critical.getState());
  }

  @Test
  void derivesTpsWhenTickBudgetIsExceeded() {
    ServerPerformanceSnapshot snapshot = PerformanceSnapshotCalculator.calculate(
        repeated(100.0D),
        1L
    );

    assertEquals(10.0D, snapshot.getCurrentTps(), 0.001D);
    assertEquals(PerformanceState.CRITICAL, snapshot.getState());
  }

  @Test
  void reportsUnavailableWithoutCompletedTicks() {
    ServerPerformanceSnapshot snapshot = PerformanceSnapshotCalculator.calculate(
        new long[20],
        50L
    );

    assertEquals(false, snapshot.isAvailable());
    assertEquals(PerformanceState.UNAVAILABLE, snapshot.getState());
  }

  private long[] repeated(final double millis) {
    long[] values = new long[20];
    Arrays.fill(values, millis(millis));
    return values;
  }

  private long millis(final double millis) {
    return Math.round(millis * 1_000_000.0D);
  }
}
