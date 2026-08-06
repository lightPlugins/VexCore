package dev.vexsoft.core.paper.performance;

import lombok.Value;

/** Contains the most recent one-second server performance measurement */
@Value
public class ServerPerformanceSnapshot {
  boolean available;
  double averageMspt;
  double minimumMspt;
  double maximumMspt;
  double currentTps;
  long sampledAt;
  PerformanceState state;
}
