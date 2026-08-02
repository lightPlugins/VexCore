package dev.vexsoft.core.paper.scheduler;

import dev.vexsoft.core.api.service.VexService;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import java.time.Duration;
import java.util.Optional;

public interface ScheduleService extends VexService {

  /** Returns the plugin that owns every task created by this service */
  public Plugin owner();

  /** Executes a task on the global region on the next tick */
  public VexTask runGlobal(Runnable task);

  /** Executes a delayed task on the global region */
  public VexTask runGlobalLater(long delayTicks, Runnable task);

  /** Executes a repeating task on the global region */
  public VexTask runGlobalTimer(long initialDelayTicks, long periodTicks, Runnable task);

  /** Executes a task on the region that owns the location on the next tick */
  public VexTask runAt(Location location, Runnable task);

  /** Executes a delayed task on the region that owns the location */
  public VexTask runAtLater(Location location, long delayTicks, Runnable task);

  /** Executes a repeating task on the region that owns the location */
  public VexTask runAtTimer(
      Location location,
      long initialDelayTicks,
      long periodTicks,
      Runnable task
  );

  /** Executes a task on the scheduler that follows the entity */
  public Optional<VexTask> runFor(Entity entity, Runnable task);

  /** Executes a task on the scheduler that follows the entity and handles retirement */
  public Optional<VexTask> runFor(Entity entity, Runnable task, Runnable retired);

  /** Executes a delayed task on the scheduler that follows the entity */
  public Optional<VexTask> runForLater(
      Entity entity,
      long delayTicks,
      Runnable task,
      Runnable retired
  );

  /** Executes a repeating task on the scheduler that follows the entity */
  public Optional<VexTask> runForTimer(
      Entity entity,
      long initialDelayTicks,
      long periodTicks,
      Runnable task,
      Runnable retired
  );

  /** Executes a task outside the server tick process */
  public VexTask runAsync(Runnable task);

  /** Executes a delayed task outside the server tick process */
  public VexTask runAsyncLater(Duration delay, Runnable task);

  /** Executes a repeating task outside the server tick process */
  public VexTask runAsyncTimer(Duration initialDelay, Duration interval, Runnable task);

  /** Cancels every task owned by this service */
  public void cancelAll();
}
