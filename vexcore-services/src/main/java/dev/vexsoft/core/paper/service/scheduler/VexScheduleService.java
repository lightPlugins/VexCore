package dev.vexsoft.core.paper.service.scheduler;

import dev.vexsoft.core.paper.scheduler.VexTask;

import java.util.logging.Level;
import dev.vexsoft.core.api.service.registry.Dependencies;
import dev.vexsoft.core.api.service.registry.VexServiceRegistry;
import dev.vexsoft.core.paper.service.platform.PlatformService;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import lombok.Getter;

@Dependencies({PlatformService.class})
public final class VexScheduleService implements ScheduleService, AutoCloseable {

  private final Server server;
  @Getter
  private final Plugin owner;
  private final ConcurrentHashMap<ScheduledTask, ScheduledVexTask> tasks = new ConcurrentHashMap<>();
  private final AtomicBoolean closed = new AtomicBoolean();

  public VexScheduleService(final VexServiceRegistry services) {
    VexServiceRegistry checkedServices = Objects.requireNonNull(services, "services");
    checkedServices.require(PlatformService.class);
    if (!(checkedServices.getOwner() instanceof Plugin plugin)) {
      throw new IllegalArgumentException("ScheduleService owner must be a Bukkit plugin");
    }
    this.server = Bukkit.getServer();
    this.owner = plugin;
  }

  @Override
  public VexTask runGlobal(final Runnable task) {
    ensureOpen();
    return track(server.getGlobalRegionScheduler().run(owner, callback(task)));
  }

  @Override
  public VexTask runGlobalLater(final long delayTicks, final Runnable task) {
    ensureOpen();
    requirePositive(delayTicks, "delayTicks");
    return track(server.getGlobalRegionScheduler().runDelayed(owner, callback(task), delayTicks));
  }

  @Override
  public VexTask runGlobalTimer(
      final long initialDelayTicks,
      final long periodTicks,
      final Runnable task
  ) {
    ensureOpen();
    requirePositive(initialDelayTicks, "initialDelayTicks");
    requirePositive(periodTicks, "periodTicks");
    return track(server.getGlobalRegionScheduler().runAtFixedRate(
        owner,
        callback(task),
        initialDelayTicks,
        periodTicks
    ));
  }

  @Override
  public VexTask runAt(final Location location, final Runnable task) {
    ensureOpen();
    return track(server.getRegionScheduler().run(owner, requireLocation(location), callback(task)));
  }

  @Override
  public VexTask runAtLater(
      final Location location,
      final long delayTicks,
      final Runnable task
  ) {
    ensureOpen();
    requirePositive(delayTicks, "delayTicks");
    return track(server.getRegionScheduler().runDelayed(
        owner,
        requireLocation(location),
        callback(task),
        delayTicks
    ));
  }

  @Override
  public VexTask runAtTimer(
      final Location location,
      final long initialDelayTicks,
      final long periodTicks,
      final Runnable task
  ) {
    ensureOpen();
    requirePositive(initialDelayTicks, "initialDelayTicks");
    requirePositive(periodTicks, "periodTicks");
    return track(server.getRegionScheduler().runAtFixedRate(
        owner,
        requireLocation(location),
        callback(task),
        initialDelayTicks,
        periodTicks
    ));
  }

  @Override
  public Optional<VexTask> runFor(final Entity entity, final Runnable task) {
    return runFor(entity, task, null);
  }

  @Override
  public Optional<VexTask> runFor(
      final Entity entity,
      final Runnable task,
      final Runnable retired
  ) {
    ensureOpen();
    AtomicReference<ScheduledTask> reference = new AtomicReference<>();
    ScheduledTask scheduled = Objects.requireNonNull(entity, "entity").getScheduler().run(
        owner,
        callback(task),
        retiredCallback(retired, reference)
    );
    return trackEntity(scheduled, reference);
  }

  @Override
  public Optional<VexTask> runForLater(
      final Entity entity,
      final long delayTicks,
      final Runnable task,
      final Runnable retired
  ) {
    ensureOpen();
    requirePositive(delayTicks, "delayTicks");
    AtomicReference<ScheduledTask> reference = new AtomicReference<>();
    ScheduledTask scheduled = Objects.requireNonNull(entity, "entity").getScheduler().runDelayed(
        owner,
        callback(task),
        retiredCallback(retired, reference),
        delayTicks
    );
    return trackEntity(scheduled, reference);
  }

