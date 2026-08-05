package dev.vexsoft.core.paper.scheduler;

import dev.vexsoft.core.api.service.VexService;
import java.time.Duration;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * Schedules owner-bound tasks through Paper's global, region, entity, and async schedulers.
 *
 * <p>Callbacks run on the scheduler named by the method and may execute concurrently with work in
 * other regions. Tasks are cancelled when the owning service scope closes. An exception thrown by
 * a callback is logged and does not escape into the server scheduler.</p>
 */
public interface ScheduleService extends VexService {

  /** @return plugin that owns every task created by this service */
  Plugin getOwner();

  /**
   * Executes a task on the global region on the next tick.
   *
   * @param task operation executed by the global region scheduler
   * @return cancellable task handle
   */
  VexTask runGlobal(Runnable task);

  /**
   * Executes a delayed task on the global region.
   *
   * @param delayTicks positive delay in server ticks
   * @param task operation executed by the global region scheduler
   * @return cancellable task handle
   * @throws IllegalArgumentException if {@code delayTicks} is less than one
   */
  VexTask runGlobalLater(long delayTicks, Runnable task);

  /**
   * Executes a repeating task on the global region.
   *
   * @param initialDelayTicks positive delay before the first execution
   * @param periodTicks positive interval between executions
   * @param task operation executed by the global region scheduler
   * @return cancellable repeating-task handle
   */
  VexTask runGlobalTimer(long initialDelayTicks, long periodTicks, Runnable task);

  /**
   * Executes a task on the region that owns the location on the next tick.
   *
   * @param location location whose owning region should execute the task
   * @param task region-bound operation
   * @return cancellable task handle
   */
  VexTask runAt(Location location, Runnable task);

  /**
   * Executes a delayed task on the region that owns the location.
   *
   * @param location location whose owning region should execute the task
   * @param delayTicks positive delay in server ticks
   * @param task region-bound operation
   * @return cancellable task handle
   */
  VexTask runAtLater(Location location, long delayTicks, Runnable task);

  /**
   * Executes a repeating task on the region that owns the location.
   *
   * @param location location whose owning region should execute the task
   * @param initialDelayTicks positive delay before the first execution
   * @param periodTicks positive interval between executions
   * @param task region-bound operation
   * @return cancellable repeating-task handle
   */
  VexTask runAtTimer(
      Location location,
      long initialDelayTicks,
      long periodTicks,
      Runnable task
  );

  /**
   * Executes a task on the scheduler that follows an entity.
   *
   * @param entity entity that owns execution
   * @param task entity-bound operation
   * @return task handle, or empty if the entity has already retired
   */
  Optional<VexTask> runFor(Entity entity, Runnable task);

  /**
   * Executes a task on the scheduler that follows an entity and handles retirement.
   *
   * @param entity entity that owns execution
   * @param task entity-bound operation
   * @param retired optional callback invoked when the entity scheduler retires
   * @return task handle, or empty if the entity has already retired
   */
  Optional<VexTask> runFor(Entity entity, Runnable task, Runnable retired);

  /**
   * Executes a delayed task on the scheduler that follows an entity.
   *
   * @param entity entity that owns execution
   * @param delayTicks positive delay in server ticks
   * @param task entity-bound operation
   * @param retired optional callback invoked when the entity scheduler retires
   * @return task handle, or empty if the entity has already retired
   */
  Optional<VexTask> runForLater(
      Entity entity,
      long delayTicks,
      Runnable task,
      Runnable retired
  );

  /**
   * Executes a repeating task on the scheduler that follows an entity.
   *
   * @param entity entity that owns execution
   * @param initialDelayTicks positive delay before the first execution
   * @param periodTicks positive interval between executions
   * @param task entity-bound operation
   * @param retired optional callback invoked when the entity scheduler retires
   * @return task handle, or empty if the entity has already retired
   */
  Optional<VexTask> runForTimer(
      Entity entity,
      long initialDelayTicks,
      long periodTicks,
      Runnable task,
      Runnable retired
  );

  /**
   * Executes a task outside the server tick process.
   *
   * @param task asynchronous operation; Bukkit world access is not safe from this callback
   * @return cancellable task handle
   */
  VexTask runAsync(Runnable task);

  /**
   * Executes a delayed task outside the server tick process.
   *
   * @param delay non-negative wall-clock delay
   * @param task asynchronous operation
   * @return cancellable task handle
   */
  VexTask runAsyncLater(Duration delay, Runnable task);

  /**
   * Executes a repeating task outside the server tick process.
   *
   * @param initialDelay non-negative wall-clock delay before the first execution
   * @param interval positive wall-clock interval between executions
   * @param task asynchronous operation
   * @return cancellable repeating-task handle
   */
  VexTask runAsyncTimer(Duration initialDelay, Duration interval, Runnable task);

  /** Cancels every currently tracked task owned by this service. */
  void cancelAll();
}
