package dev.vexsoft.core.paper.service.performance;

import dev.vexsoft.core.paper.performance.ServerPerformanceSnapshot;

import dev.vexsoft.core.api.service.registry.VexService;

/** Samples server tick times and exposes the latest performance snapshot */
public interface ServerPerformanceService extends VexService {

  /** Starts the four-times-per-second rolling performance sampler. */
  void start();

  /** Returns the latest immutable server performance snapshot */
  ServerPerformanceSnapshot getSnapshot();
}
