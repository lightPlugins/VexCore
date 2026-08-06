package dev.vexsoft.core.paper.performance;

import dev.vexsoft.core.api.service.Dependencies;
import dev.vexsoft.core.api.service.VexServiceRegistry;
import dev.vexsoft.core.paper.platform.PlatformService;
import dev.vexsoft.core.paper.scheduler.ScheduleService;
import dev.vexsoft.core.paper.scheduler.VexTask;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.Server;

@Dependencies({PlatformService.class, ScheduleService.class})
public final class VexServerPerformanceService implements
    ServerPerformanceService,
    AutoCloseable {

  private static final int TICKS_PER_SAMPLE = 20;
  private final Server server;
  private final PlatformService platform;
  private final ScheduleService schedules;
  private final AtomicBoolean started = new AtomicBoolean();
  private volatile ServerPerformanceSnapshot snapshot =
      PerformanceSnapshotCalculator.unavailable(System.currentTimeMillis());
  private VexTask samplingTask;

  public VexServerPerformanceService(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    this.server = Bukkit.getServer();
    this.platform = checkedServices.require(PlatformService.class);
    this.schedules = checkedServices.require(ScheduleService.class);
  }

  @Override
  public void start() {
    if (!started.compareAndSet(false, true)) {
      return;
    }
    if (platform.isFolia()) {
      snapshot = PerformanceSnapshotCalculator.unavailable(System.currentTimeMillis());
      return;
    }
    sample();
    samplingTask = schedules.runGlobalTimer(
        TICKS_PER_SAMPLE,
        TICKS_PER_SAMPLE,
        this::sample
    );
  }

  @Override
  public ServerPerformanceSnapshot getSnapshot() {
    return snapshot;
  }

  @Override
  public void close() {
    if (samplingTask != null) {
      samplingTask.cancel();
      samplingTask = null;
    }
    started.set(false);
  }

  private void sample() {
    snapshot = PerformanceSnapshotCalculator.calculate(
        server.getTickTimes(),
        System.currentTimeMillis()
    );
  }
}