  @Override
  public Optional<VexTask> runForTimer(
      final Entity entity,
      final long initialDelayTicks,
      final long periodTicks,
      final Runnable task,
      final Runnable retired
  ) {
    ensureOpen();
    requirePositive(initialDelayTicks, "initialDelayTicks");
    requirePositive(periodTicks, "periodTicks");
    AtomicReference<ScheduledTask> reference = new AtomicReference<>();
    ScheduledTask scheduled = Objects.requireNonNull(entity, "entity").getScheduler().runAtFixedRate(
        owner,
        callback(task),
        retiredCallback(retired, reference),
        initialDelayTicks,
        periodTicks
    );
    return trackEntity(scheduled, reference);
  }

  @Override
  public VexTask runAsync(final Runnable task) {
    ensureOpen();
    return track(server.getAsyncScheduler().runNow(owner, callback(task)));
  }

  @Override
  public VexTask runAsyncLater(final Duration delay, final Runnable task) {
    ensureOpen();
    long delayNanos = requireNonNegative(delay, "delay").toNanos();
    if (delayNanos == 0L) {
      return runAsync(task);
    }
    return track(server.getAsyncScheduler().runDelayed(
        owner,
        callback(task),
        delayNanos,
        TimeUnit.NANOSECONDS
    ));
  }

  @Override
  public VexTask runAsyncTimer(
      final Duration initialDelay,
      final Duration interval,
      final Runnable task
  ) {
    ensureOpen();
    long initialDelayNanos = requireNonNegative(initialDelay, "initialDelay").toNanos();
    long intervalNanos = requirePositive(interval, "interval").toNanos();
    return track(server.getAsyncScheduler().runAtFixedRate(
        owner,
        callback(task),
        initialDelayNanos,
        intervalNanos,
        TimeUnit.NANOSECONDS
    ));
  }

  @Override
  public void cancelAll() {
    for (ScheduledVexTask task : new ArrayList<>(tasks.values())) {
      task.cancel();
    }
    tasks.clear();
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      cancelAll();
    }
  }

  private Consumer<ScheduledTask> callback(final Runnable task) {
    Runnable checkedTask = Objects.requireNonNull(task, "task");
    return scheduled -> {
      try {
        checkedTask.run();
      } catch (Throwable throwable) {
        owner.getLogger().log(Level.SEVERE, "Scheduled task failed", throwable);
      } finally {
        if (!scheduled.isRepeatingTask()) {
          tasks.remove(scheduled);
        }
      }
    };
  }

  private Runnable retiredCallback(
      final Runnable retired,
      final AtomicReference<ScheduledTask> reference
  ) {
    return () -> {
      try {
        if (retired != null) {
          retired.run();
        }
      } catch (Throwable throwable) {
        owner.getLogger().log(
            Level.SEVERE,
            "Entity retired callback failed",
            throwable
        );
      } finally {
        ScheduledTask scheduled = reference.get();
        if (scheduled != null) {
          tasks.remove(scheduled);
        }
      }
    };
  }

  private Optional<VexTask> trackEntity(
      final ScheduledTask scheduled,
      final AtomicReference<ScheduledTask> reference
  ) {
    if (scheduled == null) {
      return Optional.empty();
    }
    reference.set(scheduled);
    return Optional.of(track(scheduled));
  }

  private VexTask track(final ScheduledTask scheduled) {
    ScheduledVexTask task = new ScheduledVexTask(scheduled);
    tasks.put(scheduled, task);

    // Async tasks may finish before the scheduler returns their handle
    if (task.isFinished() || task.isCancelled()) {
      tasks.remove(scheduled);
    }
    return task;
  }

  private Location requireLocation(final Location location) {
    Location checked = Objects.requireNonNull(location, "location");
    if (checked.getWorld() == null) {
      throw new IllegalArgumentException("Location must have a world");
    }
    return checked;
  }

  private void ensureOpen() {
    if (closed.get()) {
      throw new IllegalStateException("ScheduleService is already closed");
    }
  }

  private void requirePositive(final long value, final String name) {
    if (value < 1L) {
      throw new IllegalArgumentException(name + " must be at least one tick");
    }
  }

  private Duration requireNonNegative(final Duration value, final String name) {
    Duration checked = Objects.requireNonNull(value, name);
    if (checked.isNegative()) {
      throw new IllegalArgumentException(name + " must not be negative");
    }
    return checked;
  }

  private Duration requirePositive(final Duration value, final String name) {
    Duration checked = requireNonNegative(value, name);
    if (checked.isZero()) {
      throw new IllegalArgumentException(name + " must be greater than zero");
    }
    return checked;
  }
}
