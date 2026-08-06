package dev.vexsoft.core.paper.performance;

import dev.vexsoft.core.api.service.VexService;

/** Samples server tick times and exposes the latest performance snapshot */
public interface ServerPerformanceService extends VexService {

  /** Starts the one-second performance sampler */
  void start();

  /** Returns the latest immutable server performance snapshot */
  ServerPerformanceSnapshot getSnapshot();
}
